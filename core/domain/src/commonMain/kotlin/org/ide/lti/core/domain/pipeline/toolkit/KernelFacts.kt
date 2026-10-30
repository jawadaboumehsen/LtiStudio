/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

/**
 * What the boot kernel is and what it can read, from its version string and its embedded config (`IKCONFIG`).
 * [config] is null when the kernel carries no config: then nothing can be said about what it reads.
 */
public data class KernelFacts(val version: String?, val config: Map<String, String>?) {
    /** Compressions an EROFS image may use and still mount (lz4 covers lz4hc: it is the same decoder). */
    public val erofsCompressions: List<String>?
        get() = config?.let { c ->
            if (!c.on("CONFIG_EROFS_FS")) {
                emptyList()
            } else {
                listOfNotNull(
                    "lz4".takeIf { c.on("CONFIG_EROFS_FS_ZIP") },
                    "lzma".takeIf { c.on("CONFIG_EROFS_FS_ZIP_LZMA") },
                    "deflate".takeIf { c.on("CONFIG_EROFS_FS_ZIP_DEFLATE") },
                    "zstd".takeIf { c.on("CONFIG_EROFS_FS_ZIP_ZSTD") },
                )
            }
        }

    /** Ramdisk compressions the kernel unpacks (`CONFIG_RD_*`). */
    public val ramdiskFormats: List<String>?
        get() = config?.filterKeys { it.startsWith(RD) }?.filterValues { it == "y" }?.keys
            ?.map { it.removePrefix(RD).lowercase() }?.sorted()

    /** fs-verity (per-file hashes, used by APK verity and updatable apexes); null without a config. */
    public val fsVerity: Boolean? get() = config?.on("CONFIG_FS_VERITY")

    /** dm-verity (whole-partition hashtrees, what AVB's hashtree descriptors need); null without a config. */
    public val dmVerity: Boolean? get() = config?.on("CONFIG_DM_VERITY")

    /** dm-verity's error correction (`fec` data in AVB hashtrees). */
    public val dmVerityFec: Boolean? get() = config?.on("CONFIG_DM_VERITY_FEC")

    /** Whether the kernel mounts [filesystem] (`erofs`, `ext4`, `f2fs`), built in or a module; null if unknown. */
    public fun mounts(filesystem: String): Boolean? = config?.on("CONFIG_${filesystem.uppercase()}_FS")

    public companion object {
        private const val RD = "CONFIG_RD_"

        /** `sh -c SCRIPT sh <boot image> <unpack_bootimg>`: the lines [parse] reads. */
        public const val SCRIPT: String = """
            set -e
            img="$1"; u="$2"
            [ -f "${'$'}img" ] || { echo "nokernel"; exit 0; }
            t=$(mktemp -d)
            trap 'rm -rf "${'$'}t"' EXIT
            "${'$'}u" --boot_img "${'$'}img" --out "${'$'}t" >/dev/null
            k="${'$'}t/kernel"
            [ -s "${'$'}k" ] || { echo "nokernel"; exit 0; }
            case $(xxd -p -l 4 "${'$'}k") in
              1f8b*) gzip -dc "${'$'}k" > "${'$'}t/k" 2>/dev/null || true; k="${'$'}t/k" ;;
              02214c18|04224d18) lz4 -dc "${'$'}k" > "${'$'}t/k"; k="${'$'}t/k" ;;
            esac
            echo "version $(grep -aoE 'Linux version [^ ]+' "${'$'}k" | head -n1 | cut -d' ' -f3)"
            o=$(grep -abo IKCFG_ST "${'$'}k" | head -n1 | cut -d: -f1)
            if [ -n "${'$'}o" ]; then
              tail -c +$((o + 9)) "${'$'}k" | gzip -dc 2>/dev/null |
                grep -E '^CONFIG_(EROFS_FS|EXT4_FS|RD_|F2FS_FS|FS_VERITY|DM_VERITY)[A-Z0-9_]*=' || true
            else
              echo "noconfig"
            fi
        """

        private fun Map<String, String>.on(key: String) = this[key] == "y" || this[key] == "m"

        /** `version <v>`, then `CONFIG_X=y` lines or `noconfig`; `nokernel` when the image has none. */
        public fun parse(output: String): KernelFacts? {
            val lines = output.lines().map(String::trim).filter(String::isNotEmpty)
            if ("nokernel" in lines) return null
            val version = lines.firstOrNull { it.startsWith("version ") }?.substringAfter(' ')?.ifBlank { null }
            val config = lines.filter { it.startsWith("CONFIG_") && '=' in it }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
            return KernelFacts(version, config.takeUnless { "noconfig" in lines })
        }
    }
}
