/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration.Indefinite
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import org.ide.lti.core.data.utils.NetworkMonitor
import org.ide.lti.core.data.utils.TimeZoneMonitor
import org.ide.lti.core.designsystem.component.GlassIconButton
import org.ide.lti.core.designsystem.component.GlassNavigationBar
import org.ide.lti.core.designsystem.component.GlassNavigationBarItem
import org.ide.lti.core.designsystem.component.GlassNavigationRail
import org.ide.lti.core.designsystem.component.GlassNavigationRailItem
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.settings.navigateToNotification
import org.ide.lti.feature.settings.navigateToSettings
import org.ide.lti.shared.generated.resources.Res
import org.ide.lti.shared.generated.resources.not_connected
import org.ide.lti.shared.navigation.LtiNavHost
import org.ide.lti.shared.utils.TopLevelDestination
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun Lti(
    networkMonitor: NetworkMonitor,
    timeZoneMonitor: TimeZoneMonitor,
    modifier: Modifier = Modifier,
) {
    val appState = rememberLtiState(
        networkMonitor = networkMonitor,
        timeZoneMonitor = timeZoneMonitor,
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val destination = appState.currentTopLevelDestination

    val isOffline by appState.isOffline.collectAsStateWithLifecycle()

    // If user is not connected to the internet show a snack bar to inform them.
    val notConnectedMessage = stringResource(Res.string.not_connected)
    LaunchedEffect(isOffline) {
        if (isOffline) {
            snackbarHostState.showSnackbar(
                message = notConnectedMessage,
                duration = Indefinite,
            )
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (appState.shouldShowBottomBar && destination != null) {
                LtiBottomBar(
                    destinations = appState.topLevelDestinations,
                    destinationsWithUnreadResources = emptySet(),
                    onNavigateToDestination = appState::navigateToTopLevelDestination,
                    currentDestination = appState.currentDestination,
                    modifier = Modifier.testTag("NiaBottomBar"),
                )
            }
        },
    ) { padding ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal,
                    ),
                ),
        ) {
            if (appState.shouldShowNavRail && destination != null) {
                LtiNavRail(
                    destinations = appState.topLevelDestinations,
                    destinationsWithUnreadResources = emptySet(),
                    onNavigateToDestination = appState::navigateToTopLevelDestination,
                    currentDestination = appState.currentDestination,
                    modifier = Modifier
                        .testTag("NiaNavRail")
                        .safeDrawingPadding(),
                )
            }

            Column(Modifier.fillMaxSize()) {
                // Show the top app bar on top level destinations.
                if (destination != null) {
                    LtiBar(
                        title = stringResource(destination.titleText),
                        onNavigateToSettings = {
                            appState.navController.navigateToSettings()
                        },
                        onNavigateToEditProfile = {},
                        onNavigateToNotification = {
                            appState.navController.navigateToNotification()
                        },
                        destination = destination,
                    )
                }

                LtiNavHost(
                    appState = appState,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LtiBar(
    title: String,
    onNavigateToSettings: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToNotification: () -> Unit,
    destination: TopLevelDestination?,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = { Text(text = title) },
        actions = {
            Box {
                when (destination) {
                    TopLevelDestination.HOME -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                        ) {
                            GlassIconButton(
                                onClick = onNavigateToNotification,
                            ) {
                                Icon(
                                    imageVector = AppIcons.OutlinedNotifications,
                                    contentDescription = "view notification",
                                    modifier = Modifier.size(IconSize.Medium),
                                )
                            }

                            GlassIconButton(
                                onClick = onNavigateToSettings,
                            ) {
                                Icon(
                                    imageVector = AppIcons.SettingsOutlined,
                                    contentDescription = "view settings",
                                    modifier = Modifier.size(IconSize.Medium),
                                )
                            }
                        }
                    }

                    TopLevelDestination.PROFILE -> {
                        GlassIconButton(
                            onClick = onNavigateToEditProfile,
                        ) {
                            Icon(
                                imageVector = AppIcons.Edit2,
                                contentDescription = "Edit Profile",
                                modifier = Modifier.size(IconSize.Medium),
                            )
                        }
                    }

                    else -> {}
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
        modifier = modifier.testTag("ltiTopAppBar"),
    )
}

@Composable
private fun LtiNavRail(
    destinations: List<TopLevelDestination>,
    destinationsWithUnreadResources: Set<TopLevelDestination>,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier,
) {
    GlassNavigationRail(modifier = modifier) {
        destinations.forEach { destination ->
            val selected = currentDestination.isTopLevelDestinationInHierarchy(destination)
            val hasUnread = destinationsWithUnreadResources.contains(destination)
            GlassNavigationRailItem(
                selected = selected,
                onClick = { onNavigateToDestination(destination) },
                icon = {
                    Icon(
                        imageVector = destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                modifier = if (hasUnread) Modifier.notificationDot() else Modifier,
                selectedIcon = {
                    Icon(
                        imageVector = destination.selectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.iconText)) },
            )
        }
    }
}

@Composable
private fun LtiBottomBar(
    destinations: List<TopLevelDestination>,
    destinationsWithUnreadResources: Set<TopLevelDestination>,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier,
) {
    GlassNavigationBar(
        modifier = modifier,
    ) {
        destinations.forEach { destination ->
            val hasUnread = destinationsWithUnreadResources.contains(destination)
            val selected = currentDestination.isTopLevelDestinationInHierarchy(destination)
            GlassNavigationBarItem(
                selected = selected,
                onClick = { onNavigateToDestination(destination) },
                icon = {
                    Icon(
                        imageVector = destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                modifier = if (hasUnread) Modifier.notificationDot() else Modifier,
                selectedIcon = {
                    Icon(
                        imageVector = destination.selectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.iconText)) },
            )
        }
    }
}

private fun Modifier.notificationDot(): Modifier =
    composed {
        val tertiaryColor = MaterialTheme.colorScheme.tertiary
        drawWithContent {
            drawContent()
            drawCircle(
                tertiaryColor,
                radius = Spacing.Five.toPx(),
                // This is based on the dimensions of the NavigationBar's "indicator pill";
                // however, its parameters are private, so we must depend on them implicitly
                // (NavigationBarTokens.ActiveIndicatorWidth = 64.dp)
                center =
                center +
                    Offset(
                        ComponentSize.ActiveIndicatorWidth.toPx() * .45f,
                        ComponentSize.ActiveIndicatorHeight.toPx() * -.45f - Spacing.Compact.toPx(),
                    ),
            )
        }
    }

private fun NavDestination?.isTopLevelDestinationInHierarchy(destination: TopLevelDestination) =
    this?.hierarchy?.any {
        it.route?.contains(destination.name, true) ?: false
    } ?: false
