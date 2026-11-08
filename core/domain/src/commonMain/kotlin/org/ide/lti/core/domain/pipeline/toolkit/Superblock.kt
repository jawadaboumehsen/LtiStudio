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

/** A partition's stock image and its last built one, side by side; either is null when it does not exist. */
public data class ImageInfo(val stock: Superblock?, val built: Superblock?)

/**
 * What an image's superblock says: its filesystem, file size, and the fields its own tool
 * prints (`dump.erofs`, `dumpe2fs -h`, `dump.f2fs`), the ones worth comparing, in that tool's words.
 */
public data class Superblock(val fs: FsType, val sizeBytes: Long, val fields: List<Pair<String, String>>) {
    public companion object {
        private val EROFS = listOf(
            "Filesystem blocksize",
            "Filesystem blocks",
            "Filesystem inode count",
            "Filesystem features",
            "Filesystem created",
            "Filesystem UUID",
            "Required upstream Linux kernel version",
        )
        private val EXT4 = listOf(
            "Filesystem volume name", "Filesystem UUID", "Filesystem features", "Block size", "Block count",
            "Free blocks", "Inode count", "Free inodes", "Filesystem created", "Filesystem state",
        )
        private val F2FS = listOf(
            "superblock features",
            "total FS sectors",
            "Segments per section",
            "Sections per zone",
            "checkpoint state",
        )

        /** The fields of [dump] named for [fs], in that order; a field the tool did not print is left out. */
        public fun fields(fs: FsType, dump: String): List<Pair<String, String>> {
            val all = dump.lines().mapNotNull { raw ->
                val line = raw.trim()
                when {
                    // dump.f2fs: `Info: total FS sectors = 65536 (32 MB)`, `Info: superblock features = 4000 :  ro`
                    line.startsWith("Info: ") && " = " in line -> line.removePrefix("Info: ").split(" = ", limit = 2)

                    ':' in line -> line.split(':', limit = 2)

                    else -> null
                }?.let { (k, v) -> k.trim() to v.trim() }
            }.toMap()
            val wanted = when (fs) {
                FsType.Erofs -> EROFS
                FsType.Ext4 -> EXT4
                FsType.F2fs -> F2FS
                else -> emptyList()
            }
            return wanted.mapNotNull { key -> all[key]?.takeIf { it.isNotEmpty() }?.let { key to it } }
        }

        /** Field names that mean the same on every filesystem, so two images of different kinds line up. */
        private val SHARED = mapOf(
            "Filesystem blocksize" to "Block size",
            "Filesystem blocks" to "Block count",
            "Filesystem inode count" to "Inode count",
            "Filesystem features" to "Features",
            "superblock features" to "Features",
            "Filesystem created" to "Created",
            "Filesystem UUID" to "UUID",
        )

        /**
         * [a] and [b] side by side: filesystem, image size, then every field either prints (names shared across
         * filesystems where they mean the same), in [a]'s order then [b]'s; a side without the field shows null.
         */
        public fun compare(a: Superblock?, b: Superblock?): List<SuperblockRow> {
            fun fields(s: Superblock?) = s?.let {
                listOf("Filesystem" to it.fs.name.lowercase(), "Image size" to "${it.sizeBytes} bytes") +
                    it.fields.map { (k, v) -> (SHARED[k] ?: k) to v }
            }.orEmpty()
            val left = fields(a).toMap()
            val right = fields(b).toMap()
            return (left.keys + right.keys).map { SuperblockRow(it, left[it], right[it]) }
        }
    }
}

/** One superblock field of two images. */
public data class SuperblockRow(val field: String, val a: String?, val b: String?) {
    public val differs: Boolean get() = a != b
}
