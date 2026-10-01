/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import org.ide.lti.core.domain.setup.ToolCategory
import org.ide.lti.feature.setup.api.SetupTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SetupNavigationTest {

    @Test
    fun testSetupTabFind() {
        assertEquals(SetupTab.PROJECTS, SetupTab.find("projects"))
        assertEquals(SetupTab.ENVIRONMENT, SetupTab.find("environment"))
        assertEquals(SetupTab.TOOLS, SetupTab.find("tools"))
        assertEquals(SetupTab.RECOVERY, SetupTab.find("recovery"))

        assertNull(SetupTab.find("invalid_tab"))
        assertNull(SetupTab.find(""))
    }

    @Test
    fun testToolNavPackageFind() {
        assertEquals(ToolNavPackage.ALL, ToolNavPackage.find("all_tools"))
        assertEquals(ToolNavPackage.FIRMWARE, ToolNavPackage.find("firmware"))
        assertEquals(ToolNavPackage.FILESYSTEMS, ToolNavPackage.find("filesystems"))
        assertEquals(ToolNavPackage.SIGNING, ToolNavPackage.find("signing"))

        assertNull(ToolNavPackage.find("unknown_pkg"))
        assertNull(ToolNavPackage.find(""))
    }

    @Test
    fun testToolNavPackageFromCategory() {
        assertEquals(ToolNavPackage.ALL, ToolNavPackage.fromCategory(null))
        assertEquals(ToolNavPackage.FIRMWARE, ToolNavPackage.fromCategory(ToolCategory.BOOT_AND_KERNEL))
        assertEquals(ToolNavPackage.FIRMWARE, ToolNavPackage.fromCategory(ToolCategory.DYNAMIC_PARTITIONS))
        assertEquals(ToolNavPackage.FILESYSTEMS, ToolNavPackage.fromCategory(ToolCategory.FILESYSTEM_AND_IMAGES))
        assertEquals(ToolNavPackage.SIGNING, ToolNavPackage.fromCategory(ToolCategory.SIGNING_AND_SECURITY))
    }
}
