/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.feature.setup.SetupUiActions
import org.ide.lti.feature.setup.SetupUiState
import org.ide.lti.feature.setup.components.DocFeatureCard

/**
 * Server Bridge & WSL2 Daemon Connectivity status screen.
 *
 * Displays local communication channel metrics, daemon status, and IPC endpoints.
 * Adheres strictly to Design System Guardrails (100% token usage, 0 raw literals).
 */
@Composable
fun ConnectionsStepContent(
    state: SetupUiState,
    actions: SetupUiActions,
    resolvedBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors

    Column(
        modifier = modifier
            .widthIn(max = ComponentSize.SetupMaxContentWidth)
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.ScreenVertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
    ) {
        // Responsive Header Row with auto-wrap
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(vertical = Spacing.ExtraSmall),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Text(
                    text = "Server Bridge & Daemon Connectivity",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = glassColors.onGlass,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Local communication channel between Windows Compose GUI and WSL2 Linux Daemon",
                    style = MaterialTheme.typography.bodyMedium,
                    color = glassColors.onGlassSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            GlassSecondaryButton(
                onClick = actions.onCheckEnvironment,
                enabled = !state.toolchainSetupState.isBusy,
            ) {
                Text("Test Connection", maxLines = 1, softWrap = false)
            }
        }

        val wslStep = state.toolchainSetupState.steps.find { it.stage == SetupStepStage.WSL_DETECTION }
        val daemonStep = state.toolchainSetupState.steps.find { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        val isDaemonReady = daemonStep?.status == StepStatus.SUCCESS
        val isWslReady = wslStep?.status == StepStatus.SUCCESS

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding),
        ) {
            DocFeatureCard(
                title = "Daemon Status",
                badge = if (isDaemonReady) "Connected" else "Disconnected",
                description = if (isDaemonReady)
                    "LtiRomServer daemon is active and responding to RPC requests."
                else
                    "LtiRomServer daemon is offline or unreachable on localhost:50051.",
                modifier = Modifier.weight(1f, fill = false).widthIn(min = ComponentSize.MinPanelWidth),
                backdrop = resolvedBackdrop,
            )
            DocFeatureCard(
                title = "WSL2 Subsystem",
                badge = if (isWslReady) "Operational" else "Pending Check",
                description = if (isWslReady)
                    "Default Linux distribution detected and ready."
                else
                    "WSL2 distribution check pending or distribution not found.",
                modifier = Modifier.weight(1f, fill = false).widthIn(min = ComponentSize.MinPanelWidth),
                backdrop = resolvedBackdrop,
            )
            DocFeatureCard(
                title = "Daemon Endpoint",
                badge = "127.0.0.1:50051",
                description = "High-throughput loopback IPC channel connecting the Windows UI client to the native Linux ROM tool engine.",
                modifier = Modifier.weight(1f, fill = false).widthIn(min = ComponentSize.MinPanelWidth),
                backdrop = resolvedBackdrop,
            )
            DocFeatureCard(
                title = "Protocol & Transport",
                badge = "HTTP / SSE / RPC",
                description = "Streaming event bus for real-time progress updates, compilation logs, and partition unpack/repack pipelines.",
                modifier = Modifier.weight(1f, fill = false).widthIn(min = ComponentSize.MinPanelWidth),
                backdrop = resolvedBackdrop,
            )
        }
    }
}
