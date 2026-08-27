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
import androidx.compose.material3.MaterialTheme
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
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.feature.workspace.WORKSPACE_ROUTE
import org.ide.lti.shared.navigation.RootNavGraph
import org.koin.compose.koinInject

@Composable
fun LtiSharedApp(
    modifier: Modifier = Modifier,
    networkMonitor: NetworkMonitor = koinInject(),
    timeZoneMonitor: TimeZoneMonitor = koinInject(),
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
        val colorScheme = MaterialTheme.colorScheme

        Box(modifier = modifier.fillMaxSize()) {
            // Luminous Multi-Point Cosmic Aurora Backdrop for Liquid Glass Refraction.
            // Every color here comes from the dynamically-generated Material scheme
            // (one scheme for dark, one for light - both derived from LtiSeedColor), not
            // from bespoke hex constants, so it stays in sync with the app's single seed color.
            Canvas(
                modifier = Modifier
                    .layerBackdrop(backdrop)
                    .fillMaxSize()
            ) {
                // 1. Deep space / page base
                drawRect(colorScheme.background)

                // 2. Primary nebula (Top-Center)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colorScheme.primary.copy(alpha = AlphaTokens.Strong),
                            colorScheme.primaryContainer.copy(alpha = AlphaTokens.Ambient),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.40f, size.height * 0.15f),
                        radius = size.width * 0.55f
                    )
                )

                // 3. Secondary aurora glow (Top-Right)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colorScheme.secondary.copy(alpha = AlphaTokens.Soft),
                            colorScheme.secondaryContainer.copy(alpha = AlphaTokens.Faded),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.20f),
                        radius = size.width * 0.50f
                    )
                )

                // 4. Tertiary shimmer (Bottom-Left)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colorScheme.tertiary.copy(alpha = AlphaTokens.Ambient),
                            colorScheme.tertiaryContainer.copy(alpha = AlphaTokens.Hover),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.15f, size.height * 0.85f),
                        radius = size.width * 0.50f
                    )
                )

                // 5. Inverse-primary accent (Bottom-Right)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colorScheme.inversePrimary.copy(alpha = AlphaTokens.Faded),
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
                            colorScheme.scrim.copy(alpha = AlphaTokens.Scrim),
                            Color.Transparent,
                            colorScheme.scrim.copy(alpha = AlphaTokens.Half)
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
