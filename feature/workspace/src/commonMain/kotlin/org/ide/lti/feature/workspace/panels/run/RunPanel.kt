package org.ide.lti.feature.workspace.panels.run

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.progress.GlassConsoleDrawer
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.ArtifactKind
import org.ide.lti.core.model.run.VerificationState

@Composable
public fun RunPanel(
    viewModel: BuildRunViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val glassColors = GlassTheme.colors
    val typography = GlassTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        // Top action bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ROM Build Pipeline",
                    style = typography.titleMedium,
                    color = glassColors.onGlass
                )
                Text(
                    text = if (uiState.currentWorkspace != null) {
                        "Workspace: ${uiState.currentWorkspace?.name}"
                    } else {
                        "No workspace opened"
                    },
                    style = typography.bodySmall,
                    color = glassColors.onGlassSecondary
                )
            }

            GlassButton(
                onClick = { viewModel.startBuild() },
                enabled = !uiState.isRunDisabled,
                variant = GlassButtonVariant.Primary
            ) {
                Text(
                    text = if (uiState.isRunning) "Building..." else "Start Pipeline",
                    style = typography.labelMedium
                )
            }
        }

        // Error message banner
        if (uiState.errorMessage != null) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.Small),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        style = typography.bodySmall,
                        color = glassColors.diagnostics.error
                    )
                    GlassChip(
                        label = "Dismiss",
                        onClick = { viewModel.clearError() }
                    )
                }
            }
        }

        // Stepper
        StagePipelineView(
            latestRun = uiState.latestRun,
            isBusy = uiState.isRunning,
            modifier = Modifier.fillMaxWidth()
        )

        GlassHorizontalDivider()

        // Artifacts section
        Text(
            text = "Verified Artifacts",
            style = typography.titleSmall,
            color = glassColors.onGlass
        )

        val artifacts = uiState.latestRun?.artifacts ?: emptyList()
        if (artifacts.isEmpty()) {
            Text(
                text = if (uiState.isRunning) "Artifacts will appear after build completes." else "No build artifacts produced yet.",
                style = typography.bodySmall,
                color = glassColors.onGlassSecondary
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                items(artifacts) { artifact ->
                    ArtifactItem(artifact = artifact)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Console log drawer
        GlassConsoleDrawer(
            logs = uiState.logs,
            isRunning = uiState.isRunning,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ArtifactItem(artifact: Artifact) {
    val glassColors = GlassTheme.colors
    val typography = GlassTheme.typography

    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = artifact.linuxPath.substringAfterLast("/"),
                    style = typography.labelMedium,
                    color = glassColors.onGlass
                )
                GlassChip(
                    label = when (artifact.kind) {
                        ArtifactKind.FLASHABLE_ZIP -> "ZIP"
                        ArtifactKind.OTA_MANIFEST -> "MANIFEST"
                        ArtifactKind.CHECKSUM -> "SHA256"
                        ArtifactKind.IMAGE -> "IMG"
                    }
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val size = artifact.sizeBytes
                if (size != null) {
                    val sizeMb = size / (1024 * 1024)
                    GlassChip(label = "${sizeMb} MB")
                }
                val sha = artifact.sha256
                if (sha != null) {
                    GlassChip(label = "SHA: ${sha.take(8)}...")
                }
                GlassChip(
                    label = when (artifact.verification) {
                        VerificationState.VERIFIED -> "VERIFIED"
                        VerificationState.FAILED -> "INVALID"
                        VerificationState.NOT_VERIFIED -> "UNVERIFIED"
                    },
                    selected = artifact.verification == VerificationState.VERIFIED
                )
            }
        }
    }
}
