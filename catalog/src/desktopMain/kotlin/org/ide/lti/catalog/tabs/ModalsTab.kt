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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ide.lti.core.designsystem.component.feedback.GlassBottomSheetScaffold
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.component.feedback.GlassLoadingDialog
import org.ide.lti.core.designsystem.component.devtools.GlassPerformanceCollector
import org.ide.lti.core.designsystem.component.devtools.GlassPerformanceMonitor
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassTextButton
import org.ide.lti.core.designsystem.component.feedback.LoadingDialogState
import org.ide.lti.core.designsystem.component.devtools.MonitorPosition
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing

@Composable
fun ModalsTab() {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var showStandardDialog by remember { mutableStateOf(false) }
    var loadingState by remember { mutableStateOf<LoadingDialogState>(LoadingDialogState.Hidden) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showPerformanceMonitor by remember { mutableStateOf(true) }

    val collector = remember {
        GlassPerformanceCollector().apply {
            // Seed realistic sample frames
            recordFrame(12)
            recordFrame(14)
            recordFrame(13)
            recordFrame(15)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraLarge)
        ) {
            // Section 1: Glass Modal Dialog
            SectionContainer(
                title = "1. Frosted Modal Dialog (GlassDialog)",
                description = "Modal dialog surface with heavy frosted blur, depth lens refraction, and theme adaptation."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassPrimaryButton(onClick = { showStandardDialog = true }) {
                        Text("Open Confirmation Dialog")
                    }

                    if (showStandardDialog) {
                        GlassDialog(
                            onDismissRequest = { showStandardDialog = false },
                            title = "Workspace Action Confirmation",
                            confirmButton = {
                                GlassPrimaryButton(onClick = { showStandardDialog = false }) {
                                    Text("Confirm")
                                }
                            },
                            dismissButton = {
                                GlassTextButton(onClick = { showStandardDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        ) {
                            Text(
                                text = "Are you sure you want to clean and rebuild all design-system tokens? This action will recompile all Skia shaders.",
                                color = GlassTheme.colors.onGlass
                            )
                        }
                    }
                }
            }

            // Section 2: Glass Loading Dialog
            SectionContainer(
                title = "2. Modal Progress Indicator (GlassLoadingDialog)",
                description = "Centered modal spinner with circular progress and backdrop blur."
            ) {
                GlassPrimaryButton(
                    onClick = {
                        loadingState = LoadingDialogState.Shown
                        scope.launch {
                            delay(2000)
                            loadingState = LoadingDialogState.Hidden
                        }
                    }
                ) {
                    Text("Trigger 2s Loading Dialog")
                }

                GlassLoadingDialog(visibilityState = loadingState)
            }

            // Section 3: Glass Bottom Sheet
            SectionContainer(
                title = "3. Modal Bottom Sheet (GlassBottomSheet & Scaffold)",
                description = "Drag-to-dismiss modal bottom sheet with spring physics and heavy frosted glass."
            ) {
                GlassPrimaryButton(onClick = { showBottomSheet = true }) {
                    Text("Open Bottom Sheet Scaffold")
                }

                GlassBottomSheetScaffold(
                    visible = showBottomSheet,
                    onDismissRequest = { showBottomSheet = false },
                    title = "Workspace Inspector"
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Small)
                    ) {
                        Text("Active Theme: ${if (GlassTheme.colors == org.ide.lti.core.designsystem.theme.DarkGlassColorScheme) "Dark" else "Light"}", color = GlassTheme.colors.onGlass)
                        Text("Reduced Motion: ${GlassTheme.reducedMotion}", color = GlassTheme.colors.onGlass)
                        Text("GPU Glass Effects: ${GlassTheme.effectsEnabled}", color = GlassTheme.colors.onGlass)
                        Spacer(Modifier.height(Spacing.Medium))
                        GlassPrimaryButton(onClick = { showBottomSheet = false }) {
                            Text("Close Inspector")
                        }
                    }
                }
            }

            // Section 4: Performance Monitor Developer Tool
            SectionContainer(
                title = "4. Real-Time Performance Monitor (GlassPerformanceMonitor)",
                description = "Developer overlay displaying live framerate, frame render time, shader cache, and memory metrics."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Performance Overlay: ${if (showPerformanceMonitor) "ACTIVE (Top-End)" else "HIDDEN"}",
                        color = GlassTheme.colors.onGlass
                    )

                    GlassPrimaryButton(onClick = { showPerformanceMonitor = !showPerformanceMonitor }) {
                        Text(if (showPerformanceMonitor) "Hide Monitor" else "Show Monitor")
                    }
                }
            }
        }

        // Live Real-Time Performance Monitor Overlay
        GlassPerformanceMonitor(
            visible = showPerformanceMonitor,
            position = MonitorPosition.TopEnd,
            collector = collector
        )
    }
}
