/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui.drop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.progress.GlassProgressBar
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.domain.desktop.DropOverlayState

/**
 * Global drag-and-drop overlay and ingest banner for OS file drops (`.zip` OTAs, `payload.bin`,
 * `.tar.md5`, partition `.img` files, mod bundles, and `.apk` packages).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DropIngestOverlay(state: DropOverlayState, onDismissBanner: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        if (state.isDraggingOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = AlphaTokens.Scrim))
                    .padding(Spacing.ExtraLarge)
                    .testTag("DragDropActiveOverlay"),
                contentAlignment = Alignment.Center,
            ) {
                GlassCard(
                    modifier = Modifier
                        .widthIn(max = ComponentSize.StudioDialogMaxWidth)
                        .fillMaxWidth()
                        .clip(GlassShapes.HazePanel)
                        .border(
                            width = StrokeWidth.Focused,
                            color = MaterialTheme.colorScheme.primary,
                            shape = GlassShapes.HazePanel,
                        ),
                    shape = GlassShapes.HazePanel,
                    contentPadding = Spacing.ExtraLarge,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Drop Firmware, Image, or Mod Bundle",
                            style = GlassTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Accepts OTA .zip, A/B payload.bin, Odin .tar.md5, partition .img, " +
                                "mod bundles, and .apk files.",
                            style = GlassTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.hoverFiles.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                            ) {
                                state.hoverFiles.forEach { file ->
                                    GlassChip(
                                        label = "${file.name} (${file.category.displayLabel})",
                                        selected = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val bannerMessage = state.lastError ?: state.lastMessage
        if (state.isIngesting || bannerMessage != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large, vertical = Spacing.ExtraLarge)
                    .testTag("DropIngestFeedbackBanner"),
                horizontalArrangement = Arrangement.Center,
            ) {
                if (state.isIngesting) {
                    GlassCard(
                        modifier = Modifier.widthIn(max = ComponentSize.StudioDialogMaxWidth).fillMaxWidth(),
                        contentPadding = Spacing.SmallMedium,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                            Text(
                                text = "Ingesting dropped artifacts into workspace…",
                                style = GlassTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            GlassProgressBar(progress = null, modifier = Modifier.fillMaxWidth())
                        }
                    }
                } else if (bannerMessage != null) {
                    val hasError = state.lastError != null
                    GlassBanner(
                        severity = if (hasError) GlassBannerSeverity.ERROR else GlassBannerSeverity.SUCCESS,
                        title = if (hasError) "Drop ingest issue" else "Artifact ingested",
                        description = bannerMessage,
                        onDismiss = onDismissBanner,
                        modifier = Modifier.widthIn(max = ComponentSize.StudioDialogMaxWidth),
                    )
                }
            }
        }
    }
}
