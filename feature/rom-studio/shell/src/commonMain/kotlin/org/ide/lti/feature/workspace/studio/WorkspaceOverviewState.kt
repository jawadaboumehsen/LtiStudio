/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio

import androidx.compose.runtime.Immutable
import org.ide.lti.core.model.run.StageId

/**
 * Fixed five-item readiness matrix concerns in specification order.
 */
public enum class ReadinessId(public val displayName: String) {
    SOURCE("Source firmware"),
    DEBLOAT("Debloat"),
    PATCHES("Patches"),
    SIGNING("Signing key"),
    PUBLICATION("Publication"),
}

/**
 * Severity indicator for readiness concerns.
 */
public enum class ReadinessSeverity {
    Ready,
    Warning,
    Blocking,
    Neutral,
    Running,
    Failed,
}

/**
 * Exhaustive typed actions that can be triggered from workspace overview.
 */
@Immutable
public sealed interface WorkspaceActionUi {
    public data class OpenStage(val stageId: StageId, val objectId: String? = null) : WorkspaceActionUi

    public data class OpenWorkspaceSection(val section: WorkspaceSection) : WorkspaceActionUi

    public data object Reload : WorkspaceActionUi

    public data object Validate : WorkspaceActionUi

    public data object Save : WorkspaceActionUi

    public data object OpenSetup : WorkspaceActionUi

    public data object OpenSettings : WorkspaceActionUi
}

/**
 * Four stable truthful target summary columns plus revision and binding header.
 */
@Immutable
public data class TargetSummaryUi(
    val revision: DisplayValue,
    val binding: DisplayValue,
    val deviceId: DisplayValue,
    val androidTarget: DisplayValue,
    val partitionSlot: DisplayValue,
    val buildFlavor: DisplayValue,
)

/**
 * Single row in the fixed 5-row readiness matrix.
 */
@Immutable
public data class ReadinessItemUi(
    val id: ReadinessId,
    val title: String,
    val contextLabel: String,
    val severity: ReadinessSeverity,
    val statusLabel: String,
    val description: String,
    val action: WorkspaceActionUi? = null,
    val reviewRequired: Boolean = false,
    val priority: Int = 100,
)

/**
 * Current or latest build execution state.
 */
@Immutable
public sealed interface BuildStateUi {
    public data object NotStarted : BuildStateUi

    public data class InProgress(val runId: String, val stageLabel: String, val progress: Float? = null) :
        BuildStateUi

    public data class Succeeded(val runId: String, val completedAt: String, val artifactCount: Int) : BuildStateUi

    public data class Failed(
        val runId: String,
        val failedAt: String,
        val stageLabel: String,
        val errorMessage: String,
    ) : BuildStateUi
}

/**
 * Item in recent workspace activity feed.
 */
@Immutable
public data class ActivityItemUi(
    val id: String,
    val title: String,
    val timestamp: String,
    val description: String,
    val action: WorkspaceActionUi? = null,
)

/**
 * Top-level loading state of the overview content.
 */
@Immutable
public sealed interface ContentLoadState {
    public data object Loading : ContentLoadState

    public data object Content : ContentLoadState

    public data object Empty : ContentLoadState

    public data class Error(val message: String) : ContentLoadState
}

/**
 * Complete immutable UI state for the Workspace Overview.
 */
@Immutable
public data class WorkspaceOverviewUiState(
    val workspaceTitle: String = "",
    val subtitle: String = "",
    val targetSummary: TargetSummaryUi = TargetSummaryUi(
        revision = DisplayValue.Unavailable("No revision"),
        binding = DisplayValue.Unavailable("No target bound"),
        deviceId = DisplayValue.Unavailable("No device ID"),
        androidTarget = DisplayValue.Unavailable("No Android target"),
        partitionSlot = DisplayValue.Unavailable("No partition slot"),
        buildFlavor = DisplayValue.Unavailable("No build flavor"),
    ),
    val readinessItems: List<ReadinessItemUi> = emptyList(),
    val reviewCount: Int = 0,
    val nextAction: WorkspaceActionUi? = null,
    val buildState: BuildStateUi = BuildStateUi.NotStarted,
    val recentActivity: List<ActivityItemUi> = emptyList(),
    val loadState: ContentLoadState = ContentLoadState.Content,
) {
    init {
        if (readinessItems.isNotEmpty()) {
            require(readinessItems.size == ReadinessId.entries.size) {
                "Exactly ${ReadinessId.entries.size} readiness items required, found ${readinessItems.size}"
            }
            require(readinessItems.map { it.id } == ReadinessId.entries) {
                "Readiness items must match exact ReadinessId order: ${ReadinessId.entries.map { it.name }}"
            }
            val expectedReviews = readinessItems.count { it.reviewRequired }
            require(reviewCount == expectedReviews) {
                "reviewCount ($reviewCount) must equal reviewRequired count ($expectedReviews)"
            }
        }
    }
}
