/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mobile-wallet/blob/master/LICENSE.md
 */

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.rememberWindowState
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.LocalNucleusWindow
import dev.nucleusframework.application.nucleusApplication
import org.ide.lti.core.data.settings.AppSettingsState
import org.ide.lti.core.designsystem.component.GlassSecondaryTopBar
import org.ide.lti.core.designsystem.component.LocalWindowControlActions
import org.ide.lti.core.designsystem.component.WindowControlActions
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.settings.SettingsScreen
import org.ide.lti.shared.LtiSharedApp
import org.ide.lti.shared.di.initKoin
import org.koin.compose.koinInject

fun main(args: Array<String>) = nucleusApplication(args) {
    remember { initKoin() }
    var showSettingsWindow by remember { mutableStateOf(false) }
    val windowState = rememberWindowState()
    val settingsWindowState = rememberWindowState(
        width = ComponentSize.SettingsWindowWidth,
        height = ComponentSize.SettingsWindowHeight,
    )

    DecoratedWindow(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "LtiRom Studio",
        icon = painterResource("icons/ic_launcher.png"),
    ) {
        val nucleusWin = LocalNucleusWindow.current
        CompositionLocalProvider(
            LocalWindowControlActions provides WindowControlActions(
                onMinimize = {
                    windowState.isMinimized = true
                    try { nucleusWin.setMinimized(true) } catch (_: Throwable) {}
                },
                onMaximize = {
                    windowState.placement = if (windowState.placement == androidx.compose.ui.window.WindowPlacement.Maximized) {
                        androidx.compose.ui.window.WindowPlacement.Floating
                    } else {
                        androidx.compose.ui.window.WindowPlacement.Maximized
                    }
                    try { nucleusWin.setMaximized(!nucleusWin.isMaximized) } catch (_: Throwable) {}
                },
                onClose = {
                    exitApplication()
                },
                onDragWindow = {
                    try { nucleusWin.unsafe.taoWindow?.dragWindow() } catch (_: Throwable) {}
                },
            )
        ) {
            LtiSharedApp(
                onOpenSettings = { showSettingsWindow = true },
            )
        }
    }

    DecoratedWindow(
        onCloseRequest = { showSettingsWindow = false },
        visible = showSettingsWindow,
        state = settingsWindowState,
        title = "Settings",
        icon = painterResource("icons/ic_launcher.png"),
        resizable = true,
    ) {
        val settingsWin = LocalNucleusWindow.current
        val appSettingsState: AppSettingsState = koinInject()

        CompositionLocalProvider(
            LocalWindowControlActions provides WindowControlActions(
                onMinimize = {
                    settingsWindowState.isMinimized = true
                    try { settingsWin.setMinimized(true) } catch (_: Throwable) {}
                },
                onMaximize = {
                    settingsWindowState.placement = if (settingsWindowState.placement == androidx.compose.ui.window.WindowPlacement.Maximized) {
                        androidx.compose.ui.window.WindowPlacement.Floating
                    } else {
                        androidx.compose.ui.window.WindowPlacement.Maximized
                    }
                    try { settingsWin.setMaximized(!settingsWin.isMaximized) } catch (_: Throwable) {}
                },
                onClose = {
                    showSettingsWindow = false
                },
                onDragWindow = {
                    try { settingsWin.unsafe.taoWindow?.dragWindow() } catch (_: Throwable) {}
                },
            )
        ) {
            val backdrop = rememberLayerBackdrop()

            LtiTheme(
                darkTheme = appSettingsState.isDarkTheme,
                effectsEnabled = appSettingsState.effectsEnabled,
                reducedMotion = appSettingsState.reducedMotion,
                sharedBackdrop = backdrop,
            ) {
                val colorScheme = MaterialTheme.colorScheme

                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(
                        modifier = Modifier
                            .layerBackdrop(backdrop)
                            .fillMaxSize()
                    ) {
                        drawRect(colorScheme.background)

                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colorScheme.primary.copy(alpha = AlphaTokens.Soft),
                                    colorScheme.primaryContainer.copy(alpha = AlphaTokens.Faded),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.40f, size.height * 0.15f),
                                radius = size.width * 0.55f
                            )
                        )

                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colorScheme.secondary.copy(alpha = AlphaTokens.Ambient),
                                    colorScheme.secondaryContainer.copy(alpha = AlphaTokens.Hover),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.85f, size.height * 0.20f),
                                radius = size.width * 0.50f
                            )
                        )

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

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.ExtraSmall),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = Spacing.PanelPadding,
                                    end = Spacing.PanelPadding,
                                    top = Spacing.PanelPadding,
                                    bottom = Spacing.ExtraSmall
                                )
                        ) {
                            GlassSecondaryTopBar(
                                title = "Settings",
                                icon = AppIcons.SettingsOutlined,
                                backdrop = backdrop,
                            )
                        }

                        SettingsScreen(
                            onBackClick = { showSettingsWindow = false },
                            modifier = Modifier.weight(1f),
                            appSettingsState = appSettingsState,
                        )
                    }
                }
            }
        }
    }
}
