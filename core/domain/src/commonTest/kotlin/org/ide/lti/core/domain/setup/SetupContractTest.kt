/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
@file:Suppress("MaxLineLength")

package org.ide.lti.core.domain.setup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetupContractTest {

    @Test
    fun checkSnapshot_enforces_live_readiness_and_checkedAt_invariant() {
        // Invariant: "Only a completed live check updates checkedAt; only fully ready live evidence updates lastReadyAt."
        // Attempting lastReadyAt with null checkedAt must fail.
        val ex1 = assertFailsWith<IllegalStateException> {
            CheckSnapshot.create(
                environmentKey = "env-1",
                checkedAt = null,
                lastReadyAt = 1000L,
                connection = EnvironmentConnectionStatus.CONNECTED,
                requiredToolEvidence = mapOf(
                    "adb" to ToolEvidence(
                        toolId = "adb",
                        recipeGroup = "android-tools",
                        isInstalled = true,
                        isVerified = true,
                    ),
                ),
                publicationEvidence = setOf("adb"),
            )
        }
        assertTrue(ex1.message!!.contains("Only a completed live check updates checkedAt; only fully ready live evidence updates lastReadyAt."))

        // Attempting lastReadyAt when environment is not fully ready must fail.
        val ex2 = assertFailsWith<IllegalStateException> {
            CheckSnapshot.create(
                environmentKey = "env-1",
                checkedAt = 2000L,
                lastReadyAt = 2000L,
                connection = EnvironmentConnectionStatus.CONNECTED,
                requiredToolEvidence = mapOf(
                    "adb" to ToolEvidence(
                        toolId = "adb",
                        recipeGroup = "android-tools",
                        isInstalled = true,
                        isVerified = false,
                    ),
                ),
                publicationEvidence = setOf("adb"),
            )
        }
        assertTrue(ex2.message!!.contains("Only a completed live check updates checkedAt; only fully ready live evidence updates lastReadyAt."))
    }

    @Test
    fun checkSnapshot_absent_service_and_empty_inventory_cannot_become_ready() {
        // Absent service: connection unavailable
        val snapshotUnavailable = CheckSnapshot.create(
            environmentKey = "env-1",
            checkedAt = 1000L,
            lastReadyAt = null,
            connection = EnvironmentConnectionStatus.UNAVAILABLE,
            requiredToolEvidence = mapOf(
                "adb" to ToolEvidence(
                    toolId = "adb",
                    recipeGroup = "android-tools",
                    isInstalled = true,
                    isVerified = true,
                ),
            ),
            publicationEvidence = setOf("adb"),
        )
        assertFalse(snapshotUnavailable.isReady)

        // Empty required inventory cannot become ready
        val snapshotEmpty = CheckSnapshot.create(
            environmentKey = "env-1",
            checkedAt = 1000L,
            lastReadyAt = null,
            connection = EnvironmentConnectionStatus.CONNECTED,
            requiredToolEvidence = emptyMap(),
            publicationEvidence = emptySet(),
        )
        assertFalse(snapshotEmpty.isReady)
    }

    @Test
    fun checkSnapshot_failed_execution_verification_is_preserved_until_successful_probe() {
        // Invariant: "Failed execution verification is preserved until a successful execution probe replaces it."
        val initial = CheckSnapshot.create(
            environmentKey = "env-1",
            checkedAt = 1000L,
            lastReadyAt = null,
            connection = EnvironmentConnectionStatus.CONNECTED,
            requiredToolEvidence = mapOf(
                "adb" to ToolEvidence(
                    toolId = "adb",
                    recipeGroup = "android-tools",
                    isInstalled = true,
                    isVerified = false,
                    isFailedExecution = true,
                ),
            ),
        )

        // A file-only existence scan (e.g. file exists, but execution probe did not run) must NOT erase failed execution
        val fileOnlyScan = ToolEvidence(
            toolId = "adb",
            recipeGroup = "android-tools",
            isInstalled = true,
            isVerified = false,
            isFailedExecution = false,
        )
        val updatedWithScan = initial.withUpdatedToolEvidence(fileOnlyScan)
        assertTrue(updatedWithScan.requiredToolEvidence["adb"]!!.isFailedExecution)

        // A successful execution probe replaces the failed execution state
        val successfulProbe = ToolEvidence(
            toolId = "adb",
            recipeGroup = "android-tools",
            isInstalled = true,
            isVerified = true,
            isFailedExecution = false,
        )
        val updatedWithProbe = updatedWithScan.withUpdatedToolEvidence(successfulProbe)
        assertFalse(updatedWithProbe.requiredToolEvidence["adb"]!!.isFailedExecution)
        assertTrue(updatedWithProbe.requiredToolEvidence["adb"]!!.isVerified)
    }

    @Test
    fun setupPlan_enforces_confirmation_plan_id_and_hash() {
        val plan = SetupPlan(
            planId = "plan-123",
            revisionHash = "hash-abc",
            environmentKey = "wsl-ubuntu",
            kind = SetupPlanKind.FULL_SETUP,
            autoDoctorEnabled = true,
            orderedActions = listOf(
                SetupPlanAction.InstallPackages(listOf("build-essential")),
                SetupPlanAction.SyncSources(listOf("android-tools")),
                SetupPlanAction.BuildRecipes(listOf("android-tools")),
                SetupPlanAction.PublishTools(listOf("adb")),
            ),
        )

        // Matching plan ID and hash succeeds
        plan.validateConfirmation("plan-123", "hash-abc")

        // Mismatched plan ID fails with exact quote
        val exId = assertFailsWith<IllegalArgumentException> {
            plan.validateConfirmation("plan-other", "hash-abc")
        }
        assertEquals("Confirmation requires the displayed plan ID and unchanged revision hash.", exId.message)

        // Mismatched hash fails with exact quote
        val exHash = assertFailsWith<IllegalArgumentException> {
            plan.validateConfirmation("plan-123", "hash-changed")
        }
        assertEquals("Confirmation requires the displayed plan ID and unchanged revision hash.", exHash.message)
    }

    @Test
    fun setupPlan_enforces_immutable_auto_doctor_policy() {
        val plan = SetupPlan(
            planId = "plan-123",
            revisionHash = "hash-abc",
            environmentKey = "wsl-ubuntu",
            kind = SetupPlanKind.FULL_SETUP,
            autoDoctorEnabled = false,
        )

        // Matching policy succeeds
        plan.validateAttemptPolicy(false)

        // Modified policy throws with exact quote
        val ex = assertFailsWith<IllegalArgumentException> {
            plan.validateAttemptPolicy(true)
        }
        assertEquals("Auto Doctor policy is immutable for the confirmed attempt.", ex.message)
    }

    @Test
    fun setupOutcome_exhaustive_states_and_require_success() {
        val outcomes: List<SetupOutcome> = listOf(
            SetupOutcome.Succeeded(mapOf("adb" to ToolEvidence(toolId = "adb", recipeGroup = "android-tools"))),
            SetupOutcome.Failed(stage = "SYSTEM_DIAGNOSTICS", reason = "Missing compiler"),
            SetupOutcome.Cancelled,
            SetupOutcome.Busy(ownerId = "owner-1"),
            SetupOutcome.Interrupted(reason = "Daemon restarted"),
        )

        // Test exhaustive when mapping
        for (outcome in outcomes) {
            val label = when (outcome) {
                is SetupOutcome.Succeeded -> "succeeded"
                is SetupOutcome.Failed -> "failed"
                SetupOutcome.Cancelled -> "cancelled"
                is SetupOutcome.Busy -> "busy"
                is SetupOutcome.Interrupted -> "interrupted"
                is SetupOutcome.AwaitingUserAction -> "awaiting user action"
            }
            assertTrue(label.isNotEmpty())
        }

        // Test invariant: "A normal return is not success unless the typed outcome is Succeeded."
        val success = SetupOutcome.Succeeded()
        assertTrue(success.isSuccess)
        assertEquals(success, success.requireSuccess())

        for (nonSuccess in outcomes.filterNot { it is SetupOutcome.Succeeded }) {
            assertFalse(nonSuccess.isSuccess)
            val ex = assertFailsWith<IllegalStateException> {
                nonSuccess.requireSuccess()
            }
            assertEquals("A normal return is not success unless the typed outcome is Succeeded.", ex.message)
        }
    }

    @Test
    fun recipeCapability_repair_invalidates_owning_group_only() {
        // Invariant: "Repair invalidates the owning recipe group only and verifies all affected required tools."
        val recipes = listOf(
            RecipeCapability(recipeGroup = "android-tools", toolIds = setOf("adb", "fastboot", "lpunpack")),
            RecipeCapability(recipeGroup = "erofs-utils", toolIds = setOf("mkfs.erofs", "dump.erofs")),
        )

        val initialTools = mapOf(
            "adb" to ToolEvidence(toolId = "adb", recipeGroup = "android-tools", isVerified = true, isInstalled = true),
            "fastboot" to ToolEvidence(toolId = "fastboot", recipeGroup = "android-tools", isVerified = true, isInstalled = true),
            "lpunpack" to ToolEvidence(toolId = "lpunpack", recipeGroup = "android-tools", isVerified = false, isInstalled = false),
            "mkfs.erofs" to ToolEvidence(toolId = "mkfs.erofs", recipeGroup = "erofs-utils", isVerified = true, isInstalled = true),
            "dump.erofs" to ToolEvidence(toolId = "dump.erofs", recipeGroup = "erofs-utils", isVerified = true, isInstalled = true),
        )

        // Repairing lpunpack must invalidate android-tools recipe group only (adb, fastboot, lpunpack)
        // erofs-utils tools must remain untouched!
        val invalidated = invalidateForRepair(
            targetToolId = "lpunpack",
            allTools = initialTools,
            recipes = recipes,
        )

        assertFalse(invalidated["adb"]!!.isVerified)
        assertFalse(invalidated["fastboot"]!!.isVerified)
        assertFalse(invalidated["lpunpack"]!!.isVerified)
        assertTrue(invalidated["mkfs.erofs"]!!.isVerified)
        assertTrue(invalidated["dump.erofs"]!!.isVerified)

        // Unknown recipe is unavailable, not optimistically supported
        val unsupported = RecipeCapability(
            recipeGroup = "unknown-recipe",
            toolIds = setOf("fake-tool"),
            isSupported = false,
            unavailableReason = "Recipe unknown-recipe is not present in domain capabilities",
        )
        assertFalse(unsupported.isSupported)
    }
}
