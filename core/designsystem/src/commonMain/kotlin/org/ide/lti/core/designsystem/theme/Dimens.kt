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
    val ExtraSmall: Dp = 10.dp
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
    val ToggleMinTouchTarget: Dp = 48.dp
    val ToggleTrackWidthCompact: Dp = 56.dp
    val ToggleTrackHeightCompact: Dp = 32.dp
    val ToggleThumbSizeCompact: Dp = 28.dp
    val ToggleTrackHeightLarge: Dp = 48.dp
    val ToggleThumbSizeLarge: Dp = 40.dp
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
    val DropdownMenuMinWidth: Dp = 240.dp
    val SetupCockpitMaxWidth: Dp = 960.dp
    val SetupMaxContentWidth: Dp = 840.dp
    val MainWindowMinWidth: Dp = 960.dp
    val MainWindowMinHeight: Dp = 640.dp
    val ToggleButtonTopPadding: Dp = 48.dp
    val ToggleButtonEndPadding: Dp = 8.dp
    val ToggleButtonSpacing: Dp = 4.dp
    val DividerWidth: Dp = 8.dp
    val SettingsSidebarWidth: Dp = 210.dp
    val ConfigurationSidebarWidth: Dp = 260.dp
    val ThemeCardMinWidth: Dp = 156.dp
    val ThemePreviewHeight: Dp = 84.dp
    val ActionCardHeight: Dp = 120.dp
    val EmptyStateBadgeSize: Dp = 56.dp
    val StatusDotSize: Dp = 8.dp
    val StepNodeMinWidth: Dp = 96.dp
    val PanelHeaderStackBreakpoint: Dp = 280.dp
    val SettingsMaxWidth: Dp = 760.dp
    val CommandPaletteMinWidth: Dp = 200.dp
    val CommandPaletteMaxWidth: Dp = 320.dp
    val CommandPaletteHeight: Dp = 28.dp
    val PluginSidebarWidth: Dp = 208.dp
    val PluginInspectorWidth: Dp = 380.dp
    val PluginTableRowHeight: Dp = 54.dp
    val PluginStepperNodeSize: Dp = 24.dp
    val PluginAuthorTasksWidth: Dp = 320.dp
    val PluginSearchFieldWidth: Dp = 300.dp
    val PluginStepperConnectorWidth: Dp = 120.dp
    val PluginTableIndexColWidth: Dp = 40.dp
    val PluginTableVersionColWidth: Dp = 80.dp
    val PluginTableSourceColWidth: Dp = 80.dp
    val PluginTableStatusColWidth: Dp = 110.dp
    val PluginTableRefsColWidth: Dp = 80.dp
    val PluginPipelineBoxWidth: Dp = 180.dp
    val PluginDiagnosticsTableColIndex: Dp = 36.dp
    val PluginDiagnosticsTableColSeverity: Dp = 80.dp
    val PluginDiagnosticsTableColFile: Dp = 140.dp
    val PluginDiagnosticsTableColTime: Dp = 84.dp
    val PluginActionBtnHeight: Dp = 40.dp
    val PluginActionBtnCompactHeight: Dp = 34.dp
    val PluginActionBtnCompactWidth: Dp = 104.dp
    val SettingsSearchFieldHeight: Dp = 34.dp
    val SettingsCategoryRowHeight: Dp = 38.dp
    val AboutLogoBoxSize: Dp = 68.dp
    val AboutLinkCardHeight: Dp = 72.dp
    val AppearanceLivePreviewHeight: Dp = 360.dp
}

/**
 * Centralized elevation and shadow radius tokens.
 */
object Elevation {
    val None: Dp = 0.dp
    val Panel: Dp = 8.dp
}

/**
 * IDE shell geometry tokens specified by Feature 005 and the normative Stitch design.
 * (Folded under the Glass* naming scheme alongside GlassShapes/GlassStyle; same values, no field renames -
 * these are single-purpose shell measurements, not duplicates of the generic Spacing/CornerRadius tokens.)
 */
object GlassDimens {
    val TopBarHeight: Dp = 44.dp
    val PipelineRailWidth: Dp = 150.dp
    val CompactRailWidth: Dp = 44.dp
    val WorkspaceNavigatorWidth: Dp = 215.dp
    val CompactNavigatorWidth: Dp = 44.dp
    val BreadcrumbHeight: Dp = 32.dp
    val StatusBarHeight: Dp = 24.dp

    val ContentInset: Dp = 8.dp
    val ContentRadius: Dp = 8.dp

    val CompactBreakpoint: Dp = 1024.dp
    val CompactHeightBreakpoint: Dp = 768.dp
    val ReadableContentMaxWidth: Dp = 1280.dp
    val ReferenceWindowWidth: Dp = 1584.dp
    val ReferenceWindowHeight: Dp = 1280.dp
    val HairlineBorder: Dp = 1.dp
    val CardCornerRadius: Dp = 6.dp
    val ControlCornerRadius: Dp = 4.dp
    val BadgeCornerRadius: Dp = 3.dp

    val PipelineStageItemHeight: Dp = 36.dp
    val NavigatorItemHeight: Dp = 40.dp
    val ReadinessStageColumnWidth: Dp = 100.dp
    val ReadinessLabelColumnWidth: Dp = 140.dp
    val ReadinessStatusColumnWidth: Dp = 110.dp
    val StatusBadgeIconSize: Dp = 10.dp
    val OverviewMaxContentWidth: Dp = 1152.dp
    val OverviewStageIconBoxSize: Dp = 32.dp
    val OverviewEmptyStateHeight: Dp = 136.dp
    val OverviewTargetCardWidth: Dp = 340.dp
    val OverviewDockStripHeight: Dp = 32.dp
    val AcquirePolicyLabelWidth: Dp = 220.dp
    val AcquirePolicyInputWidth: Dp = 140.dp
    val AcquireTableSelectColWidth: Dp = 44.dp
    val AcquireTableActionColWidth: Dp = 90.dp
    val AcquireTableIconColWidth: Dp = 36.dp
    val AcquirePreviewBadgeSize: Dp = 44.dp
    val AcquireStatusBannerIconSize: Dp = 36.dp
    val TableHeaderRowHeight: Dp = 36.dp
    val TableBodyRowHeight: Dp = 40.dp
    val TableSelectionIndicatorSize: Dp = 18.dp
    val DetailsGridLabelColWidth: Dp = 140.dp
}
