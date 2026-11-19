/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workbench

import kotlinx.datetime.Instant
import org.ide.lti.core.domain.workspace.RunRecord
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.ArtifactKind
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceTestTest {
    private fun result(answers: String) = DeviceCheckResult(
        runId = "r1",
        checkedAt = "2026-11-19T10:00:00Z",
        serial = "ABC",
        model = "Phone",
        items = DeviceTest.items(answers, stamp = "1763540000"),
    )

    @Test
    fun aHealthyPhoneRunningThisBuildPasses() {
        val check = result("stamp=1763540000\nbooted=1\nselinux=Enforcing\nvb=orange\n")
        assertTrue(check.installed && check.passed)
        assertEquals(DeviceVerdict.NOTE, check.items.first { it.name == "Verified boot" }.verdict)
        assertTrue("every check passed" in check.markdown())
    }

    @Test
    fun anotherBuildOrACrashIsAFailureAndPermissiveIsOnlyANote() {
        val other = result("stamp=1700000000\nbooted=1\nselinux=Enforcing\nvb=orange\n")
        assertFalse(other.installed)
        assertTrue("the phone runs build 1700000000" in other.items.first().detail)
        val stock = result("stamp=\nbooted=1\n")
        assertTrue("runs no LtiRom build" in stock.items.first().detail)

        val crashed = result(
            "stamp=1763540000\nbooted=1\nselinux=Permissive\nvb=orange\n" +
                "crash=com.android.systemui\ncrash=/system/bin/surfaceflinger\ncrash=com.android.systemui\n",
        )
        assertTrue(crashed.installed)
        assertFalse(crashed.passed)
        assertEquals(
            "com.android.systemui, /system/bin/surfaceflinger",
            crashed.items.first { it.name == "Crashes since boot" }.detail,
        )
        assertEquals(DeviceVerdict.NOTE, crashed.items.first { it.name == "SELinux" }.verdict)
    }

    @Test
    fun aCheckIsKeptWithTheRunEvenWhenTheRunPredatesRecords() {
        val check = result("stamp=1763540000\nbooted=1\nselinux=Enforcing\nvb=orange\n")
        val json = ConfigurationSnapshot.canonicalJson.encodeToString(DeviceCheckResult.serializer(), check)
        // An old run: the folder holds only the check, no keys.tsv, so no "record" row.
        val old = RunRecord.parse("device-check.json\t$json\n")
        assertFalse(old.present)
        assertEquals(check, old.deviceCheck)
        val current = RunRecord.parse("record\tyes\ndevice-check.json\t$json\ncert\tAA\n")
        assertTrue(current.present)
        assertEquals(check, current.deviceCheck)
    }

    @Test
    fun onlyASucceededRunWithAPackageCanBeCheckedOnThePhone() {
        val zip = Artifact(ArtifactKind.FLASHABLE_ZIP, "/ws/out/rom.zip", sizeBytes = 1, sha256 = "a")
        val built = BuildRun("r", "w", "s", RunState.SUCCEEDED, Instant.fromEpochSeconds(0), artifacts = listOf(zip))
        assertTrue(DeviceTest.madePackage(built))
        // Run through Assemble: succeeded, but nothing to install.
        assertFalse(DeviceTest.madePackage(built.copy(artifacts = emptyList())))
        assertFalse(DeviceTest.madePackage(built.copy(state = RunState.FAILED)))
    }
}
