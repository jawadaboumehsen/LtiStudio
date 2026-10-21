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

import org.ide.lti.core.model.plugin.FileMetadata
import kotlin.test.Test
import kotlin.test.assertEquals

class SidecarEditorTest {
    private val fsConfig = "/ 0 2000 755 capabilities=0x0\nvendor/etc 0 2000 755 capabilities=0x0\n"
    private val fileContext = "/vendor u:object_r:vendor_file:s0\n/vendor/etc u:object_r:vendor_configs_file:s0\n"

    @Test
    fun anAddedFileGetsItsMissingParentsAndKeepsExistingEntries() {
        val fs = SidecarEditor.withFsConfig(fsConfig, "vendor", "etc/init/x.rc", isDirectory = false)
        assertEquals(
            fsConfig + "vendor/etc/init 0 0 755 capabilities=0x0\nvendor/etc/init/x.rc 0 0 644 capabilities=0x0\n",
            fs,
        )
        assertEquals(fs, SidecarEditor.withFsConfig(fs, "vendor", "etc/init/x.rc", isDirectory = false))
    }

    @Test
    fun anAddedFileIsLabelledLikeItsClosestListedParent() {
        val ctx = SidecarEditor.withFileContext(fileContext, "vendor", "etc/init/x.rc")
        assertEquals(
            fileContext + "/vendor/etc/init u:object_r:vendor_configs_file:s0\n" +
                "/vendor/etc/init/x\\.rc u:object_r:vendor_configs_file:s0\n",
            ctx,
        )
    }

    @Test
    fun systemPathsAreRelativeToTheImageRoot() {
        val root = " 0 0 755 capabilities=0x0\n"
        val fs = SidecarEditor.withFsConfig(root, "system", "system/bin/x", isDirectory = false)
        assertEquals(
            " 0 0 755 capabilities=0x0\nsystem 0 0 755 capabilities=0x0\nsystem/bin 0 0 755 capabilities=0x0\n" +
                "system/bin/x 0 0 644 capabilities=0x0\n",
            fs,
        )
    }

    @Test
    fun explicitMetadataReplacesTheFilesOwnEntries() {
        val meta = FileMetadata(uid = 0, gid = 2000, mode = "0755", label = "u:object_r:toolbox_exec:s0")
        val fs = SidecarEditor.withFsConfig(fsConfig, "vendor", "etc", isDirectory = true, metadata = meta)
        assertEquals("/ 0 2000 755 capabilities=0x0\nvendor/etc 0 2000 0755 capabilities=0x0\n", fs)
        val ctx = SidecarEditor.withFileContext(fileContext, "vendor", "etc", meta.label)
        assertEquals("/vendor u:object_r:vendor_file:s0\n/vendor/etc u:object_r:toolbox_exec:s0\n", ctx)
    }
}
