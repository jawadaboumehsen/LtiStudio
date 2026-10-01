- to memorize donk breake the project arch and moduarization, and system desgin

## Architecture: core:designsystem vs core:ui

Add to `core:designsystem` when:
- Defining theme tokens (colors, alpha, typography, dimensions, motion)
- Creating branded variants of Material 3 components
- Adding app-wide icons (`AppIcons.kt`)
- Building stateless, reusable UI primitives and glass layout scaffolding
- Something every feature might need

Examples:
- Glass color scheme and design tokens (`Color.kt`, `Alpha.kt`, `Dimens.kt`, `Motion.kt`, `Type.kt`, `GlassMaterialStyles.kt`)
- Base glass surfaces and containers (`GlassSurface.kt`, `GlassCard.kt`, `GlassPanel.kt`)
- Interactive glass controls (`GlassButton.kt`, `GlassTextField.kt`, `GlassSlider.kt`, `GlassToggle.kt`)
- Layout scaffolding (`GlassLayout.kt`, `GlassTopBar.kt`, `GlassStatusBar.kt`, `GlassBottomSheet.kt`)
- Progress & timeline visualization (`GlassStepper.kt`, `GlassProgressBar.kt`)
- Developer tools (`GlassPerformanceMonitor.kt`)

Add to `core:ui` when:
- Building complex widgets that combine multiple components
- Creating stateful composables with ViewModels
- Adding utility functions for navigation, effects, lifecycle
- Platform-specific UI utilities (share, file pickers)

Examples:
- Code editor and file tree components (`GlassCodeEditor.kt`, `GlassFileTree.kt`)
- Chat input and message bubbles (`GlassChatInput.kt`, `GlassMessageBubble.kt`)
- Scrollable tab rows and small chips (`GlassScrollableTabRow.kt`, `GlassSmallChip.kt`)

## Design System Architecture & Centralized Tokens

> [!IMPORTANT]
> The design system is mid-consolidation to one source of truth per concern (color, layers,
> effects, components) — see `specs/006-design-system-consolidation/`. The token/component names
> documented below are the pre-consolidation state and are being replaced; check that spec before
> treating this section as current. For recomposition/allocation performance rules (not
> correctness), see `rules/jetpackcomposerule.md`. If you're adding a `GlassStyles` role,
> migrating a component off `GlassEffects.*` onto `Modifier.glass`, or touching the real
> production shell chrome (`IdeTopAppBar`/`IdeStatusBar`/`IdePipelineRail`/`IdeNavigatorPanel`/
> `Modifier.ideCardSurface`), read `rules/glass-context.md` first.

LtiRom Studio uses a professional IDE design built around a centralized token system in `core/designsystem/src/commonMain/kotlin/org/ide/lti/core/designsystem/theme/`.

### 1. Color System (`Color.kt`)

All colors are anchored to a brand seed color and dynamic Material 3 color generation:
- `LtiSeedColor` = `#38BDF8` (Light Sky Blue, matches `BrandColors.LogoGradientEnd`)
- `DarkGlassColorScheme` / `LightGlassColorScheme`: dynamically generated via `dynamicGlassColorScheme()` and `com.materialkolor.dynamicColorScheme()`.

**`GlassColorScheme` Tokens:**
```kotlin
@Immutable
data class GlassColorScheme(
    val glassSurface: Color,
    val glassSurfaceElevated: Color,
    val canvasSurface: Color,
    val glassTint: Color,
    val glassTintSecondary: Color,
    val glassBorder: Color,
    val glassBorderHighlight: Color,
    val onGlass: Color,
    val onGlassSecondary: Color,
    val onGlassMuted: Color,
    val glassHighlight: Color,
    val glassShadow: Color,
    val glassAccent: Color,
    val focusRing: Color,
    val syntax: SyntaxColorScheme,
)
```

**`SyntaxColorScheme` Tokens:**
```kotlin
@Immutable
data class SyntaxColorScheme(
    val keyword: Color,
    val function: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val type: Color,
    val annotation: Color,
    val plain: Color,
)
```

**Key Color Tokens & Helpers:**
- `syntax`: Dynamic theme-aware syntax palette derived via `com.materialkolor` HCT color science with verified WCAG AA contrast against `canvasSurface`.
- `canvasSurface`: Dedicated flat, fully-opaque background token for the editor/terminal content area, distinct from translucent glass chrome.
- `focusRing`: Dedicated WCAG focus ring token derived from `colorScheme.primary` at full (1.0f) opacity.
- `onGlass` / `onGlassSecondary` / `onGlassMuted`: Text and icon colors guaranteeing WCAG contrast against `glassSurface`.
- Specialized token objects:
  - `WindowControlColors.CloseHover`: Native window close button hover color (`#E81123`).
  - `BrandColors.LogoGradientEnd`: Brand logo sky-blue gradient stop (`#38BDF8`).
  - `PerformanceMonitorColors`: `Good` (`#4EC9B0`), `Warning` (`#D7BA7D`), `Degraded` (`#CE9178`), `Bad` (`#F48771`).
  - `ToggleColors`: `ActiveLight` (`#34C759`), `ActiveDark` (`#30D158`).
  - `PreviewSampleColors`: `Blue`, `Purple`, `Orange`, `Green`.
  - `GlassLayoutColors.PanelShadow`: Panel shadow color token (`#26000000`).

### 2. Alpha & Opacity Tokens (`Alpha.kt`)

All opacity values are centralized in `AlphaTokens` (no raw float literals in UI code):
- `AlphaTokens.UltraFaint` (0.03f), `Faint` (0.05f), `Subtle` (0.10f), `Glow` (0.15f), `Hover` (0.20f)
- `AlphaTokens.Faded` (0.25f), `Muted` / `Scrim` (0.30f), `Ambient` (0.35f), `Medium` / `Disabled` (0.40f)
- `AlphaTokens.Soft` (0.45f), `Half` / `Border` / `Elevated` (0.50f), `Strong` (0.55f), `Prominent` (0.60f), `IconTint` (0.75f)

### 3. Dimension Tokens (`Dimens.kt`)

All layout metrics are centralized in `Dimens.kt`:
- `Spacing`: `None` (0dp), `Hairline` (1dp), `ExtraExtraSmall` (2dp), `ExtraSmall` (4dp), `Five` (5dp), `Compact` (6dp), `Small` (8dp), `MediumSmall` (10dp), `SmallMedium` (12dp), `Fourteen` (14dp), `Medium` (16dp), `Large` (20dp), `ExtraLarge` (24dp), `ExtraExtraLarge` (32dp), `DialogPadding` (24dp), `PanelPadding` (8dp), `TabHorizontal` (20dp).
- `CornerRadius`: `None` (0dp), `ExtraSmall` (4dp), `Compact` (6dp), `Small` (6dp), `MediumSmall` (8dp), `Medium` (12dp), `ExtraLarge` (28dp), `Pill` (50dp), `Card` (16dp), `Dialog` (16dp), `Panel` (12dp).
- `ComponentSize`: `TopBarHeight` (44dp), `StatusBarHeight` (22dp), `StatusBarHeightAlt` (24dp), `BottomTabsHeight` (64dp), `BottomTabItemHeight` (56dp), `ButtonMinWidth` (64dp), `ButtonMinHeight` (40dp), `IconButtonSize` (40dp), `TopBarButtonSize` (28dp), `SidePanelRailButtonSize` (36dp), `LogoBadgeSize` (28dp), `SearchCapsuleHeight` (28dp), `SearchCapsuleMinWidth` (160dp), `SearchCapsuleMaxWidth` (280dp), `SegmentedToggleHeight` (28dp), `SegmentedButtonSize` (24dp), `TopBarDividerHeight` (18dp), `WindowControlWidth` (36dp), `WindowControlHeight` (28dp), `PanelHeaderAction` (24dp), `PanelHeaderActionTouchTarget` (32dp), `TabBarHeight` (36dp), `TabItemHeight` (32dp), `OtpBoxSize` (48dp), `ToggleTrackWidth` (64dp), `ToggleTrackHeight` (28dp), `ToggleThumbWidth` (40dp), `ToggleThumbHeight` (24dp), `ToggleDragWidth` (20dp), `SliderTrackHeight` (6dp), `SliderThumbWidth` (40dp), `SliderThumbHeight` (24dp), `DragHandleWidth` (32dp), `DragHandleHeight` (4dp), `ProgressBarHeight` (4dp), `PerformanceMonitorWidth` (220dp), `MinPanelWidth` (180dp), `MaxPanelWidth` (600dp), `MinPanelHeight` (100dp), `MaxPanelHeight` (600dp), `ToggleButtonTopPadding` (48dp), `ToggleButtonEndPadding` (8dp), `ToggleButtonSpacing` (4dp), `DividerWidth` (8dp), `SetupSidebarWidth` (240dp), `ActionCardHeight` (120dp), `EmptyStateBadgeSize` (56dp), `SpinnerSize` (28dp), `ShortcutHelpWidth` (320dp), `StatusDotSize` (8dp), `ScrollEdgeFadeHeight` (24dp).
- `StrokeWidth`: `Hairline` (0.5dp), `Standard` (1dp), `Focused` (2dp), `Active` (3dp).
- `IconSize`: `Indicator` (6dp), `Small` (12dp), `WindowControl` (13dp), `Search` (14dp), `Segmented` (15dp), `Medium` (16dp), `SidePanelRail` (18dp), `PanelHeaderAction` (18dp), `Large` (24dp), `ExtraLarge` (28dp), `ActionCard` (32dp), `Avatar` (80dp).
- `Elevation`: `None` (0dp), `Raised` (4dp), `Panel` (8dp).

### 4. Motion & Animation Tokens (`Motion.kt`)

All animation durations, physics, and easing curves are centralized in `Motion.kt`:
- `MotionDuration`: `Fast` (150ms), `Moderate` (200ms), `Standard` (300ms).
- `MotionSpring`: `StiffnessInteractive` (300f), `DampingRatioMediumBouncy` (0.5f), `DampingRatioNoBouncy` (1.0f).
- `MotionEasing`: `Standard` (`CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)`), `EmphasizedDecelerate` (`CubicBezierEasing(0.4f, 0.4f, 0.17f, 0.9f)`).
- `MotionThreshold`: `Fine` (0.001f), `Offset` (0.5f).

### 5. Typography (`Type.kt`)

- `appTypography()`: Material 3 `Typography` configured with the unified Outfit font family across multiple weights (Black, Bold, SemiBold, Medium, Normal, Light, Thin, ExtraLight, ExtraBold).

### 6. Theme Setup (`Theme.kt`)

The unified entry point for styling is `LtiTheme`:
```kotlin
@Composable
fun LtiTheme(
    modifier: Modifier = Modifier,
    appTheme: AppTheme = AppTheme.Blue,      // Light, Dark, Blue
    effectsEnabled: Boolean? = null,         // null = inherit from GlassSceneHost
    reducedMotion: Boolean? = null,          // null = inherit
    content: @Composable () -> Unit,
)
```

- `LtiTheme` reuses the Haze state from an enclosing `GlassSceneHost` (`LocalHazeState`) and only
  creates its own when there is none, so nested themes (e.g. a screen re-wrapping `LtiTheme`) keep
  sampling the same scene.
- Effects and reduced motion are monotonic: an enclosing "off" can never be switched back on by a
  nested theme or host.
- `GlassTheme` accessors: `appTheme`, `isDark`, `hazeState`, `localContentHazeState`,
  `effectsEnabled`, `reducedMotionPolicy` (`GlassReducedMotionPolicy.System` / `Reduced`),
  `syntaxColors`, `diagnosticColors`, `shapes`, `typography`. Colors come from
  `MaterialTheme.colorScheme` (`colorSchemeFor(appTheme)`).

### 7. Design System Guardrails & Detekt Enforcement

To prevent design system drift and ensure strict token adherence, two automated enforcement layers are active:

1. **Gradle Guardrail Task (`verifyDesignSystemGuardrails`):**
   - Run via: `./gradlew verifyDesignSystemGuardrails`
   - Scans UI code outside `theme/` for raw `Color(0x...)` and `alpha = 0.x` literals.
   - Scans UI code outside `theme/` for raw `.dp` / `Dp(...)` dimension literals.
   - Verifies that `feature/*`, `lti-shared`, `lti-desktop`, and `catalog` do not import a raw Material3
     component that has a Glass equivalent (e.g. `material3.Button`, `TextField`, `Switch`, `Slider`,
     `Card`, `IconButton`, `AlertDialog`, `NavigationBar`/`NavigationRail`, `Tab`/`TabRow`,
     `ModalBottomSheet`) — use the corresponding `Glass*` component from `core:designsystem`/`core:ui`
     instead. Primitives with no styled container of their own (`Text`, `Icon`, `MaterialTheme`) are
     not restricted.

2. **Custom Detekt Rule Set (`:detekt-rules`):**
   - Configured in `config/detekt/detekt.yml` under `lti-design-system:`.
   - Rules: `NoRawColorLiteral`, `NoRawAlphaLiteral`, `NoRawDimensionLiteral`, `NoRawMotionLiteral`,
     `NoUnprotectedTextClip`, `NoWeightInScrollableContainer`.

## Haze Glass System

All glass (blur, refraction, tint) is rendered by the vendored **Haze** library
(`:haze`, `:haze-utils`, `:haze-blur`, `:haze-glass`, `:haze-materials`, plus the `-material3`
variants). The old `:backdrop` module (`drawBackdrop`, `LayerBackdrop`, `BackdropEffectScope`) is
gone - do not reintroduce it.

### How a frame is wired

```
GlassSceneHost(effectsEnabled)          // owns the scene HazeState + effects policy (per window)
└─ LtiTheme(appTheme, ...)              // reuses that HazeState; provides GlassTheme.*
   ├─ GlassBackdrop()                   // Canvas + Modifier.hazeSource(GlassTheme.hazeState)
   └─ screens                           // chrome/cards use Modifier.glassSurface(...)
```

Real root: `lti-shared/.../LtiSharedApp.kt`.

1. **Source - `GlassBackdrop()`** (`component/layout/GlassBackdrop.kt`): the per-theme aura canvas,
   registered with `Modifier.hazeSource(GlassTheme.hazeState)`. Place it **once**, behind all
   content, at the app root.
2. **Scene - `GlassSceneHost`** (`theme/GlassSceneHost.kt`): provides the wallpaper `HazeState`
   (`LocalHazeState`), a separate `LocalContentHazeState` for floating controls over scrolling
   content, and the resolved `effectsEnabled`.
3. **Consumers - `Modifier.glassSurface(role, shape, ...)`** (`component/layout/IdeShellSurface.kt`):
   the one modifier every glass surface goes through.
   - Effects on: `Modifier.hazeGlass(input = sceneHazeInput(hazeState), style = ...)` with the style
     from `GlassMaterialStyles.baseStyle(theme, ...)` (`clearStyle` for `FloatingControl`). The glass
     colours come from `GlassMaterialStyles` for the current `AppTheme`; the `surfaceColor` argument
     is **not** used on this path.
   - Effects off: a plain opaque `Modifier.background(surfaceColor)` fallback.
   - `GlassRole`: `Shell` (top bar, navigator, rail, status bar), `TextCard` (cards/panels with
     text), `FloatingControl` (chips, sliders, toggles - Clear optics).
   - `Modifier.ideShellSurface(...)` is `glassSurface(role = GlassRole.Shell, ...)`.
4. **Source selection - `sceneHazeInput()`** (`theme/ControlSourceKey.kt`): consumers sample
   `HazeSourceSelection.Behind`, excluding sources tagged with a `ControlSourceKey` (e.g. a toggle's
   own thumb). `toggleThumbSelection(key)` lets one control see its own source.

### Rules

- Never call `hazeGlass`/`hazeSource` from feature code - use `glassSurface` / `ideShellSurface` or
  a `Glass*` component.
- Glass applies to every `AppTheme`; optics are not branched per theme (only the style colours).
- Keep long-form content on readable surfaces; glass belongs on chrome and controls.
- Chromatic aberration, if ever used, stays modest (Haze guidance 0.1-0.25).

### Verification

- `GlassChromeDiagnosticScreenshotTest` (`core/designsystem/src/desktopTest`):
  `testNavigatorPanelOnBlueInIdeAppFrameShowsGlassOptics` asserts real optics (light passes through
  vs a flat background; blur reduces stripe-edge contrast), plus compared golden images.
- `feature/setup` `SetupThemeScreenshotTest`: compared golden `setup_screen_blue_effects_on.png`.

## Core Glass Components (`core:designsystem/component`)

All glass components render through `Modifier.glassSurface` (Haze, see above) and integrate with `GlassTheme`. Shapes are Haze shapes from `GlassShapes` (`HazePanel`, `HazeCard`, `HazeFlat`, ...).

### 1. `GlassSurface.kt`

Base glass surface component: a container drawn with `Modifier.glassSurface`.
- Parameters: `modifier`, `shape` (default `GlassShapes.HazePanel`), `surfaceColor`, `tint`, `captureBacking`, `borderColor`, `borderWidth`, `content`.

```kotlin
GlassSurface(modifier = Modifier.size(ComponentSize.ActionCardHeight)) {
    Text("Base surface", color = MaterialTheme.colorScheme.onSurface)
}
```

### 2. `GlassCard.kt`

Container card with rounded corners and content padding.
- Default shape: `GlassShapes.HazeCard`.
- Parameters: `modifier`, `shape`, `surfaceColor`, `tint`, `captureBacking`, `contentPadding`, `content`.

```kotlin
GlassCard(
    modifier = Modifier.fillMaxWidth(),
    contentPadding = Spacing.Medium,
) {
    Column {
        Text("Card Title", style = MaterialTheme.typography.titleMedium, color = GlassTheme.colors.onGlass)
        Text("Card body text", style = MaterialTheme.typography.bodyMedium, color = GlassTheme.colors.onGlassSecondary)
    }
}
```

### 3. `GlassPanel.kt`

Panel component optimized for IDE tool windows, explorer trees, and sidebars.
- Default shape: `GlassShapes.HazePanel`.
- Companion composables:
  - `GlassPanelHeader(title, modifier, icon, actions)`: Standardized header row with title and action buttons.
  - `GlassPanelHeaderAction(icon, contentDescription, onClick)`: Compact header action button.
  - `GlassPanelContainer(modifier, header, content)`: Vertical container layout combining header and panel body.

```kotlin
GlassPanel(
    width = 280.dp,
    contentPadding = Spacing.PanelPadding,
) {
    GlassPanelContainer(
        header = {
            GlassPanelHeader(
                title = "EXPLORER",
                actions = {
                    GlassPanelHeaderAction(
                        icon = AppIcons.SettingsOutlined,
                        contentDescription = "Settings",
                        onClick = { }
                    )
                }
            )
        }
    ) {
        FileTreeContent()
    }
}
```

### 4. `GlassLayout.kt` & `GlassSidePanelRail`

Two-glass-surface IDE architecture (chrome "picture frame" surface wrapping an inner content surface):

```
┌────────────────────────────────────────────────────────┐
│ topBar() - GlassTopBar (40dp fixed, Surface 1 Chrome)  │
├────┬──────────────────────────────────────────────┬────┤
│ ▣  │  Explorer │   Editor   │  AI Assistant       │ ✦  │
│rail│  (left)   ├────────────┤     (right)         │rail│
│(1) │           │  Terminal  │                     │(1) │
│    │           │  (bottom)  │                     │    │
│    │  (Surface 2 Content Glass Surface, own bg)   │    │
├────┴──────────────────────────────────────────────┴────┤
│ statusBar() - GlassStatusBar (22dp, Surface 1 Chrome)  │
└────────────────────────────────────────────────────────┘
```

- **Surface 1 (Chrome):** Outer `GlassSurface` containing `topBar`, `leftRail`, `rightRail`, and `statusBar` as one glass surface for uniform chrome luma.
- **Surface 2 (Content):** Inner `GlassSurface` with `surfaceColor = glassColors.canvasSurface` and `tint = Color.Transparent`, containing `leftPanel`, `mainContent`, `bottomPanel`, and `rightPanel`.
- **`GlassSidePanelRail`**: Standalone vertical icon strip for side panel tool windows with inner hairline divider.
- **`GlassSidePanelTabs`**: Convenience wrapper delegating to `GlassSidePanelRail` and hosting the active tab content.
- State controller: `LtiLayoutState` (`rememberLtiLayoutState()`).
  - Controls panel visibility: `showLeft`, `showRight`, `showBottom`.
  - Controls panel weights: `leftWeight`, `rightWeight`, `bottomWeight`.
  - Methods: `toggleLeft()`, `toggleRight()`, `toggleBottom()`, `setLeftPanelVisible()`, `setRightPanelVisible()`, `setBottomPanelVisible()`.
- Composable parameters: `state`, `modifier`, `leftRail`, `leftPanel`, `mainContent`, `bottomPanel`, `rightPanel`, `rightRail`, `topBar`, `statusBar`, `showToggleButtons`, `appearance`.

### 5. `GlassTopBar.kt` & `GlassStatusBar.kt`

- **`GlassTopBar.kt`**:
  - 40dp height top bar with app logo gradient (`BrandColors.LogoGradientEnd`), layout panel toggle icons, search bar slot, notification/settings triggers, and desktop window controls (minimize, maximize/restore, close with `#E81123` hover).
  - Supports `drawOwnChrome: Boolean = true` (when `false`, transparently delegates background, shape, and borders to parent `GlassLayout` frame).
  - Uses `LocalWindowControlActions` to trigger platform window actions.
- **`GlassStatusBar.kt`**:
  - 22dp height bottom bar displaying Git branch, file status, cursor position (line/column), indentation, encoding, and line endings (`LineEnding.LF`, `CRLF`, `CR`).
  - Supports `drawOwnChrome: Boolean = true` (when `false`, delegates styling to parent `GlassLayout` frame).
  - State controller: `GlassStatusBarState` (`rememberGlassStatusBarState()`).

## Interactive Components (`core:designsystem/component`)

### 1. `GlassButton.kt`

Interactive buttons with fluid press lighting (`InteractiveHighlight`), Haze glass surfaces, and disabled state handling.
- Variants (`GlassButtonVariant`):
  - `GlassButtonVariant.Primary`: Accent tint fill, elevated surface.
  - `GlassButtonVariant.Secondary`: Secondary surface tint fill.
  - `GlassButtonVariant.Standard`: Standard glass surface with subtle tint.
  - `GlassButtonVariant.Text`: Transparent background without glass overhead.
- Composables:
  - `GlassButton(onClick, modifier, enabled, shape, variant, ...)`
  - `GlassPrimaryButton(onClick, modifier, enabled, content)`
  - `GlassTextButton(onClick, modifier, enabled, content)`
  - `GlassIconButton(onClick, modifier, size, shape, checked, selected, enabled, content)`
  - `GlassIconToggleButton(selected, onSelectedChange, modifier, size, shape, enabled, content)`

```kotlin
// Primary button
GlassPrimaryButton(onClick = { doSave() }) {
    Text("Save Changes")
}

// Icon toggle button for toolbars
GlassIconToggleButton(
    selected = isMuted,
    onSelectedChange = { isMuted = it }
) {
    Icon(
        imageVector = if (isMuted) AppIcons.VolumeOff else AppIcons.VolumeUp,
        contentDescription = "Toggle Mute"
    )
}
```

### 2. `GlassDialog.kt`

Modal dialogs and loading indicators with Haze blur and scrim.
- Composables:
  - `GlassDialog(onDismissRequest, confirmButton, modifier, title, dismissButton, shape, properties, content)`
  - `GlassLoadingDialog(visibilityState: LoadingDialogState, modifier)` (`LoadingDialogState.Hidden` / `LoadingDialogState.Shown`)

```kotlin
var showDialog by remember { mutableStateOf(false) }

if (showDialog) {
    GlassDialog(
        onDismissRequest = { showDialog = false },
        title = "Confirm Action",
        confirmButton = {
            GlassPrimaryButton(onClick = { showDialog = false }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            GlassTextButton(onClick = { showDialog = false }) {
                Text("Cancel")
            }
        }
    ) {
        Text("Are you sure you want to proceed?", color = GlassTheme.colors.onGlass)
    }
}
```

### 3. `GlassIcon.kt`

Standalone interactive glass icon container:
- `GlassIcon(onClick, modifier, isInteractive, tint, surfaceColor, icon)`

### 4. Glass modifiers

- `Modifier.glassSurface(role, shape, ...)` / `Modifier.ideShellSurface(...)` (`component/layout/IdeShellSurface.kt`): the only way to draw a glass surface - see **Haze Glass System**.
- `Modifier.glassOutlineBorder(color, width, shape)` (`component/primitives/GlassModifiers.kt`): hairline border drawn inset so it never spills outside capsules or rounded rectangles.

## Input & Navigation Components (`core:designsystem/component`)

### 1. `GlassTextField.kt`

Inputs with subtle glass blur, animated focus border transitions, and password/OTP specializations.
- Composables:
  - `GlassTextField(value, onValueChange, modifier, enabled, readOnly, textStyle, label, placeholder, helperText, errorText, leadingIcon, trailingIcon, ...)`
  - `GlassPasswordField(label, value, showPassword, showPasswordChange, onValueChange, modifier, readOnly, singleLine, hint, showPasswordTestTag, autoFocus, ...)`
  - `GlassOtpTextField(onOtpTextCorrectlyEntered, modifier, realOtp, otpCount)`

```kotlin
var username by remember { mutableStateOf("") }

GlassTextField(
    value = username,
    onValueChange = { username = it },
    label = "Username",
    placeholder = "Enter username",
    leadingIcon = {
        Icon(AppIcons.Person, contentDescription = null, tint = GlassTheme.colors.onGlassSecondary)
    }
)
```

### 2. `GlassSlider.kt`

- `GlassSlider(value, onValueChange, valueRange, visibilityThreshold, modifier)`: Drag-interpolated slider with velocity-squish thumb effect on a glass track.

### 3. `GlassToggle.kt`

- `GlassToggle(checked, onCheckedChange, modifier, state)`: Toggle switch with animated thumb offset and track highlighting (`GlassToggleState`, `rememberGlassToggleState`).

### 4. `GlassTabBar.kt` & `GlassBottomTabs.kt`

- `GlassTabBar(tabs, onTabSelected, modifier, selectedIndex)`: Horizontal tab bar with pill indicator and tab items (`GlassTab`).
- `GlassBottomTabs(...)`: Bottom navigation tabs with interactive spotlight positioning.

### 5. `GlassNavigation.kt`

- `GlassNavigationBar`, `GlassNavigationBarItem`: Material 3 glass bottom navigation bar.
- `GlassNavigationRail`, `GlassNavigationRailItem`: Material 3 glass vertical navigation rail.

### 6. `GlassStepper.kt` (`core:designsystem/component/progress`)

Liquid glass step-progress visualizer for multi-stage pipelines (Setup Doctor, ROM Build Pipeline, Workspace Provisioning).
- Orientation: `GlassStepperOrientation.Horizontal`, `GlassStepperOrientation.Vertical`.
- Statuses (`GlassStepStatus`):
  - `PENDING`: Waiting to run, muted outline, step number displayed.
  - `RUNNING`: Actively executing, accent outline, animated spinner indicator.
  - `SUCCESS`: Completed successfully, green accent, checkmark indicator.
  - `WARNING`: Completed with non-fatal warnings, amber status icon.
  - `FAILED`: Errored stage, red error indicator, retry action button (`onRetryStep`).
  - `SKIPPED`: Cache hit / bypassed, muted checkmark indicator.
  - `CANCELLED`: User or signal cancellation, muted stop badge.
  - `INTERRUPTED`: Daemon / connection dropped mid-execution, warning indicator.
  - `RESTORED`: Snapshot restored from previous session, cyan recovery badge.
- Model: `GlassStepInfo(id, shortTitle, status, statusLabel)`.
- Helpers: `GlassStepperDefaults.defaultStatusLabel(status)`.
- Composables:
  - `GlassStepper(steps, modifier, orientation, isBusy, onRetryStep, onStepClick)`

## Modal & Developer Utilities

### 1. `GlassBottomSheet.kt`

Bottom sheet modal with drag-to-dismiss gesture handling and spring physics.
- State controller: `GlassBottomSheetState` (`rememberGlassBottomSheetState()`)
  - State values (`GlassBottomSheetValue`): `Collapsed`, `Expanded`.
  - Methods: `expand()`, `collapse()`, `toggle()`.
- Composables:
  - `GlassBottomSheet(state, onDismissRequest, modifier, shape, scrimColor, dismissThreshold, content)`
  - `GlassBottomSheetScaffold(visible, onDismissRequest, modifier, title, headerContent, content)`
  - `GlassBottomSheetExample()`

```kotlin
var showSheet by remember { mutableStateOf(false) }

GlassBottomSheetScaffold(
    visible = showSheet,
    onDismissRequest = { showSheet = false },
    title = "Settings Sheet"
) {
    Column(Modifier.padding(Spacing.Medium)) {
        Text("Sheet Content", color = GlassTheme.colors.onGlass)
        Spacer(Modifier.height(Spacing.SmallMedium))
        GlassPrimaryButton(onClick = { showSheet = false }) {
            Text("Close")
        }
    }
}
```

### 2. `GlassPerformanceMonitor.kt`

Real-time developer overlay tracking glass rendering metrics and frame timing.
- Metrics data class (`GlassPerformanceMetrics`):
  - `fps`: Current frames per second
  - `avgFrameTimeMs`, `minFrameTimeMs`, `maxFrameTimeMs`: Frame render duration statistics
  - `droppedFrames`: Count of frames taking > 16.67ms (target: 60 FPS)
  - `shaderCacheSize`: Active compiled shader count
  - `activeBackdrops`: Number of active glass sources (field name predates the Haze migration)
  - `memoryUsageKB`: Estimated glass memory usage
  - `componentsRendered`: Number of glass elements
  - `performanceRating`: 3 (Excellent: 55+ FPS, <= 18ms), 2 (Good: 45+ FPS, <= 25ms), 1 (Fair: 30+ FPS, <= 35ms), 0 (Poor: < 30 FPS or > 35ms)
  - `performanceColor`: maps to `PerformanceMonitorColors.Good`, `Degraded`, `Warning`, or `Bad`.
- Performance collector: `GlassPerformanceCollector` (`recordFrame(ms)`, `computeMetrics(...)`, `reset()`).
- Overlay composables:
  - `GlassPerformanceMonitor(visible, modifier, position, collector)`
  - `MonitorPosition`: `TopStart`, `TopEnd`, `BottomStart`, `BottomEnd`.
  - `GlassPerformanceMonitorExample()`

```kotlin
var showMonitor by remember { mutableStateOf(true) }

Box(Modifier.fillMaxSize()) {
    AppContent()

    // Real-time overlay in debug / profiling builds
    GlassPerformanceMonitor(
        visible = showMonitor,
        position = MonitorPosition.TopEnd
    )
}
```

## Accessibility & Reduced Motion

### WCAG Focus Ring Token (`focusRing`)

- `focusRing: Color` is a dedicated field on `GlassColorScheme` (`core/designsystem/src/commonMain/kotlin/org/ide/lti/core/designsystem/theme/Color.kt`).
- Derived from `colorScheme.primary` at full 1.0f opacity.
- Meets WCAG 2.x AA non-text contrast requirements (>= 3.0:1) against `glassSurface` in both dark and light modes.

### Reduced Motion Support (`GlassTheme.reducedMotionPolicy`)

- Read `GlassTheme.reducedMotionPolicy == GlassReducedMotionPolicy.Reduced` (Haze's policy enum).
- Set it with `LtiTheme(reducedMotion = true)` (the app passes the persisted `AppSettingsState.reducedMotion`).
- When reduced: snap transitions (`snap()`), replace spinners with static text, and stop decorative infinite animations.

## Testing & Verification Infrastructure

### Automated Contrast Verification (`GlassContrastTest.kt`)

The repo contains an automated WCAG 2.x mathematical contrast regression test located at:
`core/designsystem/src/commonTest/kotlin/org/ide/lti/core/designsystem/theme/GlassContrastTest.kt`

Run via:
```bash
./gradlew :core:designsystem:test
```

**Verified Contrast Ratios against `glassSurface`:**
- **`DarkGlassColorScheme`:**
  - `onGlass` vs `glassSurface`: **14.37:1** (WCAG AA requirement: >= 4.5:1) — PASS
  - `onGlassSecondary` vs `glassSurface`: **10.85:1** (WCAG AA requirement: >= 4.5:1) — PASS
  - `onGlassMuted` vs `glassSurface`: **5.88:1** (WCAG AA requirement: >= 3.0:1) — PASS
  - `glassAccent` vs `glassSurface`: **10.87:1** (WCAG AA requirement: >= 3.0:1) — PASS
  - `focusRing` vs `glassSurface`: **10.87:1** (WCAG AA requirement: >= 3.0:1) — PASS
- **`LightGlassColorScheme`:**
  - `onGlass` vs `glassSurface`: **16.35:1** (WCAG AA requirement: >= 4.5:1) — PASS
  - `onGlassSecondary` vs `glassSurface`: **8.86:1** (WCAG AA requirement: >= 4.5:1) — PASS
  - `onGlassMuted` vs `glassSurface`: **4.27:1** (WCAG AA requirement: >= 3.0:1) — PASS
  - `glassAccent` vs `glassSurface`: **6.15:1** (WCAG AA requirement: >= 3.0:1) — PASS
  - `focusRing` vs `glassSurface`: **6.15:1** (WCAG AA requirement: >= 3.0:1) — PASS

> [!NOTE]
> `GlassContrastTest` is a focused mathematical test validating relative luminance and contrast formulas on color tokens. It is not a full UI rendering test suite.

## Real Integration Example (`WorkspaceScreen.kt`)

The production IDE workspace is wired together in `feature/workspace/src/commonMain/kotlin/org/ide/lti/feature/workspace/WorkspaceScreen.kt`:

```kotlin
@Composable
fun WorkspaceScreen(
    modifier: Modifier = Modifier,
) {
    val ltiLayoutState = rememberLtiLayoutState()

    GlassLayout(
        state = ltiLayoutState,
        modifier = modifier,
        leftPanel = { ExplorerPanel() },
        mainContent = { EditorPanel() },
        bottomPanel = { TerminalPanel() },
        rightPanel = { AIAssistantPanel() },
    )
}
```

And initialized at the app root in `lti-shared/src/commonMain/kotlin/org/ide/lti/shared/LtiSharedApp.kt` (simplified):

```kotlin
GlassSceneHost(effectsEnabled = appSettingsState.effectsEnabled) {
    LtiTheme(
        appTheme = AppTheme.fromId(appSettingsState.theme),
        effectsEnabled = appSettingsState.effectsEnabled,
        reducedMotion = appSettingsState.reducedMotion,
    ) {
        Box(Modifier.fillMaxSize()) {
            GlassBackdrop()                     // the single Haze source for the whole window
            RootNavGraph(navHostController = navController, startDestination = SETUP_ROUTE)
        }
    }
}
```

## Verified Component Matrix

| Component | File | Location | Interactive | Key Feature |
|-----------|------|----------|-------------|-------------|
| `GlassSurface` | `GlassSurface.kt` | `core:designsystem` | No | Base Haze glass surface container |
| `GlassCard` | `GlassCard.kt` | `core:designsystem` | No | Rounded container with standard card padding |
| `GlassPanel` | `GlassPanel.kt` | `core:designsystem` | No | Tool window / sidebar panel container & header |
| `GlassLayout` | `GlassLayout.kt` | `core:designsystem` | Yes | 4-panel resizable IDE workspace with drag dividers |
| `GlassTopBar` | `GlassTopBar.kt` | `core:designsystem` | Yes | Top bar (40dp) with window controls & actions |
| `GlassStatusBar` | `GlassStatusBar.kt` | `core:designsystem` | No | Status bar (22dp) with cursor and branch metrics |
| `GlassButton` | `GlassButton.kt` | `core:designsystem` | Yes | Interactive highlight, 4 style variants |
| `GlassPrimaryButton` | `GlassButton.kt` | `core:designsystem` | Yes | Elevated accent-filled primary action |
| `GlassTextButton` | `GlassButton.kt` | `core:designsystem` | Yes | Transparent background text action |
| `GlassIconButton` | `GlassButton.kt` | `core:designsystem` | Yes | Compact circular toolbar icon button |
| `GlassIconToggleButton` | `GlassButton.kt` | `core:designsystem` | Yes | Toggle state toolbar icon button |
| `GlassDialog` | `GlassDialog.kt` | `core:designsystem` | Yes | Frosted modal dialog with scrim |
| `GlassLoadingDialog` | `GlassDialog.kt` | `core:designsystem` | No | Centered modal circular progress indicator |
| `GlassTextField` | `GlassTextField.kt` | `core:designsystem` | Yes | Text input with animated focus/error border |
| `GlassPasswordField` | `GlassTextField.kt` | `core:designsystem` | Yes | Password input with visibility toggle icon |
| `GlassOtpTextField` | `GlassTextField.kt` | `core:designsystem` | Yes | Multi-box OTP verification input |
| `GlassIcon` | `GlassIcon.kt` | `core:designsystem` | Yes | Standalone interactive glass icon container |
| `GlassSlider` | `GlassSlider.kt` | `core:designsystem` | Yes | Velocity-squish drag thumb on glass track |
| `GlassToggle` | `GlassToggle.kt` | `core:designsystem` | Yes | Animated glass toggle switch |
| `GlassTabBar` | `GlassTabBar.kt` | `core:designsystem` | Yes | Horizontal tab bar with pill indicators |
| `GlassBottomTabs` | `GlassBottomTabs.kt` | `core:designsystem` | Yes | Bottom navigation with tinted spotlight |
| `GlassNavigationRail` | `GlassNavigation.kt` | `core:designsystem` | Yes | Vertical navigation rail for desktop |
| `GlassNavigationBar` | `GlassNavigation.kt` | `core:designsystem` | Yes | Bottom navigation bar for mobile/desktop |
| `GlassBottomSheet` | `GlassBottomSheet.kt` | `core:designsystem` | Yes | Drag-to-dismiss modal sheet with spring physics |
| `GlassPerformanceMonitor` | `GlassPerformanceMonitor.kt` | `core:designsystem` | Yes | Real-time FPS, frame time, & memory overlay |
| `GlassCodeEditor` | `GlassCodeEditor.kt` | `core:ui` | Yes | Syntax-highlighted glass code editor widget |
| `GlassFileTree` | `GlassFileTree.kt` | `core:ui` | Yes | Interactive workspace file explorer tree |
| `GlassChatInput` | `GlassChatInput.kt` | `core:ui` | Yes | AI assistant chat input with send action |
| `GlassMessageBubble` | `GlassMessageBubble.kt` | `core:ui` | No | Chat message bubble with role-based styling |
| `GlassScrollableTabRow` | `GlassScrollableTabRow.kt` | `core:ui` | Yes | Horizontally scrollable tab container |
| `GlassSmallChip` | `GlassSmallChip.kt` | `core:ui` | Yes | Compact metadata chip widget |

## Production Checklist

Before merging UI changes or releasing new features:

- [ ] **Pre-push rule gate**: `scripts/git-hooks/pre-push` runs `detekt`, `verifyDesignSystemGuardrails` and `checkArchitecture` and blocks the push on any failure. Any `run`/`build`/`clean` installs it (`./gradlew installGitHooks` sets `core.hooksPath`). Existing debt lives in `config/detekt/baseline/`; never regenerate a baseline to hide new findings.
- [ ] **Design-System Guardrails**: Run `./gradlew verifyDesignSystemGuardrails` to verify no raw color (`Color(0x...)`), alpha, dimension (`.dp`), or motion literals bypass `theme/`.
- [ ] **Detekt Static Analysis**: Run `./gradlew detekt` to verify all custom `:detekt-rules` (`lti-design-system`) pass with 0 violations.
- [ ] **WCAG Contrast Ratios**: Run `./gradlew :core:designsystem:test` to ensure `GlassContrastTest` passes all AA contrast thresholds (>= 4.5:1 text, >= 3.0:1 UI/muted/focus ring).
- [ ] **Focus Ring Visibility**: Verify that focused interactive elements use `GlassTheme.colors.focusRing` or `glassBorderHighlight` for clear keyboard focus indication.
- [ ] **Reduced Motion Support**: Verify that components check `GlassTheme.reducedMotionPolicy` to snap or simplify animations when reduced.
- [ ] **Single Haze Source**: `GlassBackdrop()` appears once at the app root; surfaces use `glassSurface`, never their own `hazeSource`/`hazeGlass`.
- [ ] **Performance Validation**: Enable `GlassPerformanceMonitor` in debug mode to verify target framerate (>= 55 FPS) and average frame time (< 18ms) during panel resizing and scrolling.
- [ ] **Manual Keyboard Navigation**: Verify Tab, Shift+Tab, Enter, and Space keys correctly navigate and activate interactive glass controls.

## Troubleshooting

**Problem: Low FPS or dropped frames during glass rendering**
- **Cause**: Too many glass surfaces stacked over frequently redrawn content.
- **Solution**: Keep glass on chrome and controls; give long scrolling content a solid surface.
- **Solution**: Profile with `GlassPerformanceMonitor` and check the Haze performance mode.

**Problem: Glass effects are not rendering (flat colors only)**
- **Cause**: Effects are off - check the persisted `AppSettingsState.effectsEnabled` and any enclosing `GlassSceneHost(effectsEnabled = false)`, which nothing below it can override.
- **Cause**: No Haze source behind the surface - `GlassBackdrop()` must be at the app root, drawn before the surfaces, on the same `GlassTheme.hazeState`.
- **Check**: run `GlassChromeDiagnosticScreenshotTest` (optics assertions) to separate a rendering bug from app configuration.

**Problem: Text is hard to read on translucent glass surfaces**
- **Cause**: Direct hardcoded text colors or insufficiently opaque surface tint.
- **Solution**: Use `GlassTheme.colors.onGlass` for primary text and `GlassTheme.colors.onGlassSecondary` for secondary text.
- **Solution**: Verify text contrast against `glassSurface` by running `./gradlew :core:designsystem:test`.

**Problem: Guardrail or Detekt rule violation during build**
- **Cause**: Direct instantiation of `Color(0x...)`, `alpha = 0.5f`, or raw `.dp` inside UI files outside `theme/`.
- **Solution**: Import the corresponding centralized token from `org.ide.lti.core.designsystem.theme.*` (`GlassTheme.colors`, `AlphaTokens`, `Spacing`, `CornerRadius`, `ComponentSize`, `MotionDuration`, etc.).

## Setup Flow & Clean Machine Architecture

Setup takes a Windows machine with an empty WSL Ubuntu to a working toolchain (spec
`specs/007-clean-machine-setup/`). The check runs in `WslToolchainProvisioner` (`core:data`), one function
per stage; a failed stage marks every later stage pending and stops.

### 1. Stages (in order)
1. **WSL 2 Runtime** (`WSL_DETECTION`): classifies every distro (`DistroStatus`: WSL unavailable, no
   distro, didn't start, unsupported, needs a user, usable). Picks the single usable Ubuntu LTS, else the
   WSL default (`wsl -l -v`), else asks the user to pick. Nothing is guessed: no hard-coded "Ubuntu".
2. **System packages** (`SYSTEM_PACKAGES`): `WslHostPrerequisiteProbe` checks every `RequirementCatalog`
   entry in one `wsl.exe` round trip, **without the build service**, and finds a Java >= 21 home.
3. **Build service** (`SERVER_CONNECTIVITY`): installs the bundled server if missing or outdated, starts
   it with that Java, and checks health.
4. **Pre-Flight Doctor** (`SYSTEM_DIAGNOSTICS`): inspectors that run through the build service.
5. **Source Repositories** (`REPO_SYNCHRONIZATION`): pinned tool sources.
6. **Toolchain Binaries** (`TOOLCHAIN_COMPILATION`): builds the `ToolCatalog` tools and registers exactly
   those IDs with the service (`ToolPublicationAdapter`), then confirms each resolves as `DYNAMIC`.

### 2. Package handoff (no stored passwords)
- Missing packages produce one command from `AptCommandBuilder`: `sudo apt-get update && sudo apt-get
  install -y <exact missing list>`.
- The attempt is journaled as `BOOTSTRAP_PACKAGES` / `AWAITING_USER_ACTION` and `TerminalHandoffSheet`
  opens. The user runs the command in their own terminal and types their password there.
- "I've run it — check again" re-probes (the terminal's exit code is never trusted). When everything is
  present the attempt is archived and the check continues to the build service.
- Never: `sudo -S`, `NOPASSWD`, sudoers edits, or a password field (`SecuritySudoersGuardTest`).

### 3. Bundled build service
- `lti-desktop:stageBundledServer` depends on the included build's `:installDist` and copies it into the
  app resources as `ltirom-server/`, adding `server-version.txt` and `checksums.txt` (sha256).
- `ServerArtifactInstaller` copies it to `~/.ltirom/server/versions/<v>.partial`, runs
  `sha256sum -c checksums.txt`, renames it to `versions/<v>` and swaps the `current` link atomically.
  A failure keeps the previous version active; the active version is never deleted.

### 4. SetupEnvironment snapshot rule
- An operation captures one `SetupEnvironment` (distro, user, home) when it starts; every command, the
  journal and the daemon start use that snapshot. Changing the selection mid-operation never retargets it.
- A resumed handoff uses the environment journaled with its attempt, never the current selection.

### 5. Logs and Recovery
- Every check step writes `[check]` start/result lines; the activity buffer is bounded in memory and
  each attempt is also persisted to `<LOCALAPPDATA>/LtiRomGui/logs/setup/<attemptId>.log` (size-capped,
  one rotation). Export saves that file, never the truncated in-memory tail.
- The Recovery tab shows only journal data (`ToolchainSetupState.pendingAttempt`) and real step state;
  with nothing to recover it says so.

### 6. Tool Group Catalog & Recipes (008 Tool Version Editing)
- `ToolGroupCatalog` (`core:domain`) defines 7 authoritative tool build groups:
  - `ANDROID_TOOLS` (`android-tools`): `ToolSource.Git`, `RecipeConfig.AndroidTools`. Native outputs: `adb`, `fastboot`, `mke2fs`, `e2fsdroid`, `lpmake`, `lpunpack`, `simg2img`, `avbtool`, etc.
  - `EROFS_UTILS` (`erofs-utils`): `ToolSource.Git`, `RecipeConfig.CMake`. Native outputs: `mkfs.erofs`, `dump.erofs`, `fsck.erofs`, `erofsfuse`.
  - `IMG2SDAT` (`img2sdat`): `ToolSource.Git`, `RecipeConfig.ScriptCopy`. Script output: `img2sdat`.
  - `APKTOOL` (`apktool`): `ToolSource.Git`, `RecipeConfig.GradleJar`. Jar output: `apktool.jar`.
  - `SIGNAPK` (`signapk`): `ToolSource.Git`, `RecipeConfig.GradleJar`. Jar output: `signapk.jar`.
  - `GH` (`gh`): `ToolSource.Release`, `RecipeConfig.Release`. Native output: `gh`.
  - `PAYLOAD_DUMPER_GO` (`payload-dumper-go`): `ToolSource.Release`, `RecipeConfig.Release`. Native output: `payload-dumper-go`.
- Sealed `RecipeConfig` types (`core:domain`):
  - `RecipeConfig.AndroidTools(cmakeArgs, gitIdentity, patchVendor, revision)`: native build via `AndroidToolsRecipe`.
  - `RecipeConfig.CMake(sourceSubdir, buildSubdir, cmakeArgs, revision)`: CMake-based build via `CMakeRecipe`.
  - `RecipeConfig.GradleJar(task, outputPath, revision)`: shadow JAR build via `GradleJarRecipe`.
  - `RecipeConfig.ScriptCopy(files, revision)`: script stage copy via `ScriptCopyRecipe`.
  - `RecipeConfig.Release(versions, revision)`: checksum-verified release tarball download via `ReleaseDownloadRecipe`.
- Layout verification: `SourceResolverPort.checkLayout` / `GitSourceResolver.checkLayout` validates expected repository layout before offering a build.
- Build dependencies: missing `buildPackages` transition to `TerminalHandoffSheet`.

### 7. Content-Addressable Artifacts & Toolchain Installations (`toolchainLayoutV2`)
- Multi-version directory layout under `~/LtiRomTools/`:
  - `~/LtiRomTools/artifacts/<groupId>/<artifactId>/`: Immutable artifact stores for built or downloaded binaries, containing `manifest.json` (`ArtifactManifest`) and tool outputs.
  - `~/LtiRomTools/installs/<installId>/`: Assembled installations containing `install.json` (`InstallManifest`) and `bin/` directory with relative symlinks to artifact binaries (`../../artifacts/<groupId>/<artifactId>/bin/<file>`).
  - `~/LtiRomTools/bin`: Active installation symlink pointing directly to `installs/<installId>/bin` (swapped atomically via `bin.tmp-<nano>`).
- Direct writes into `~/LtiRomTools/bin` or candidate directories are prohibited outside `ArtifactStore` and `InstallationManager`.

### 8. Atomic Activation, Health Verification & Safe Switching
- `SwitchToolchainHandler` (`core:data`) coordinates toolchain switching:
  1. Freezes selections into `ResolvedInput` and checks `ArtifactStore` cache; reuses matching artifacts by input fingerprint.
  2. Compiles or downloads changed tool groups into isolated staging directories before publishing to `ArtifactStore`.
  3. Assembles candidate installation directory via `InstallationManager` at `~/LtiRomTools/installs/<installId>`.
  4. Runs verification suite (`ToolVerifier`) on required candidate binaries.
  5. Records checkpoint in `ActivationReconciler` (`SwitchCheckpoint.CANDIDATE_VERIFIED`).
  6. Dispatches activation to `ToolchainActivator` on the build service via `ToolchainInstallationPort.activate`.
- Build service activation safety (`ToolchainActivator`):
  - Engine hold check: if active work exists, rejects activation with `ActivationOutcome.Blocked`.
  - Atomic link swap: swaps `~/LtiRomTools/bin` to point to `installs/<targetInstallId>/bin` using atomic filesystem rename.
  - Self-healing rollback: if post-switch verification fails, restores previous active install and returns `ActivationOutcome.VerifyFailedRestored`.
  - Audit logging: records transitions to `ToolchainJournal`.

### 9. Toolchain Health & Recovery
- Clean layout policy:
  - No legacy migration: all operations strictly target `~/LtiRomTools/{artifacts,installs,bin}`.
  - Uninitialized state: if no active install exists, toolchain remains uninitialized until the first activation commits.
- Health tracking:
  - `ToolchainHealth` (`core:domain`) evaluates four operational facts: `selectedInstallId != null`, `intact` (artifact files exist and match sha256), `serviceResolves` (build service resolves binaries), and `journalCommitted` (active install recorded in journal).
  - Overall health: `isHealthy = selectedInstallId != null && intact && serviceResolves && journalCommitted`.
- Recovery and repair:
  - Unhealthy installations transition group row status to `ToolGroupStatus.NEEDS_RECOVERY` ("Needs recovery") in `ToolVersionsState` / `ToolVersionsPresentationMapper`.
  - Repair operations rebuild damaged artifacts via `SwitchToolchainHandler` or roll back via `ToolchainRestoreCoordinator`.

## Build Layout & Multi-Repo Requirements

```text
LtiRomProject/
├── LtiRomGui/      (Compose Multiplatform desktop client, this repo)
└── LtiRomServer/   (Ktor build service that runs inside WSL)
```

- `LtiRomGui` and `LtiRomServer` must be siblings: `settings.gradle.kts` includes `../LtiRomServer` as
  a composite build (`rootProject.name = "ltirom-server"`).
- CI must check out both, side by side.
- You never build the server by hand: `stageBundledServer` (a dependency of `run`, `createDistributable`
  and `package*`) builds it through the included build.

## Credits

LtiRom Studio Glass Theme System is built on:
- Compose Multiplatform (JetBrains)
- Haze (Chris Banes) - vendored glass/blur engine
- Skia Graphics Engine (Google)
- Material 3 Design Tokens (Google)
- WCAG 2.1 Accessibility Guidelines (W3C)