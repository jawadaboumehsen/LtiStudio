/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.diff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigDiffEngineTest {

    private val originalBuildProp = """
        # Build properties
        ro.product.model=Pixel 8
        ro.build.type=user
        ro.debuggable=0
        ro.secure=1
        ro.crypto.state=encrypted
        persist.sys.usb.config=adb
    """.trimIndent()

    private val modifiedBuildProp = """
        # Modified build properties
        ro.product.model=Pixel 8 Pro
        ro.build.type=userdebug
        ro.debuggable=1
        ro.secure=0
        ro.crypto.state=unencrypted
        persist.sys.phh.root=1
    """.trimIndent()

    @Test
    fun testParseProperties() {
        val props = ConfigDiffEngine.parseProperties(originalBuildProp)
        assertEquals(6, props.size)
        assertEquals("Pixel 8", props["ro.product.model"])
        assertEquals("encrypted", props["ro.crypto.state"])
    }

    @Test
    fun testComputeDiffCategories() {
        val result = ConfigDiffEngine.computeDiff(originalBuildProp, modifiedBuildProp)

        assertEquals(1, result.addedCount) // persist.sys.phh.root
        assertEquals(1, result.removedCount) // persist.sys.usb.config
        assertEquals(5, result.modifiedCount) // model, build.type, debuggable, secure, crypto.state
        assertTrue(result.hasCriticalRisks)

        // Check critical risk on crypto state
        val cryptoEntry = result.entries.first { it.key == "ro.crypto.state" }
        assertEquals(PropertyRiskLevel.CRITICAL, cryptoEntry.riskLevel)
        assertEquals(DiffKind.MODIFIED, cryptoEntry.kind)
        assertEquals("encrypted", cryptoEntry.oldValue)
        assertEquals("unencrypted", cryptoEntry.newValue)

        // Check warning on ro.secure
        val secureEntry = result.entries.first { it.key == "ro.secure" }
        assertEquals(PropertyRiskLevel.WARNING, secureEntry.riskLevel)

        // Check info on ro.debuggable
        val debuggableEntry = result.entries.first { it.key == "ro.debuggable" }
        assertEquals(PropertyRiskLevel.INFO, debuggableEntry.riskLevel)
    }

    @Test
    fun testSnapshotAndRollback() {
        val snapshot = ConfigDiffEngine.createSnapshot(
            id = "snap-001",
            name = "Pre-flash Stock",
            content = originalBuildProp,
            timestamp = 1700000000000L,
        )

        assertEquals("snap-001", snapshot.id)
        assertEquals("Pre-flash Stock", snapshot.name)
        assertEquals(6, snapshot.properties.size)

        val rolledBack = ConfigDiffEngine.rollbackTo(snapshot)
        assertEquals(originalBuildProp, rolledBack)
    }

    @Test
    fun testIdenticalContentHasNoDiff() {
        val result = ConfigDiffEngine.computeDiff(originalBuildProp, originalBuildProp)
        assertEquals(0, result.addedCount)
        assertEquals(0, result.removedCount)
        assertEquals(0, result.modifiedCount)
        assertTrue(result.entries.isEmpty())
        assertFalse(result.hasCriticalRisks)
    }
}
