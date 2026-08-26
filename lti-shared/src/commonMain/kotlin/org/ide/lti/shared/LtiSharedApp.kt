/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import org.ide.lti.core.data.utils.NetworkMonitor
import org.ide.lti.core.data.utils.TimeZoneMonitor
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.feature.workspace.WORKSPACE_ROUTE
import org.ide.lti.shared.navigation.RootNavGraph
import org.koin.compose.koinInject

@Composable
fun LtiSharedApp(
    networkMonitor: NetworkMonitor = koinInject(),
    timeZoneMonitor: TimeZoneMonitor = koinInject(),
    modifier: Modifier = Modifier,
) {
    SharedApp(
        networkMonitor = networkMonitor,
        timeZoneMonitor = timeZoneMonitor,
        modifier = modifier,
    )
}

@Composable
private fun SharedApp(
    networkMonitor: NetworkMonitor,
    timeZoneMonitor: TimeZoneMonitor,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backdrop = rememberLayerBackdrop()

    LtiTheme(sharedBackdrop = backdrop) {
        Box(modifier = modifier.fillMaxSize()) {
            // Luminous Multi-Point Cosmic Aurora Backdrop for Liquid Glass Refraction
            Canvas(
                modifier = Modifier
                    .layerBackdrop(backdrop)
                    .fillMaxSize()
            ) {
                // 1. Deep space base
                drawRect(Color(0xFF060911))

                // 2. Luminous Electric Cyan / Azure Nebula (Top-Center)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0284C7).copy(alpha = 0.55f),
                            Color(0xFF0369A1).copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.40f, size.height * 0.15f),
                        radius = size.width * 0.55f
                    )
                )

                // 3. Royal Indigo / Purple Aurora Glow (Top-Right)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF6366F1).copy(alpha = 0.45f),
                            Color(0xFF4338CA).copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.20f),
                        radius = size.width * 0.50f
                    )
                )

                // 4. Vibrant Teal / Emerald Shimmer (Bottom-Left)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0D9488).copy(alpha = 0.35f),
                            Color(0xFF042F2E).copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.15f, size.height * 0.85f),
                        radius = size.width * 0.50f
                    )
                )

                // 5. Deep Magenta Accent (Bottom-Right)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF701A75).copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.90f, size.height * 0.90f),
                        radius = size.width * 0.45f
                    )
                )

                // 6. Smooth Vignette Overlay
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF060911).copy(alpha = 0.3f),
                            Color.Transparent,
                            Color(0xFF060911).copy(alpha = 0.5f)
                        )
                    )
                )
            }

            RootNavGraph(
                networkMonitor = networkMonitor,
                timeZoneMonitor = timeZoneMonitor,
                navHostController = navController,
                startDestination = WORKSPACE_ROUTE,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}