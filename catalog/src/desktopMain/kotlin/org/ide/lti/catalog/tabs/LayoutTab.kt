/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.catalog.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.kyant.capsule.ContinuousRoundedRectangle
import org.ide.lti.core.designsystem.component.GlassLayout
import org.ide.lti.core.designsystem.component.GlassPanel
import org.ide.lti.core.designsystem.component.GlassPanelContainer
import org.ide.lti.core.designsystem.component.GlassPanelHeader
import org.ide.lti.core.designsystem.component.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.GlassStatusBar
import org.ide.lti.core.designsystem.component.GlassTextButton
import org.ide.lti.core.designsystem.component.GlassTopBar
import org.ide.lti.core.designsystem.component.LineEnding
import org.ide.lti.core.designsystem.component.rememberLtiLayoutState
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing

@Composable
fun LayoutTab() {
    val scrollState = rememberScrollState()
    val layoutState = rememberLtiLayoutState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraLarge)
    ) {
        SectionContainer(
            title = "1. Interactive 4-Panel IDE Workspace (GlassLayout)",
            description = "4-panel resizable IDE layout with draggable dividers, collapsible sidebars, GlassTopBar, and GlassStatusBar."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassPrimaryButton(onClick = { layoutState.toggleLeft() }) {
                    Text(if (layoutState.showLeft) "Hide Left" else "Show Left")
                }
                GlassPrimaryButton(onClick = { layoutState.toggleRight() }) {
                    Text(if (layoutState.showRight) "Hide Right" else "Show Right")
                }
                GlassPrimaryButton(onClick = { layoutState.toggleBottom() }) {
                    Text(if (layoutState.showBottom) "Hide Bottom" else "Show Bottom")
                }
            }

            Spacer(Modifier.height(Spacing.Medium))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ComponentSize.MaxPanelHeight * 0.8f)
                    .clip(ContinuousRoundedRectangle(CornerRadius.Card))
                    .background(GlassTheme.colors.glassSurface)
            ) {
                GlassLayout(
                    state = layoutState,
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        GlassTopBar(
                            title = "LtiRom Catalog Studio",
                            workspaceName = "DesignSystem",
                            drawOwnChrome = false,
                        )
                    },
                    leftPanel = {
                        GlassPanelContainer(
                            header = {
                                GlassPanelHeader(title = "EXPLORER", icon = AppIcons.Folder)
                            }
                        ) {
                            Column(Modifier.padding(Spacing.SmallMedium)) {
                                Text("📁 theme/", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlass)
                                Text("📁 component/", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlass)
                            }
                        }
                    },
                    mainContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(GlassTheme.colors.glassSurfaceElevated)
                                .padding(Spacing.Medium),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Editor Workspace Area (Center Content)",
                                style = MaterialTheme.typography.titleMedium,
                                color = GlassTheme.colors.onGlass
                            )
                        }
                    },
                    bottomPanel = {
                        GlassPanelContainer(
                            header = {
                                GlassPanelHeader(title = "TERMINAL", icon = AppIcons.Info)
                            }
                        ) {
                            Column(Modifier.padding(Spacing.SmallMedium)) {
                                Text("$ ./gradlew verifyDesignSystemGuardrails", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.glassAccent)
                                Text("BUILD SUCCESSFUL in 2s", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlassSecondary)
                            }
                        }
                    },
                    rightPanel = {
                        GlassPanelContainer(
                            header = {
                                GlassPanelHeader(title = "AI ASSISTANT", icon = AppIcons.Info)
                            }
                        ) {
                            Column(Modifier.padding(Spacing.SmallMedium)) {
                                Text("Ready to assist with design system components.", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlassSecondary)
                            }
                        }
                    },
                    statusBar = {
                        GlassStatusBar(
                            branchName = "feat/catalog-app",
                            cursorPosition = Pair(42, 18),
                            encoding = "UTF-8",
                            lineEnding = LineEnding.LF,
                            drawOwnChrome = false,
                        )
                    }
                )
            }
        }
    }
}
