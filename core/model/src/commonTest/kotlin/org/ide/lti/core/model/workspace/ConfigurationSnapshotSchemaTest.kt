/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.workspace

import kotlinx.datetime.Instant
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ConfigurationSnapshotSchemaTest {

    private val baseSnapshot = ConfigurationSnapshot.create(
        id = "test-id",
        workspaceId = "ws-id",
        profileRevision = 1,
        acquisition = AcquisitionSettings(
            mode = AcquisitionMode.DOWNLOAD,
            region = TargetRegion.GLOBAL,
            firmware = TargetFirmware("v1", "test-url", "test-sha", "2023-01"),
        ),
        extraction = ExtractionSettings(adapter = "AUTO"),
        assembly = AssemblySettings(romVersion = "1.0"),
        debloat = DebloatSettings(enabled = true),
        customization = CustomizationSettings(enabledPackages = listOf("pkg1")),
        build = BuildSettings(compression = "lz4hc,9"),
        release = ReleaseSettings(otaBaseUrl = "https://example.com/updates"),
        publish = PublishSettings(provider = PublishProvider.NONE),
        createdAt = Instant.parse("2023-01-01T00:00:00Z"),
    )

    @Test
    fun `round trips through canonicalJson with every group intact`() {
        val json = ConfigurationSnapshot.canonicalJson.encodeToString(ConfigurationSnapshot.serializer(), baseSnapshot)
        val decoded = ConfigurationSnapshot.canonicalJson.decodeFromString(ConfigurationSnapshot.serializer(), json)
        assertEquals(baseSnapshot, decoded)
    }

    @Test
    fun `digest is deterministic for equal input`() {
        val snap2 = ConfigurationSnapshot.create(
            id = "test-id",
            workspaceId = "ws-id",
            profileRevision = 1,
            acquisition = baseSnapshot.acquisition,
            extraction = baseSnapshot.extraction,
            assembly = baseSnapshot.assembly,
            debloat = baseSnapshot.debloat,
            customization = baseSnapshot.customization,
            build = baseSnapshot.build,
            release = baseSnapshot.release,
            publish = baseSnapshot.publish,
            createdAt = baseSnapshot.createdAt,
        )
        assertEquals(baseSnapshot.digest, snap2.digest)
    }

    @Test
    fun `digest changes when any single field in any group changes`() {
        val mods = listOf(
            baseSnapshot.copy(acquisition = baseSnapshot.acquisition.copy(retries = 1)),
            baseSnapshot.copy(extraction = baseSnapshot.extraction.copy(adapter = "MANUAL")),
            baseSnapshot.copy(assembly = baseSnapshot.assembly.copy(buildType = "eng")),
            baseSnapshot.copy(debloat = baseSnapshot.debloat.copy(enabled = false)),
            baseSnapshot.copy(customization = baseSnapshot.customization.copy(enabledPackages = listOf("pkg2"))),
            baseSnapshot.copy(build = baseSnapshot.build.copy(compression = "gz")),
            baseSnapshot.copy(release = baseSnapshot.release.copy(otaBaseUrl = "https://example.com/other")),
            baseSnapshot.copy(publish = baseSnapshot.publish.copy(provider = PublishProvider.LOCAL_EXPORT)),
            baseSnapshot.copy(workspaceId = "ws-id-2"),
            baseSnapshot.copy(profileRevision = 2),
        )

        val baseDigest = ConfigurationSnapshot.computeDigest(
            workspaceId = baseSnapshot.workspaceId,
            profileRevision = baseSnapshot.profileRevision,
            acquisition = baseSnapshot.acquisition,
            extraction = baseSnapshot.extraction,
            assembly = baseSnapshot.assembly,
            debloat = baseSnapshot.debloat,
            customization = baseSnapshot.customization,
            build = baseSnapshot.build,
            release = baseSnapshot.release,
            publish = baseSnapshot.publish,
        )

        mods.forEach { mod ->
            val newDigest = ConfigurationSnapshot.computeDigest(
                workspaceId = mod.workspaceId,
                profileRevision = mod.profileRevision,
                acquisition = mod.acquisition,
                extraction = mod.extraction,
                assembly = mod.assembly,
                debloat = mod.debloat,
                customization = mod.customization,
                build = mod.build,
                release = mod.release,
                publish = mod.publish,
            )
            assertNotEquals(baseDigest, newDigest, "Digest should have changed for modification: $mod")
        }
    }

    @Test
    fun `unknown keys are ignored`() {
        // Inject unknown keys into the real serialized form (top level and inside a group) rather than
        // hand-writing JSON, so the fixture cannot drift from how TargetRegion/TargetFirmware serialize.
        val json = ConfigurationSnapshot.canonicalJson.encodeToString(ConfigurationSnapshot.serializer(), baseSnapshot)
            .replaceFirst("{", """{"pipelineVersion":2,"legacyField":"ignored",""")
            .replaceFirst(""""acquisition":{""", """"acquisition":{"unknownField":"ignored",""")

        val decoded = ConfigurationSnapshot.canonicalJson.decodeFromString(ConfigurationSnapshot.serializer(), json)
        assertEquals(baseSnapshot, decoded)
    }

    @Test
    fun `init checks reject invalid data`() {
        assertFailsWith<IllegalArgumentException> {
            AcquisitionSettings(
                mode = AcquisitionMode.DOWNLOAD,
                region = TargetRegion.GLOBAL,
                firmware = TargetFirmware("v1", "test-url", "test-sha", "2023-01"),
                retries = 6,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ReleaseSettings(otaBaseUrl = "http://example.com")
        }
        assertFailsWith<IllegalArgumentException> {
            ReleaseSettings(otaBaseUrl = "https://example.com/")
        }
        assertFailsWith<IllegalArgumentException> {
            AssemblySettings(propertyOverrides = mapOf("key\n1" to "value"))
        }
        assertFailsWith<IllegalArgumentException> {
            AcquisitionSettings(
                mode = AcquisitionMode.IMPORT_ARCHIVE,
                region = TargetRegion.GLOBAL,
                firmware = TargetFirmware("v1", "test-url", "test-sha", "2023-01"),
                archiveRef = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AcquisitionSettings(
                mode = AcquisitionMode.IMPORT_ARCHIVE,
                region = TargetRegion.GLOBAL,
                firmware = TargetFirmware("v1", "test-url", "test-sha", "2023-01"),
                archiveRef = "",
            )
        }
    }
}
