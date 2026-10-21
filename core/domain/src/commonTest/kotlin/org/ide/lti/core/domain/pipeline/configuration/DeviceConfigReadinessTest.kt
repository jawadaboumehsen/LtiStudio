/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.configuration

import org.ide.lti.core.domain.pipeline.PipelineFixture
import org.ide.lti.core.model.target.PackagePolicy
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.SigningPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceConfigReadinessTest {
    private val fixture = PipelineFixture()
    private val target = fixture.targetGlobal
    private val snapshot = fixture.snapshotGlobalOta

    private fun codes(t: TargetDevice = target, s: ConfigurationSnapshot = snapshot) =
        DeviceConfigReadiness.check(t, s).map { it.fieldPath to it.code }

    private fun withFirmware(change: (TargetFirmware) -> TargetFirmware) = snapshot.copy(
        acquisition = snapshot.acquisition.copy(firmware = change(snapshot.acquisition.firmware)),
    )

    @Test
    fun `a complete PQ84P01 config has nothing blocking`() {
        assertEquals(emptyList(), DeviceConfigReadiness.check(target, snapshot))
    }

    @Test
    fun `identity fields are required and the codename is a plain token`() {
        val found = codes(target.copy(name = "", productName = " ", fastbootProduct = "", codename = "a b"))
        assertTrue("name" to DeviceConfigReadiness.REQUIRED in found)
        assertTrue("productName" to DeviceConfigReadiness.REQUIRED in found)
        assertTrue("fastbootProduct" to DeviceConfigReadiness.REQUIRED in found)
        assertTrue("codename" to DeviceConfigReadiness.INVALID_FORMAT in found)
    }

    @Test
    fun `a download needs a url and exactly one of checksum or unavailable`() {
        assertTrue(
            "firmware.acquisitionUrl" to DeviceConfigReadiness.REQUIRED in
                codes(s = withFirmware { it.copy(acquisitionUrl = null, otaUrl = null) }),
        )
        assertTrue(
            "firmware.sha256" to DeviceConfigReadiness.REQUIRED in
                codes(s = withFirmware { it.copy(sha256 = null) }),
        )
        assertTrue(
            "firmware.sha256" to DeviceConfigReadiness.INVALID_FORMAT in
                codes(s = withFirmware { it.copy(sha256 = "ABC") }),
        )
        assertTrue(
            "firmware.sha256" to DeviceConfigReadiness.CHECKSUM_CONFLICT in
                codes(s = withFirmware { it.copy(checksumUnavailable = true) }),
        )
        assertEquals(
            emptyList(),
            DeviceConfigReadiness.check(
                target,
                withFirmware { it.copy(sha256 = null, checksumUnavailable = true) },
            ),
        )
    }

    @Test
    fun `partitions must be known, unique and non empty and boot partitions need slots`() {
        val found = codes(
            target.copy(
                dynamicPartitions = listOf("system", "system", "bogus"),
                bootPartitions = emptyList(),
            ),
        )
        assertTrue("dynamicPartitions" to DeviceConfigReadiness.DUPLICATE_PARTITION in found)
        assertTrue("dynamicPartitions" to DeviceConfigReadiness.UNKNOWN_PARTITION in found)
        assertTrue("bootPartitions" to DeviceConfigReadiness.REQUIRED in found)

        val noSlots = target.copy(packagePolicy = PackagePolicy(bootSlots = emptyList()))
        assertTrue("packagePolicy.bootSlots" to DeviceConfigReadiness.REQUIRED in codes(noSlots))
    }

    @Test
    fun `the group must fit in the super partition and metadata slots must be positive`() {
        val found = codes(
            target.copy(superGroupBytes = target.superPartitionBytes + 1, superMetadataSlots = 0, superGroupName = ""),
        )
        assertTrue("superGroupBytes" to DeviceConfigReadiness.INVALID_SIZE in found)
        assertTrue("superMetadataSlots" to DeviceConfigReadiness.INVALID_SIZE in found)
        assertTrue("superGroupName" to DeviceConfigReadiness.REQUIRED in found)
    }

    @Test
    fun `boot chain sizes matter only for partitions that are listed`() {
        val noSize = codes(target.copy(dtboPartitionBytes = 0))
        assertTrue("dtboPartitionBytes" to DeviceConfigReadiness.INVALID_SIZE in noSize)
        val noDtbo = target.copy(dtboPartitionBytes = 0, bootPartitions = listOf("boot", "init_boot", "vendor_boot"))
        assertEquals(emptyList(), DeviceConfigReadiness.check(noDtbo, snapshot))
        val badPath = codes(target.copy(bootDevicePath = "dev"))
        assertTrue("bootDevicePath" to DeviceConfigReadiness.INVALID_FORMAT in badPath)
    }

    @Test
    fun `signing stays on and key references are never inline key text`() {
        fun signing(p: SigningPolicy) = snapshot.copy(build = snapshot.build.copy(signing = p))
        assertTrue(
            "signing.signImages" to DeviceConfigReadiness.SIGNING_DISABLED in
                codes(s = signing(SigningPolicy(signImages = false))),
        )
        assertTrue(
            "signing.avbKeyRef" to StageSettingsValidator.INLINE_CREDENTIAL_NOT_ALLOWED in
                codes(s = signing(SigningPolicy(avbKeyRef = "-----BEGIN PRIVATE KEY-----"))),
        )
        assertTrue(
            "signing.avbKeyRef" to StageSettingsValidator.MISSING_KEY_REF in
                codes(s = signing(SigningPolicy(avbKeyRef = ""))),
        )
    }
}
