/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.text

public object SidecarNormalizer {

    public data class NormalizedSidecars(val fsConfigContent: String, val fileContextContent: String)

    /** Every character a file_contexts (PCRE) path regex gives a meaning to. */
    private val REGEX_METACHARS = Regex("""([.+*?^$()\[\]{}|\\])""")

    /**
     * `sh -c LABEL_DUMP sh <dir>`: the SELinux labels of everything under `<dir>` (every entry must have one), then
     * the file capabilities of the few files that carry them (`security.capability` in hex; files without one print
     * nothing). `getfattr -d -m` is not used: erofsfuse lists the attributes of only some inodes.
     */
    public const val LABEL_DUMP: String =
        "getfattr -R -h -n security.selinux --absolute-names \"\$1\" || exit; " +
            "getfattr -R -h -n security.capability -e hex --absolute-names \"\$1\" 2>/dev/null; exit 0"

    /**
     * Normalizes raw find/stat and getfattr output into canonical fs_config and file_context.
     */
    public fun normalize(
        partition: String,
        mountPrefix: String,
        rawStatLines: List<String>,
        rawSelinuxLines: List<String>,
    ): NormalizedSidecars {
        val cleanStats = rawStatLines.filter { it.isNotBlank() }
        val cleanSelinux = rawSelinuxLines.filter { it.isNotBlank() }

        require(cleanStats.size == cleanSelinux.size) {
            "Stat count (${cleanStats.size}) != SELinux count (${cleanSelinux.size}) for $partition"
        }

        val prefixNormalized = mountPrefix.removeSuffix("/")
        val fsConfigLines = mutableListOf<String>()
        val fileContextLines = mutableListOf<String>()

        for (i in cleanStats.indices) {
            val statLine = cleanStats[i]
            val selinuxLabel = cleanSelinux[i].trim()

            // Trailing fields are fixed; the path may itself contain spaces.
            val parts = statLine.trim().split(" ")
            require(parts.size >= 5) { "Malformed stat line: $statLine" }

            val rawPath = parts.dropLast(4).joinToString(" ")
            val uid = parts[parts.size - 4]
            val gid = parts[parts.size - 3]
            val mode = parts[parts.size - 2]
            var caps = parts.last()

            val relPath = when {
                rawPath == prefixNormalized -> ""
                rawPath.startsWith("$prefixNormalized/") -> rawPath.removePrefix("$prefixNormalized/")
                else -> rawPath.removePrefix(prefixNormalized).removePrefix("/")
            }

            val targetFsPath = fsConfigPath(partition, relPath)
            // A readback that cannot show capabilities still keeps the two AOSP ones every system has.
            val runner = targetFsPath.endsWith("/bin/run-as") || targetFsPath.endsWith("/bin/simpleperf_app_runner")
            if (runner && caps == "capabilities=0x0") {
                caps = "capabilities=0xc0"
            }
            fsConfigLines.add("$targetFsPath $uid $gid $mode $caps")
            val fileContextPath = fileContextPath(partition, relPath)
            val escapedPath = escapeRegex(fileContextPath)
            fileContextLines.add("$escapedPath $selinuxLabel")
        }

        return NormalizedSidecars(
            fsConfigContent = fsConfigLines.sorted().joinToString("\n") + "\n",
            fileContextContent = fileContextLines.sorted().joinToString("\n") + "\n",
        )
    }

    /**
     * The fs_config path of [path] (relative to the partition's work tree, `work/<partition>/`). system is
     * built with `mkfs.erofs --mount-point /`, so its paths are relative to the image root (system-as-root:
     * `system/app/Foo`, the root itself ""), exactly as LtiRom's extract_fw.sh writes them; every other
     * partition is mounted at its name (`product/app/Foo`), and its root is looked up as `/` (mkfs.erofs
     * fails with "failed to find  in canned fs_config" when the root line is the partition name).
     */
    public fun fsConfigPath(partition: String, path: String): String {
        val rel = path.trim('/')
        return when {
            partition == "system" -> rel
            rel.isEmpty() -> "/"
            else -> "$partition/$rel"
        }
    }

    /** The (unescaped) file_contexts path of [path]: its device path, `/system/app/Foo`, `/product/app/Foo`. */
    public fun fileContextPath(partition: String, path: String): String {
        val rel = path.trim('/')
        return when {
            partition == "system" -> "/$rel"
            rel.isEmpty() -> "/$partition"
            else -> "/$partition/$rel"
        }
    }

    /**
     * Builds the sidecars from the two dumps the extraction stage captures:
     * `find <mnt> -exec stat -c "%u %g %a %n" {} +` and
     * `getfattr -R -h -n security.selinux --absolute-names <mnt>` (blocks of `# file: <path>` /
     * `security.selinux="<label>"`). Entries are joined by path, so traversal order is irrelevant;
     * every file must carry a label (equal-count rule).
     */
    public fun fromDumps(
        partition: String,
        mountPrefix: String,
        statDump: String,
        getfattrDump: String,
    ): NormalizedSidecars {
        val labels = parseGetfattr(getfattrDump)
        val capabilities = parseCapabilities(getfattrDump)
        val statLines = mutableListOf<String>()
        val selinuxLines = mutableListOf<String>()
        statDump.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.trim().split(" ", limit = 4)
            require(parts.size == 4) { "Malformed stat line for $partition: $line" }
            val path = parts[3]
            val label = requireNotNull(labels[path]) { "No security.selinux label for $path ($partition)" }
            val caps = capabilities[path] ?: "0x0"
            statLines += "$path ${parts[0]} ${parts[1]} ${parts[2]} capabilities=$caps"
            selinuxLines += label
        }
        require(statLines.isNotEmpty()) { "Empty stat dump for $partition" }
        return normalize(partition, mountPrefix, statLines, selinuxLines)
    }

    public fun parseGetfattr(dump: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var currentPath: String? = null
        dump.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("# file: ") -> currentPath = decodePath(line.removePrefix("# file: "))

                line.startsWith("security.selinux=") -> currentPath?.let { path ->
                    result[path] = line.substringAfter('=').trim().trim('"')
                }
            }
        }
        return result
    }

    /**
     * The `security.capability` values of a [LABEL_DUMP] (`vfs_cap_data` in hex: a little-endian magic word, then
     * permitted / inheritable words for capabilities 0-31 and, from revision 2, 32-63) as fs_config writes them:
     * the permitted mask, `0x1000001400` for NET_BIND_SERVICE, NET_ADMIN and BLOCK_SUSPEND.
     */
    public fun parseCapabilities(dump: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var currentPath: String? = null
        dump.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("# file: ") -> currentPath = decodePath(line.removePrefix("# file: "))

                line.startsWith("security.capability=0x") -> currentPath?.let { path ->
                    capabilityMask(line.substringAfter("=0x"))?.let { result[path] = "0x" + it.toString(16) }
                }
            }
        }
        return result
    }

    private fun capabilityMask(hex: String): Long? {
        val bytes = hex.chunked(2).mapNotNull { it.toIntOrNull(16) }
        if (bytes.size < CAP_V1_BYTES) return null
        fun word(at: Int): Long = (0 until 4).fold(0L) { acc, i -> acc or (bytes[at + i].toLong() shl (8 * i)) }
        val high = if (bytes.size >= CAP_V2_BYTES) word(CAP_HIGH_PERMITTED) else 0L
        return (high shl 32) or word(CAP_LOW_PERMITTED)
    }

    /** getfattr writes a backslash, newline and other special bytes of a name as `\ooo` (octal). */
    private fun decodePath(printed: String): String {
        if ('\\' !in printed) return printed
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < printed.length) {
            val octal = printed.substring(i + 1, minOf(i + 4, printed.length))
            if (printed[i] == '\\' && octal.length == 3 && octal.all { it in '0'..'7' }) {
                bytes.write(octal.toInt(8))
                i += 4
            } else {
                bytes.write(printed.substring(i, i + 1).toByteArray())
                i++
            }
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private const val CAP_V1_BYTES = 12
    private const val CAP_V2_BYTES = 20
    private const val CAP_LOW_PERMITTED = 4
    private const val CAP_HIGH_PERMITTED = 12

    public fun escapeRegex(path: String): String = path.replace(REGEX_METACHARS, """\\$1""")
}
