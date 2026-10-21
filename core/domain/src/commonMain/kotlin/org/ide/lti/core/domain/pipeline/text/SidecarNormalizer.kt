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

    public data class NormalizedSidecars(
        val fsConfigContent: String,
        val fileContextContent: String,
    )

    private val REGEX_METACHARS = Regex("""([.+\[\]*])""")

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
            if (targetFsPath.endsWith("/bin/run-as") || targetFsPath.endsWith("/bin/simpleperf_app_runner")) {
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
        val statLines = mutableListOf<String>()
        val selinuxLines = mutableListOf<String>()
        statDump.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.trim().split(" ", limit = 4)
            require(parts.size == 4) { "Malformed stat line for $partition: $line" }
            val path = parts[3]
            val label = requireNotNull(labels[path]) { "No security.selinux label for $path ($partition)" }
            statLines += "$path ${parts[0]} ${parts[1]} ${parts[2]} capabilities=0x0"
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
                line.startsWith("# file: ") -> currentPath = line.removePrefix("# file: ")
                line.startsWith("security.selinux=") -> currentPath?.let { path ->
                    result[path] = line.substringAfter('=').trim().trim('"')
                }
            }
        }
        return result
    }

    public fun escapeRegex(path: String): String {
        return path.replace(REGEX_METACHARS, """\\$1""")
    }
}
