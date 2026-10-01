/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.setup

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToolSelectionSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Test
    fun toolRefPolymorphicRoundtrip() {
        val tag: ToolRef = ToolRef.Tag("v1.2.3")
        val branch: ToolRef = ToolRef.Branch("feature/custom-tools")
        val commit: ToolRef = ToolRef.Commit("0123456789abcdef0123456789abcdef01234567")
        val release: ToolRef = ToolRef.ReleaseVersion("34.0.5")

        assertEquals(tag, json.decodeFromString<ToolRef>(json.encodeToString(tag)))
        assertEquals(branch, json.decodeFromString<ToolRef>(json.encodeToString(branch)))
        assertEquals(commit, json.decodeFromString<ToolRef>(json.encodeToString(commit)))
        assertEquals(release, json.decodeFromString<ToolRef>(json.encodeToString(release)))
    }

    @Test
    fun toolSelectionRoundtripAndUnknownFieldsIgnored() {
        val selection = ToolSelection(
            group = "android-tools",
            repoUrl = "https://github.com/android-tools/android-tools.git",
            ref = ToolRef.Tag("34.0.5"),
        )
        val encoded = json.encodeToString(selection)
        val decoded = json.decodeFromString<ToolSelection>(encoded)
        assertEquals(selection, decoded)

        val jsonWithUnknownFields = """
            {
                "group": "android-tools",
                "repoUrl": "https://github.com/android-tools/android-tools.git",
                "ref": {
                    "type": "Tag",
                    "name": "34.0.5"
                },
                "unknownField": "unexpected_data",
                "nestedIgnored": { "flag": true }
            }
        """.trimIndent()
        val decodedWithUnknown = json.decodeFromString<ToolSelection>(jsonWithUnknownFields)
        assertEquals(selection, decodedWithUnknown)
    }

    @Test
    fun distroToolSelectionsRoundtripWithDefaults() {
        val emptySelections = DistroToolSelections()
        val encodedEmpty = json.encodeToString(emptySelections)
        val decodedEmpty = json.decodeFromString<DistroToolSelections>(encodedEmpty)
        assertEquals(emptySelections, decodedEmpty)
        assertEquals(0L, decodedEmpty.revision)
        assertEquals(emptyMap(), decodedEmpty.desired)

        val populated = DistroToolSelections(
            desired = mapOf(
                "android-tools" to ToolSelection(
                    group = "android-tools",
                    repoUrl = null,
                    ref = ToolRef.Branch("master"),
                ),
                "apktool" to ToolSelection(
                    group = "apktool",
                    repoUrl = "ssh://github.com/iBotPeaches/Apktool.git",
                    ref = ToolRef.Commit("abcdef0123456789abcdef0123456789abcdef01"),
                ),
            ),
            revision = 42L,
        )
        val encodedPopulated = json.encodeToString(populated)
        val decodedPopulated = json.decodeFromString<DistroToolSelections>(encodedPopulated)
        assertEquals(populated, decodedPopulated)
    }

    @Test
    fun pendingSwitchRecordRoundtrip() {
        for (checkpoint in SwitchCheckpoint.entries) {
            val record = PendingSwitchRecord(
                activationRequestId = "act-12345",
                expectedActiveInstallId = if (checkpoint == SwitchCheckpoint.OUTCOME_UNKNOWN) null else "inst-old",
                targetInstallId = "inst-new",
                selectionRevision = 7L,
                checkpoint = checkpoint,
            )
            val encoded = json.encodeToString(record)
            val decoded = json.decodeFromString<PendingSwitchRecord>(encoded)
            assertEquals(record, decoded)
        }
    }

    @Test
    fun toolchainPersistenceStatePreservesDefaultsOnLegacyJson() {
        val legacyJson = """
            {
                "isSetupCompleted": true,
                "completedStages": ["doctor", "packages", "sources", "toolchain"],
                "passedDiagnosticIds": ["wsl2", "ram"]
            }
        """.trimIndent()

        val decoded = json.decodeFromString<ToolchainPersistenceState>(legacyJson)
        assertEquals(true, decoded.isSetupCompleted)
        assertEquals(setOf("doctor", "packages", "sources", "toolchain"), decoded.completedStages)
        assertEquals(emptyMap(), decoded.toolSelections)
        assertEquals(emptySet(), decoded.trustedRepositories)
        assertNull(decoded.pendingSwitch)
    }

    @Test
    fun toolchainPersistenceStateRoundtripWithToolSelections() {
        val fullState = ToolchainPersistenceState(
            isSetupCompleted = true,
            activeDistro = "Ubuntu-24.04",
            toolSelections = mapOf(
                "Ubuntu-24.04" to DistroToolSelections(
                    desired = mapOf(
                        "android-tools" to ToolSelection(
                            group = "android-tools",
                            repoUrl = null,
                            ref = ToolRef.Tag("34.0.5"),
                        ),
                    ),
                    revision = 1L,
                ),
            ),
            trustedRepositories = setOf("https://github.com/custom/android-tools.git"),
            pendingSwitch = PendingSwitchRecord(
                activationRequestId = "req-001",
                expectedActiveInstallId = "inst-v1",
                targetInstallId = "inst-v2",
                selectionRevision = 1L,
                checkpoint = SwitchCheckpoint.ACTIVATION_COMMITTED,
            ),
        )

        val encoded = json.encodeToString(fullState)
        val decoded = json.decodeFromString<ToolchainPersistenceState>(encoded)
        assertEquals(fullState, decoded)
    }
}
