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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.LocalNucleusWindow
import dev.nucleusframework.application.NucleusWindow
import dev.nucleusframework.application.nucleusApplication
import org.ide.lti.core.data.settings.AppSettingsState
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.component.layout.GlassTopBar
import org.ide.lti.core.designsystem.component.layout.TopBarNavigation
import org.ide.lti.core.designsystem.component.actions.LocalWindowControlActions
import org.ide.lti.core.designsystem.component.actions.WindowControlActions
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.config.AppTheme
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.settings.SettingsScreen
import org.ide.lti.shared.LtiSharedApp
import org.ide.lti.shared.di.initKoin
import org.koin.compose.koinInject

private fun buildWindowControlActions(
    windowState: WindowState,
    nucleusWin: NucleusWindow,
    onClose: () -> Unit,
): WindowControlActions = WindowControlActions(
    onMinimize = {
        windowState.isMinimized = true
        try { nucleusWin.setMinimized(true) } catch (_: Throwable) {}
    },
    onMaximize = {
        windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
            WindowPlacement.Floating
        } else {
            WindowPlacement.Maximized
        }
        try { nucleusWin.setMaximized(!nucleusWin.isMaximized) } catch (_: Throwable) {}
    },
    onClose = onClose,
    onDragWindow = {
        try { nucleusWin.unsafe.taoWindow?.dragWindow() } catch (_: Throwable) {}
    },
    isMaximized = {
        windowState.placement == WindowPlacement.Maximized ||
            try { nucleusWin.isMaximized } catch (_: Throwable) { false }
    },
)

fun main(args: Array<String>) = nucleusApplication(args) {
    remember { initKoin() }
    var showSettingsWindow by remember { mutableStateOf(false) }
    val windowState = rememberWindowState(
        width = ComponentSize.MaxPanelWidth * 2,
        height = ComponentSize.MaxPanelHeight * 1.4f,
    )
    val settingsWindowState = rememberWindowState(
        width = ComponentSize.SettingsWindowWidth,
        height = ComponentSize.SettingsWindowHeight,
    )

    DecoratedWindow(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "LtiRom Studio",
        icon = painterResource("icons/ic_launcher.png"),
        minimumSize = DpSize(ComponentSize.MainWindowMinWidth, ComponentSize.MainWindowMinHeight),
    ) {
        val nucleusWin = LocalNucleusWindow.current
        CompositionLocalProvider(
            LocalWindowControlActions provides buildWindowControlActions(
                windowState = windowState,
                nucleusWin = nucleusWin,
                onClose = ::exitApplication,
            ),
        ) {
            LtiSharedApp(
                onOpenSettings = { showSettingsWindow = true },
            )
        }
    }

    if (showSettingsWindow) {
        DecoratedWindow(
            onCloseRequest = { showSettingsWindow = false },
            state = settingsWindowState,
            title = "Settings",
            icon = painterResource("icons/ic_launcher.png"),
            resizable = true,
        ) {
            val settingsWin = LocalNucleusWindow.current
            val appSettingsState: AppSettingsState = koinInject()

            CompositionLocalProvider(
                LocalWindowControlActions provides buildWindowControlActions(
                    windowState = settingsWindowState,
                    nucleusWin = settingsWin,
                    onClose = { showSettingsWindow = false },
                ),
            ) {
                val backdrop = rememberLayerBackdrop()

                LtiTheme(
                    appTheme = AppTheme.fromId(appSettingsState.theme),
                    effectsEnabled = appSettingsState.effectsEnabled,
                    reducedMotion = appSettingsState.reducedMotion,
                    sharedBackdrop = backdrop,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        GlassBackdrop(backdrop = backdrop)

                        Column(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            GlassTopBar(
                                navigation = TopBarNavigation.Back(
                                    onClick = { showSettingsWindow = false },
                                ),
                                search = null,
                                panelToggles = null,
                                windowControls = WindowControlActions(
                                    onClose = { showSettingsWindow = false },
                                ),
                                backdrop = backdrop,
                                drawOwnChrome = true,
                            )

                            SettingsScreen(
                                onBackClick = { showSettingsWindow = false },
                                modifier = Modifier.weight(1f),
                                showHeader = false,
                                appSettingsState = appSettingsState,
                            )
                        }
                    }
                }
            }
        }
    }
}
