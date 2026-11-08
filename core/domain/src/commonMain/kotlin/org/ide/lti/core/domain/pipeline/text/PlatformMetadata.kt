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

/**
 * What a file added to the work tree gets when nothing says otherwise: its SELinux label from the ROM's own
 * file_contexts rules and its owner, mode and capabilities from the ROM's fs_config tables or Android's defaults,
 * as Android's build would give them. Read from the assembled work tree ([load]).
 */
public class PlatformMetadata(public val contexts: FileContextRules, public val owners: OwnerRules) {
    /** The label the ROM's rules give [path] (inside `work/<partition>/`), or null when none applies. */
    public fun label(partition: String, path: String, kind: EntryKind): String? =
        contexts.label(SidecarNormalizer.fileContextPath(partition, path), kind)

    public fun owner(partition: String, path: String, isDirectory: Boolean): Pair<Owner, OwnerRules.Source> =
        owners.owner(SidecarNormalizer.fsConfigPath(partition, path), isDirectory)

    public companion object {
        public val NONE: PlatformMetadata = PlatformMetadata(FileContextRules.EMPTY, OwnerRules.ANDROID_ONLY)

        /**
         * The rule files inside `work/`, in the order Android loads them. system_ext and product may be folded into
         * system; `nonplat_file_contexts` is vendor's name for its rules before Android 9.
         */
        public val CONTEXT_FILES: List<String> = listOf(
            "system/system/etc/selinux/plat_file_contexts",
            "system/system/system_ext/etc/selinux/system_ext_file_contexts",
            "system_ext/etc/selinux/system_ext_file_contexts",
            "system/system/product/etc/selinux/product_file_contexts",
            "product/etc/selinux/product_file_contexts",
            "vendor/etc/selinux/vendor_file_contexts",
            "vendor/etc/selinux/nonplat_file_contexts",
            "odm/etc/selinux/odm_file_contexts",
        )

        /** Where Android 7 and older keep the rules, compiled: read only when no text rules exist. */
        public val COMPILED_FILES: List<String> = listOf("system/file_contexts.bin", "system/system/file_contexts.bin")

        /** The device's fs_config tables inside `work/`: `<base>/etc/fs_config_dirs` and `fs_config_files`. */
        public val OWNER_TABLE_BASES: List<String> = listOf(
            "system/system",
            "system/system/system_ext",
            "system_ext",
            "system/system/product",
            "product",
            "vendor",
            "odm",
            "vendor_dlkm",
            "system_dlkm",
            "odm_dlkm",
        )

        /** Every file [load] may read, relative to `work/`. */
        public val FILES: List<String>
            get() = CONTEXT_FILES + COMPILED_FILES + OWNER_TABLE_BASES.flatMap {
                listOf("$it/etc/fs_config_dirs", "$it/etc/fs_config_files")
            }

        /**
         * The work tree's rules; [read] gives a file's bytes (a path relative to `work/`), or null when it is absent.
         * A file that cannot be read as rules is skipped: Android's defaults and the folder still answer.
         */
        public fun load(read: (String) -> ByteArray?): PlatformMetadata {
            val texts = CONTEXT_FILES.mapNotNull { read(it)?.decodeToString() }
            val rules = texts.ifEmpty {
                COMPILED_FILES.mapNotNull { file ->
                    read(file)?.let { bytes -> runCatching { CompiledFileContexts.decode(bytes) }.getOrNull() }
                }
            }
            fun tables(name: String) = OWNER_TABLE_BASES.flatMap { base ->
                read("$base/etc/$name")?.let(OwnerRules::decodeTable).orEmpty()
            }
            return PlatformMetadata(
                FileContextRules.parse(rules),
                OwnerRules(tables("fs_config_dirs"), tables("fs_config_files")),
            )
        }
    }
}
