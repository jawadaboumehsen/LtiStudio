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

/** An fs_config line's owner, group, permission bits and capabilities. */
public data class Owner(val uid: Int, val gid: Int, val mode: Int, val capabilities: Long = 0L) {
    /** `<uid> <gid> <mode> capabilities=0x<caps>`, as fs_config lines say it (mode in octal, at least 3 digits). */
    public val fsConfigFields: String
        get() = "$uid $gid ${mode.toString(
            OCTAL,
        ).padStart(MODE_DIGITS, '0')} capabilities=0x${capabilities.toString(HEX)}"

    private companion object {
        const val OCTAL = 8
        const val HEX = 16
        const val MODE_DIGITS = 3
    }
}

/**
 * Who owns a path and with what mode, as Android's build decides it: the device's own tables first
 * (`<partition>/etc/fs_config_dirs` and `fs_config_files`, built from the device's `config.fs`, with capabilities),
 * then Android's built-in table (`bin/` folders are `root:shell 0751`, their files `0755`), then root `0755` for
 * folders and `0644` for files. Paths are as fs_config lines write them (`system/bin/sh`, `vendor/bin/cnd`).
 */
public class OwnerRules(private val dirs: List<Entry>, private val files: List<Entry>) {
    public data class Entry(val pattern: String, val owner: Owner)

    /** Where an answer came from. */
    public enum class Source { Device, Android, Default }

    public fun owner(path: String, isDirectory: Boolean): Pair<Owner, Source> {
        val clean = path.trim('/')
        fun find(table: List<Entry>) = table.firstOrNull { matches(it.pattern, clean, isDirectory) }?.owner
        val device = find(if (isDirectory) dirs else files)
        val android = find(if (isDirectory) ANDROID_DIRS else ANDROID_FILES)
        return when {
            device != null -> device to Source.Device
            android != null -> android to Source.Android
            else -> Owner(0, 0, if (isDirectory) DIR_MODE else FILE_MODE) to Source.Default
        }
    }

    public companion object {
        public val ANDROID_ONLY: OwnerRules = OwnerRules(emptyList(), emptyList())
        private const val HEADER = 16
        private const val DIR_MODE = 0x1ED // 0755
        private const val FILE_MODE = 0x1A4 // 0644
        private const val BIN_DIR_MODE = 0x1E9 // 0751
        private const val XBIN_DIR_MODE = 0x1E8 // 0750
        private const val READ_ONLY = 0x124 // 0444
        private const val SHELL = 2000

        /**
         * One binary table: entries of `u16 length, u16 mode, u16 uid, u16 gid, u64 capabilities, NUL-ended path`
         * (little-endian, [length] covering it all). A damaged tail ends the list, never a wrong entry.
         */
        public fun decodeTable(bytes: ByteArray): List<Entry> {
            val out = mutableListOf<Entry>()
            var at = 0
            fun u16(i: Int) = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
            while (at + HEADER <= bytes.size) {
                val length = u16(at)
                if (length <= HEADER || at + length > bytes.size) break
                val caps = (0 until 8).fold(0L) { acc, i -> acc or ((bytes[at + 8 + i].toLong() and 0xFF) shl (8 * i)) }
                val name = bytes.decodeToString(at + HEADER, at + length).substringBefore('\u0000')
                if (name.isNotEmpty()) out += Entry(name, Owner(u16(at + 4), u16(at + 6), u16(at + 2), caps))
                at += length
            }
            return out
        }

        /** Android's fs_config match: `*` matches anything (also `/`); a folder rule covers what is below it. */
        internal fun matches(pattern: String, path: String, isDirectory: Boolean): Boolean {
            if (!isDirectory) return glob(pattern, path)
            val base = pattern.trimEnd('/').removeSuffix("/*")
            return glob("$base/*", "$path/")
        }

        private fun glob(pattern: String, text: String): Boolean {
            val regex = pattern.split('*').joinToString(".*") { Regex.escape(it) }
            return Regex(regex).matches(text)
        }

        private fun dir(pattern: String, mode: Int, gid: Int = 0) = Entry(pattern, Owner(0, gid, mode))
        private fun file(pattern: String, mode: Int, gid: Int = 0) = Entry(pattern, Owner(0, gid, mode))

        /** The part of Android's `fs_config.cpp` that covers the partitions of an image (not ramdisk or data). */
        private val ANDROID_DIRS = listOf(
            dir("product/bin", BIN_DIR_MODE, SHELL),
            dir("product/apex/*/bin", BIN_DIR_MODE, SHELL),
            dir("system/bin", BIN_DIR_MODE, SHELL),
            dir("system/etc/ppp", DIR_MODE),
            dir("system/vendor", DIR_MODE, SHELL),
            dir("system/xbin", XBIN_DIR_MODE, SHELL),
            dir("system/apex/*/bin", BIN_DIR_MODE, SHELL),
            dir("system_ext/bin", BIN_DIR_MODE, SHELL),
            dir("system_ext/apex/*/bin", BIN_DIR_MODE, SHELL),
            dir("vendor/bin", BIN_DIR_MODE, SHELL),
            dir("vendor/apex/*/bin", BIN_DIR_MODE, SHELL),
            dir("vendor", DIR_MODE, SHELL),
        )

        private val ANDROID_FILES = listOf("system", "vendor", "odm", "product", "system_ext", "oem").flatMap {
            listOf(file("$it/etc/fs_config_dirs", READ_ONLY), file("$it/etc/fs_config_files", READ_ONLY))
        } + listOf(
            "system/bin/*", "system/xbin/*", "system/apex/*/bin/*", "system_ext/bin/*", "system_ext/apex/*/bin/*",
            "vendor/bin/*", "vendor/apex/*/bin/*", "vendor/xbin/*", "odm/bin/*", "product/bin/*",
            "product/apex/*/bin/*",
        ).map { file(it, DIR_MODE, SHELL) }
    }
}
