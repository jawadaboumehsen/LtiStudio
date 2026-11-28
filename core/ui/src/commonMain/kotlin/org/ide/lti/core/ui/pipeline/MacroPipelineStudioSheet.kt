/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui.pipeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.layout.GlassGlintState
import org.ide.lti.core.designsystem.component.layout.GlassRole
import org.ide.lti.core.designsystem.component.layout.glassAmbientGlint
import org.ide.lti.core.designsystem.component.layout.glassContainer
import org.ide.lti.core.designsystem.component.layout.glassSurface
import org.ide.lti.core.designsystem.theme.AccentPresetColors
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.macro.MacroRecipe
import org.ide.lti.core.domain.pipeline.macro.MacroStep
import org.ide.lti.core.domain.pipeline.macro.MacroStepStatus

/**
 * 1-Click Macro Pipeline Studio Sheet for visual multi-stage ROM build and flash recipes:
 * "Payload.bin -> Inject KernelSU -> Repack EROFS Super -> Patch Vbmeta -> Flash Fastbootd -> Reboot"
 */
@Composable
fun MacroPipelineStudioSheet(
    recipe: MacroRecipe,
    modifier: Modifier = Modifier,
    isRunning: Boolean = false,
    onRunRecipe: () -> Unit = {},
    onCancelRecipe: () -> Unit = {},
    onRetryStep: (String) -> Unit = {},
) {
    val glintState = when {
        isRunning -> GlassGlintState.ActiveBuild()
        recipe.hasFailed -> GlassGlintState.Error("Pipeline step failed")
        recipe.isComplete && recipe.passedSteps == recipe.totalSteps -> GlassGlintState.DeviceConnected("Build Success")
        else -> GlassGlintState.Idle
    }

    Column(
        modifier = modifier
            .testTag("MacroPipelineStudioSheet")
            .glassAmbientGlint(glintState = glintState, shape = GlassShapes.HazeMedium)
            .glassSurface(role = GlassRole.TextCard, shape = GlassShapes.HazeMedium, drawBorder = true)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        // Recipe Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = ideFontFamily(),
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = recipe.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = ideFontFamily(),
                        fontSize = FontSize.Micro,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }

            Text(
                text = "${recipe.passedSteps}/${recipe.totalSteps} steps",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = codeFontFamily(),
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                ),
            )
        }

        // Virtualized Steps List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = ComponentSize.DialogDefaultHeight)
                .glassContainer(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            items(recipe.steps, key = { it.id }) { step ->
                MacroStepRow(step = step, onRetry = { onRetryStep(step.id) })
            }
        }

        // Action Footer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isRunning) {
                Box(
                    modifier = Modifier
                        .clip(GlassShapes.Small)
                        .background(AccentPresetColors.Crimson.copy(alpha = AlphaTokens.Hover))
                        .clickable(onClick = onCancelRecipe)
                        .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = ideFontFamily(),
                            color = AccentPresetColors.Crimson,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .testTag("RunRecipeButton")
                        .clip(GlassShapes.Small)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Prominent))
                        .clickable(onClick = onRunRecipe)
                        .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                ) {
                    Text(
                        text = "Run Recipe",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = ideFontFamily(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroStepRow(step: MacroStep, onRetry: () -> Unit) {
    val (statusColor, statusText) = when (step.status) {
        MacroStepStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant to "PENDING"
        MacroStepStatus.RUNNING -> AccentPresetColors.Cyan to "RUNNING"
        MacroStepStatus.PASSED -> AccentPresetColors.Emerald to "PASSED"
        MacroStepStatus.FAILED -> AccentPresetColors.Crimson to "FAILED"
        MacroStepStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant to "SKIPPED"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.Small)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Half))
            .padding(Spacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = codeFontFamily(),
                        fontSize = FontSize.Micro,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier
                        .clip(GlassShapes.ExtraSmall)
                        .background(statusColor.copy(alpha = AlphaTokens.Hover))
                        .padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.ExtraExtraSmall),
                )
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = ideFontFamily(),
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }

            if (step.durationMs > 0) {
                Text(
                    text = "${step.durationMs}ms",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = codeFontFamily(),
                        fontSize = FontSize.Micro,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.ExtraExtraSmall))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = step.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = ideFontFamily(),
                    fontSize = FontSize.Micro,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )

            if (step.status == MacroStepStatus.FAILED) {
                Box(
                    modifier = Modifier
                        .clip(GlassShapes.ExtraSmall)
                        .background(AccentPresetColors.Amber.copy(alpha = AlphaTokens.Hover))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.ExtraExtraSmall),
                ) {
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = ideFontFamily(),
                            fontSize = FontSize.Micro,
                            fontWeight = FontWeight.Bold,
                            color = AccentPresetColors.Amber,
                        ),
                    )
                }
            }
        }

        if (step.errorMessage != null) {
            Text(
                text = "Error: ${step.errorMessage}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = codeFontFamily(),
                    fontSize = FontSize.Micro,
                    color = AccentPresetColors.Crimson,
                ),
                modifier = Modifier.padding(top = Spacing.ExtraExtraSmall),
            )
        }
    }
}
