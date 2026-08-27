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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kyant.capsule.ContinuousRoundedRectangle
import org.ide.lti.core.designsystem.component.GlassCard
import org.ide.lti.core.designsystem.component.GlassPanel
import org.ide.lti.core.designsystem.component.GlassPanelContainer
import org.ide.lti.core.designsystem.component.GlassPanelHeader
import org.ide.lti.core.designsystem.component.GlassPanelHeaderAction
import org.ide.lti.core.designsystem.component.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.GlassSurface
import org.ide.lti.core.designsystem.component.glassBackground
import org.ide.lti.core.designsystem.component.glassBorder
import org.ide.lti.core.designsystem.component.glassEffect
import org.ide.lti.core.designsystem.component.glassInteractive
import org.ide.lti.core.designsystem.component.glassScrim
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

@Composable
fun SurfacesTab() {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraLarge)
    ) {
        // Section 1: Base Glass Surface
        SectionContainer(
            title = "1. Base Glass Surface (GlassSurface)",
            description = "Thin wrapper over drawBackdrop providing customizable shapes, tints, and border rendering."
        ) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = ContinuousRoundedRectangle(CornerRadius.Medium),
                borderColor = GlassTheme.colors.glassBorderHighlight
            ) {
                Column(modifier = Modifier.padding(Spacing.Medium)) {
                    Text(
                        text = "Standard Glass Surface",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTheme.colors.onGlass
                    )
                    Spacer(Modifier.height(Spacing.ExtraSmall))
                    Text(
                        text = "Renders StandardGlassEffects (vibrancy + blur(4dp) + lens(16dp, 32dp)) over ambient backdrop.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTheme.colors.onGlassSecondary
                    )
                }
            }
        }

        // Section 2: Glass Card Container
        SectionContainer(
            title = "2. Glass Card Container (GlassCard)",
            description = "Rounded container card optimized for content grouping with standard 16dp padding."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                GlassCard(
                    modifier = Modifier.weight(1f),
                    contentPadding = Spacing.Medium
                ) {
                    Column {
                        Text("Workspace Metrics", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
                        Spacer(Modifier.height(Spacing.ExtraSmall))
                        Text("Active modules: 18\nClean builds: 100%\nLint violations: 0", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlassSecondary)
                    }
                }

                GlassCard(
                    modifier = Modifier.weight(1f),
                    contentPadding = Spacing.Medium
                ) {
                    Column {
                        Text("Design Tokens Status", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
                        Spacer(Modifier.height(Spacing.ExtraSmall))
                        Text("Tokens centralized: YES\nWCAG AA compliant: YES\nDetekt enforced: YES", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlassSecondary)
                    }
                }
            }
        }

        // Section 3: Glass Panel & Header
        SectionContainer(
            title = "3. IDE Tool Window Panel (GlassPanel & GlassPanelContainer)",
            description = "Specialized container with header title and actions for tool windows and sidebars."
        ) {
            Box(modifier = Modifier.height(ComponentSize.MinPanelHeight * 2).fillMaxWidth()) {
                GlassPanel(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = Spacing.PanelPadding
                ) {
                    GlassPanelContainer(
                        header = {
                            GlassPanelHeader(
                                title = "EXPLORER",
                                icon = AppIcons.Folder,
                                actions = {
                                    GlassPanelHeaderAction(
                                        icon = AppIcons.SettingsOutlined,
                                        contentDescription = "Panel Settings",
                                        onClick = {}
                                    )
                                }
                            )
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(Spacing.SmallMedium),
                            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
                        ) {
                            Text("📁 core:designsystem", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlass)
                            Text("📁 feature:workspace", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlass)
                            Text("📄 build.gradle.kts", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlassSecondary)
                        }
                    }
                }
            }
        }

        // Section 4: Glass Modifiers Suite
        SectionContainer(
            title = "4. Modifier Extensions Suite (GlassModifiers.kt)",
            description = "Modifier.glassEffect(), Modifier.glassBackground(), Modifier.glassBorder(), Modifier.glassInteractive(), Modifier.glassScrim()."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .glassBackground()
                        .glassBorder(width = StrokeWidth.Standard)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Modifier.glassBackground()\n+ .glassBorder()", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlass)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .glassEffect()
                        .glassInteractive(onClick = {})
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Modifier.glassEffect()\n+ .glassInteractive()", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlass)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .glassScrim(blurRadius = Spacing.Small)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Modifier.glassScrim()\nModal overlay surface", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlass)
                }
            }
        }
    }
}
