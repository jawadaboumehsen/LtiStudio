/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralized spacing tokens (padding, margins, gaps, arrangements).
 */
object Spacing {
    val None: Dp = 0.dp
    val Hairline: Dp = 1.dp
    val ExtraExtraSmall: Dp = 2.dp
    val ExtraSmall: Dp = 4.dp
    val Five: Dp = 5.dp
    val Compact: Dp = 6.dp
    val Small: Dp = 8.dp
    val MediumSmall: Dp = 10.dp
    val SmallMedium: Dp = 12.dp
    val Fourteen: Dp = 14.dp
    val Medium: Dp = 16.dp
    val Large: Dp = 20.dp
    val ExtraLarge: Dp = 24.dp
    val ExtraExtraLarge: Dp = 32.dp

    // Semantic aliases
    val DialogPadding: Dp = 24.dp
    val PanelPadding: Dp = 8.dp
    val TabHorizontal: Dp = 20.dp
    val ScreenHorizontal: Dp = 28.dp
    val ScreenVertical: Dp = 28.dp
    val SectionGap: Dp = 24.dp
    val CardPadding: Dp = 20.dp
}

/**
 * Centralized corner radius tokens for shapes and surfaces.
 */
object CornerRadius {
    val None: Dp = 0.dp
    val ExtraSmall: Dp = 4.dp
    val Compact: Dp = 6.dp
    val Small: Dp = 6.dp
    val MediumSmall: Dp = 8.dp
    val Medium: Dp = 12.dp
    val Large: Dp = 20.dp
    val ExtraLarge: Dp = 32.dp
    val Pill: Dp = 50.dp

    // Semantic aliases
    val Card: Dp = 26.dp
    val Dialog: Dp = 28.dp
    val Panel: Dp = 16.dp
}

/**
 * Centralized icon size tokens across toolbars, buttons, and indicators.
 */
object IconSize {
    val MiniDot: Dp = 5.dp
    val Indicator: Dp = 6.dp
    val Small: Dp = 12.dp
    val WindowControl: Dp = 13.dp
    val Search: Dp = 14.dp
    val Segmented: Dp = 15.dp
    val Medium: Dp = 16.dp
    val SidePanelRail: Dp = 18.dp
    val PanelHeaderAction: Dp = 18.dp
    val Large: Dp = 24.dp
    val ExtraLarge: Dp = 28.dp
    val ActionCard: Dp = 32.dp
    val Avatar: Dp = 80.dp
}

/**
 * Centralized border and stroke width tokens.
 */
object StrokeWidth {
    val Hairline: Dp = 0.5.dp
    val Standard: Dp = 1.dp
    val Focused: Dp = 2.dp
    val Active: Dp = 3.dp
}

/**
 * Centralized component height, width, and layout dimension tokens.
 */
object ComponentSize {
    val TopBarHeight: Dp = 44.dp
    val StatusBarHeight: Dp = 22.dp
    val StatusBarHeightAlt: Dp = 24.dp
    val BottomTabsHeight: Dp = 64.dp
    val BottomTabItemHeight: Dp = 56.dp
    val ButtonMinWidth: Dp = 64.dp
    val ButtonMinHeight: Dp = 40.dp
    val IconButtonSize: Dp = 40.dp
    val TopBarButtonSize: Dp = 28.dp
    val SidePanelRailButtonSize: Dp = 36.dp
    val LogoBadgeSize: Dp = 28.dp
    val SegmentedToggleHeight: Dp = 28.dp
    val SegmentedButtonSize: Dp = 24.dp
    val TopBarDividerHeight: Dp = 18.dp
    val WindowControlWidth: Dp = 36.dp
    val WindowControlHeight: Dp = 28.dp
    val PanelHeaderAction: Dp = 24.dp
    val PanelHeaderActionTouchTarget: Dp = 32.dp
    val ListTileBadgeSize: Dp = 32.dp
    val TabBarHeight: Dp = 36.dp
    val TabItemHeight: Dp = 32.dp
    val OtpBoxSize: Dp = 48.dp
    val ToggleTrackWidth: Dp = 64.dp
    val ToggleTrackHeight: Dp = 28.dp
    val ToggleThumbWidth: Dp = 40.dp
    val ToggleThumbHeight: Dp = 24.dp
    val ToggleDragWidth: Dp = 20.dp
    val SliderTrackHeight: Dp = 6.dp
    val SliderThumbWidth: Dp = 40.dp
    val SliderThumbHeight: Dp = 24.dp
    val DragHandleWidth: Dp = 32.dp
    val DragHandleHeight: Dp = 4.dp
    val ProgressBarHeight: Dp = 4.dp
    val PerformanceMonitorWidth: Dp = 220.dp
    val MinPanelWidth: Dp = 180.dp
    val MaxPanelWidth: Dp = 600.dp
    val MinPanelHeight: Dp = 100.dp
    val MaxPanelHeight: Dp = 600.dp
    val DialogDefaultHeight: Dp = 400.dp
    val MainWindowMinWidth: Dp = 960.dp
    val MainWindowMinHeight: Dp = 640.dp
    val ToggleButtonTopPadding: Dp = 48.dp
    val ToggleButtonEndPadding: Dp = 8.dp
    val ToggleButtonSpacing: Dp = 4.dp
    val DividerWidth: Dp = 8.dp
    val SetupSidebarWidth: Dp = 240.dp
    val SettingsSidebarWidth: Dp = 210.dp
    val ThemeCardMinWidth: Dp = 156.dp
    val ThemePreviewHeight: Dp = 84.dp
    val ActionCardHeight: Dp = 120.dp
    val EmptyStateBadgeSize: Dp = 56.dp
    val SpinnerSize: Dp = 28.dp
    val ShortcutHelpWidth: Dp = 320.dp
    val StatusDotSize: Dp = 8.dp
    val ModalCardMaxWidth: Dp = 440.dp
    val SettingsMaxWidth: Dp = 760.dp
    val SettingsWindowWidth: Dp = 860.dp
    val SettingsWindowHeight: Dp = 720.dp
    val ScrollEdgeFadeHeight: Dp = 24.dp
    val CommandPaletteMinWidth: Dp = 200.dp
    val CommandPaletteMaxWidth: Dp = 320.dp
    val CommandPaletteHeight: Dp = 28.dp
}

/**
 * Centralized elevation and shadow radius tokens.
 */
object Elevation {
    val None: Dp = 0.dp
    val Raised: Dp = 4.dp
    val Panel: Dp = 8.dp
    val Modal: Dp = 16.dp
}
