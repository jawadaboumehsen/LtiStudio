/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.workspace

import kotlinx.datetime.Clock
import okio.FileSystem
import org.ide.lti.core.model.workspace.EnvironmentId
import org.ide.lti.core.model.workspace.WorkspaceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkDirDiscoveryServiceTest {

    private val fs = FileSystem.SYSTEM

    @Test
    fun testDiscoversOnlyWorkspacesSubdirectoryWithValidManifest() {
        val tempDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "lti-disc-test-${Clock.System.now().toEpochMilliseconds()}"
        val workspacesDir = tempDir / "workspaces"
        val validWsDir = workspacesDir / "valid_project"
        val emptyWsDir = workspacesDir / "empty_project"
        val corruptWsDir = workspacesDir / "corrupt_project"
        val otherDir = tempDir / "other_dir"

        fs.createDirectories(validWsDir)
        fs.createDirectories(emptyWsDir)
        fs.createDirectories(corruptWsDir)
        fs.createDirectories(otherDir)

        // 1. Valid workspace manifest
        fs.write(validWsDir / "workspace.json") {
            writeUtf8(
                """{
                    "id": "ws-valid-1",
                    "layoutVersion": 1,
                    "targetBinding": {
                        "profileId": "PQ84P01",
                        "profileRevision": 1
                    },
                    "createdAt": "2026-09-12T00:00:00Z",
                    "appVersion": "1.0.0"
                }
                """.trimIndent(),
            )
        }

        // 2. Corrupt workspace manifest
        fs.write(corruptWsDir / "workspace.json") {
            writeUtf8("{ not valid json }")
        }

        // 3. Manifest outside workspaces/ directory (should be ignored)
        fs.write(otherDir / "workspace.json") {
            writeUtf8(
                """{
                    "id": "ws-outside",
                    "layoutVersion": 1,
                    "targetBinding": {
                        "profileId": "PQ84P01",
                        "profileRevision": 1
                    },
                    "createdAt": "2026-09-12T00:00:00Z",
                    "appVersion": "1.0.0"
                }
                """.trimIndent(),
            )
        }

        // 4. Loose file in workspaces/ (should be ignored)
        fs.write(workspacesDir / "notes.txt") {
            writeUtf8("just a note")
        }

        try {
            val service = WorkDirDiscoveryService(fileSystem = fs)
            val discovered = service.discoverWorkspaces(basePathOverride = tempDir.toString())

            assertEquals(1, discovered.size)
            val ws = discovered.first()
            assertEquals("ws-valid-1", ws.workspaceId)
            assertEquals("valid_project", ws.name)
            assertEquals("~/LtiRomWorkDir/workspaces/valid_project", ws.path)
            assertEquals(WorkspaceType.REMOTE_WSL, ws.type)
            assertEquals("PQ84P01", ws.targetBinding?.profileId)
        } finally {
            try {
                fs.deleteRecursively(tempDir)
            } catch (_: Exception) {}
        }
    }

    @Test
    fun testDynamicEnvironmentIdResolutionWithoutHardcodedPaths() {
        val tempDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "lti-env-test-${Clock.System.now().toEpochMilliseconds()}"
        val wsDir = tempDir / "workspaces" / "fedora_rom"
        fs.createDirectories(wsDir)

        fs.write(wsDir / "workspace.json") {
            writeUtf8(
                """{
                    "id": "ws-fedora-1",
                    "layoutVersion": 1,
                    "targetBinding": {
                        "profileId": "CUSTOM_DEVICE",
                        "profileRevision": 2
                    },
                    "createdAt": "2026-09-12T12:00:00Z",
                    "appVersion": "1.0.0"
                }
                """.trimIndent(),
            )
        }

        val customEnv = EnvironmentId(
            distro = "FedoraRemix",
            user = "developer",
            daemonId = "daemon-42",
        )

        try {
            val service = WorkDirDiscoveryService(
                fileSystem = fs,
                environmentId = customEnv,
            )

            val discovered = service.discoverWorkspaces(basePathOverride = tempDir.toString())
            assertEquals(1, discovered.size)
            assertEquals("ws-fedora-1", discovered.first().workspaceId)
            assertEquals("fedora_rom", discovered.first().name)
        } finally {
            try {
                fs.deleteRecursively(tempDir)
            } catch (_: Exception) {}
        }
    }

    @Test
    fun testEmptyWhenDirectoryDoesNotExist() {
        val service = WorkDirDiscoveryService(
            fileSystem = fs,
            environmentId = EnvironmentId(distro = "NonExistentDistro", user = "no_user", daemonId = "0"),
        )
        val discovered = service.discoverWorkspaces(basePathOverride = "/non/existent/path/xyz")
        assertTrue(discovered.isEmpty())
    }
}
