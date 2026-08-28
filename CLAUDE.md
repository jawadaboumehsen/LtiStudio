- to memorize donk breake the project arch and moduarization, and system desgin

## Architecture: core:designsystem vs core:ui

Add to `core:designsystem` when:
- Defining theme tokens (colors, alpha, typography, dimensions, motion)
- Creating branded variants of Material 3 components
- Adding app-wide icons (`AppIcons.kt`)
- Building stateless, reusable UI primitives and glass layout scaffolding
- Something every feature might need

Examples:
- Glass color scheme and design tokens (`Color.kt`, `Alpha.kt`, `Dimens.kt`, `Motion.kt`, `Type.kt`, `Backdrop.kt`)
- Base glass surfaces and containers (`GlassSurface.kt`, `GlassCard.kt`, `GlassPanel.kt`)
- Interactive glass controls (`GlassButton.kt`, `GlassTextField.kt`, `GlassSlider.kt`, `GlassToggle.kt`)
- Layout scaffolding (`GlassLayout.kt`, `GlassTopBar.kt`, `GlassStatusBar.kt`, `GlassBottomSheet.kt`)
- Developer tools (`GlassPerformanceMonitor.kt`)

Add to `core:ui` when:
- Building complex widgets that combine multiple components
- Creating stateful composables with ViewModels
- Adding utility functions for navigation, effects, lifecycle
- Implementing business logic in UI (like password strength validation)
- Platform-specific UI utilities (share, file pickers)

Examples:
- Code editor and file tree components (`GlassCodeEditor.kt`, `GlassFileTree.kt`)
- Chat input and message bubbles (`GlassChatInput.kt`, `GlassMessageBubble.kt`)
- Scrollable tab rows and small chips (`GlassScrollableTabRow.kt`, `GlassSmallChip.kt`)
- Password strength indicator (`PasswordStrengthIndicator.kt`)
- ViewModel base classes and lifecycle effect handlers

## Design System Architecture & Centralized Tokens

> [!IMPORTANT]
> Before editing anything under `core/designsystem/theme/`, `core/designsystem/component/`,
> `core/ui/`, or any file rendering text/icons on a `Glass*` surface, read
> `rules/systemdesignrule.md` (project root) first. It documents hard rules and known blind spots
> discovered by real bugs this session — most load-bearing: any content-lambda-accepting glass
> component must make `LocalContentColor` resolve to something other than black, and the
> design-system guardrails/Detekt rules do NOT catch a color/size parameter being omitted entirely
> (only a wrong literal). This section (`CLAUDE.md`) is the token/component API catalog;
> `rules/systemdesignrule.md` is the "don't repeat this bug" companion.

LtiRom Studio uses a professional IDE design built around a centralized token system in `core/designsystem/src/commonMain/kotlin/org/ide/lti/core/designsystem/theme/`.

### 1. Color System (`Color.kt`)

All colors are anchored to a brand seed color and dynamic Material 3 color generation:
- `LtiSeedColor` = `#38BDF8` (Electric Cyan)
- `DarkGlassColorScheme` / `LightGlassColorScheme`: dynamically generated via `dynamicGlassColorScheme()` and `com.materialkolor.dynamicColorScheme()`.

**`GlassColorScheme` Tokens:**
```kotlin
@Immutable
data class GlassColorScheme(
    val glassSurface: Color,
    val glassSurfaceElevated: Color,
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
)
```

**Key Color Tokens & Helpers:**
- `focusRing`: Dedicated WCAG focus ring token derived from `colorScheme.primary` at full (1.0f) opacity.
- `onGlass` / `onGlassSecondary` / `onGlassMuted`: Text and icon colors guaranteeing WCAG contrast against `glassSurface`.
- Specialized token objects:
  - `WindowControlColors.CloseHover`: Native window close button hover color (`#E81123`).
  - `BrandColors.LogoGradientEnd`: Brand logo cyan gradient stop (`#00E5FF`).
  - `PerformanceMonitorColors`: `Good` (`#4EC9B0`), `Warning` (`#D7BA7D`), `Degraded` (`#CE9178`), `Bad` (`#F48771`).
  - `ToggleColors`: `ActiveLight` (`#34C759`), `ActiveDark` (`#30D158`).
  - `PreviewSampleColors`: `Blue`, `Purple`, `Orange`, `Green`.
  - `GlassLayoutColors.PanelShadow`: Panel shadow color token (`#26000000`).
  - `PasswordStrengthColors`: `Weak` (`#8B6609`), `Strong` (`#41B06D`), `VeryStrong` (`#9C27B0`).

### 2. Alpha & Opacity Tokens (`Alpha.kt`)

All opacity values are centralized in `AlphaTokens` (no raw float literals in UI code):
- `AlphaTokens.UltraFaint` (0.03f), `Faint` (0.05f), `Subtle` (0.10f), `Glow` (0.15f), `Hover` (0.20f)
- `AlphaTokens.Faded` (0.25f), `Muted` / `Scrim` (0.30f), `Ambient` (0.35f), `Medium` / `Disabled` (0.40f)
- `AlphaTokens.Soft` (0.45f), `Half` / `Border` / `Elevated` (0.50f), `Strong` (0.55f), `Prominent` (0.60f), `IconTint` (0.75f)

### 3. Dimension Tokens (`Dimens.kt`)

All layout metrics are centralized in `Dimens.kt`:
- `Spacing`: `None` (0dp), `Hairline` (1dp), `ExtraExtraSmall` (2dp), `ExtraSmall` (4dp), `Five` (5dp), `Compact` (6dp), `Small` (8dp), `MediumSmall` (10dp), `SmallMedium` (12dp), `Fourteen` (14dp), `Medium` (16dp), `Large` (20dp), `ExtraLarge` (24dp), `ExtraExtraLarge` (32dp), `DialogPadding` (24dp), `PanelPadding` (8dp), `TabHorizontal` (20dp).
- `CornerRadius`: `None` (0dp), `ExtraSmall` (4dp), `Compact` (6dp), `Small` (6dp), `MediumSmall` (8dp), `Medium` (12dp), `ExtraLarge` (28dp), `Pill` (50dp), `Card` (16dp), `Dialog` (16dp), `Panel` (12dp).
- `ComponentSize`: `TopBarHeight` (44dp), `StatusBarHeight` (22dp), `StatusBarHeightAlt` (24dp), `BottomTabsHeight` (64dp), `BottomTabItemHeight` (56dp), `ButtonMinWidth` (64dp), `ButtonMinHeight` (40dp), `IconButtonSize` (40dp), `TopBarButtonSize` (28dp), `LogoBadgeSize` (28dp), `SearchCapsuleHeight` (28dp), `SearchCapsuleMinWidth` (160dp), `SearchCapsuleMaxWidth` (280dp), `SegmentedToggleHeight` (28dp), `SegmentedButtonSize` (24dp), `TopBarDividerHeight` (18dp), `WindowControlWidth` (36dp), `WindowControlHeight` (28dp), `PanelHeaderAction` (24dp), `TabBarHeight` (36dp), `TabItemHeight` (32dp), `OtpBoxSize` (48dp), `ToggleTrackWidth` (64dp), `ToggleTrackHeight` (28dp), `ToggleThumbWidth` (40dp), `ToggleThumbHeight` (24dp), `ToggleDragWidth` (20dp), `SliderTrackHeight` (6dp), `SliderThumbWidth` (40dp), `SliderThumbHeight` (24dp), `DragHandleWidth` (32dp), `DragHandleHeight` (4dp), `ProgressBarHeight` (4dp), `PerformanceMonitorWidth` (220dp), `MinPanelWidth` (180dp), `MaxPanelWidth` (600dp), `MinPanelHeight` (100dp), `MaxPanelHeight` (600dp), `ToggleButtonTopPadding` (48dp), `ToggleButtonEndPadding` (8dp), `ToggleButtonSpacing` (4dp), `DividerWidth` (8dp), `MaxRevealDp` (75dp), `VelocityThreshold` (100dp), `DefaultVelocityThreshold` (10dp), `SetupSidebarWidth` (240dp), `ActionCardHeight` (120dp), `EmptyStateBadgeSize` (56dp), `SpinnerSize` (28dp), `ShortcutHelpWidth` (320dp), `StatusDotSize` (8dp), `ActiveIndicatorWidth` (64dp), `ActiveIndicatorHeight` (32dp).
- `StrokeWidth`: `Hairline` (0.5dp), `Standard` (1dp), `Focused` (2dp), `Active` (3dp).
- `IconSize`: `Indicator` (6dp), `Small` (12dp), `WindowControl` (13dp), `Search` (14dp), `Segmented` (15dp), `Medium` (16dp), `Large` (24dp), `ExtraLarge` (28dp), `ActionCard` (32dp), `Avatar` (80dp).
- `Elevation`: `None` (0dp), `Raised` (4dp), `Panel` (8dp).

### 4. Motion & Animation Tokens (`Motion.kt`)

All animation durations, physics, and easing curves are centralized in `Motion.kt`:
- `MotionDuration`: `Fast` (150ms), `Moderate` (200ms), `Standard` (300ms).
- `MotionSpring`: `StiffnessInteractive` (300f), `DampingRatioMediumBouncy` (0.5f), `DampingRatioNoBouncy` (1.0f).
- `MotionEasing`: `Standard` (`CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)`), `EmphasizedDecelerate` (`CubicBezierEasing(0.4f, 0.4f, 0.17f, 0.9f)`).
- `MotionThreshold`: `Fine` (0.001f), `Offset` (0.5f).

### 5. Backdrop Tokens (`Backdrop.kt`)

All blur radii, lens refraction profiles, saturation, and brightness tokens are centralized in `Backdrop.kt`:
- `BackdropBlur`: `Subtle` (2dp), `Standard` (4dp), `Panel` (6dp), plus semantic aliases: `Button` (2dp), `TextField` (2dp), `SearchCapsule` (2dp), `TopBar` (4dp), `Minimap` (4dp), `Card` (8dp), `BottomTabs` (8dp), `Slider` (8dp), `Toggle` (8dp), `DialogDark` (8dp), `DialogLight` (16dp), `BottomSheet` (16dp), `Scrim` (16dp).
- `LensRefraction`: `Minimap` (2dp, 4dp), `TopBar` (4dp, 8dp), `Toggle` (5dp, 10dp), `TextField` (8dp, 16dp), `Slider` / `TabSpotlight` (10dp, 14dp), `Button` / `Panel` (12dp, 24dp), `Card` (16dp, 24dp), `Surface` (16dp, 32dp), `BottomTabs` (24dp, 24dp), `BottomSheet` (24dp, 40dp), `Dialog` (24dp, 48dp).
- `BackdropSaturation`: `Vibrant` (1.5f).
- `BackdropBrightness`: `DialogLight` (0.2f), `DialogDark` (0.0f).

### 6. Typography (`Type.kt`)

- `appTypography()`: Material 3 `Typography` configured with the unified Outfit font family across multiple weights (Black, Bold, SemiBold, Medium, Normal, Light, Thin, ExtraLight, ExtraBold).

### 7. Theme Setup (`Theme.kt`)

The unified entry point for styling is `LtiTheme`:
```kotlin
@Composable
fun LtiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    glassColors: GlassColorScheme? = null,
    effectsEnabled: Boolean = true,
    reducedMotion: Boolean = false,
    materialColorScheme: ColorScheme? = null,
    sharedBackdrop: Backdrop? = null,
    content: @Composable () -> Unit,
)
```

**Composition Locals and Theme Accessor:**
- `LocalGlassColors`: provides the active `GlassColorScheme` (default: `DarkGlassColorScheme`).
- `LocalGlassBackdrop`: provides the ambient shared `Backdrop?` (default: `null`).
- `LocalGlassEffectsEnabled`: controls whether GPU glass effects are rendered (default: `true`).
- `LocalReducedMotion`: in-memory flag for requesting reduced motion (default: `false`).
- `GlassTheme` object:
  - `GlassTheme.colors`: active `GlassColorScheme`
  - `GlassTheme.effectsEnabled`: active boolean flag
  - `GlassTheme.reducedMotion`: active reduced motion boolean flag
  - `GlassTheme.backdrop`: active shared `Backdrop?`
- `ProvideGlassColors(colors)`: helper to override glass color scheme for a composable subtree.

### 8. Design System Guardrails & Detekt Enforcement

To prevent design system drift and ensure strict token adherence, two automated enforcement layers are active:

1. **Gradle Guardrail Task (`verifyDesignSystemGuardrails`):**
   - Run via: `./gradlew verifyDesignSystemGuardrails`
   - Scans UI code outside `theme/` for raw `Color(0x...)` and `alpha = 0.x` literals.
   - Scans UI code outside `theme/` for raw `.dp` / `Dp(...)` dimension literals.
   - Verifies that raw `com.kashif_e.backdrop.effects.*` imports are not used outside `core:designsystem`.
   - Verifies that `feature/*`, `lti-shared`, `lti-desktop`, and `catalog` do not import a raw Material3
     component that has a Glass equivalent (e.g. `material3.Button`, `TextField`, `Switch`, `Slider`,
     `Card`, `IconButton`, `AlertDialog`, `NavigationBar`/`NavigationRail`, `Tab`/`TabRow`,
     `ModalBottomSheet`) — use the corresponding `Glass*` component from `core:designsystem`/`core:ui`
     instead. Primitives with no styled container of their own (`Text`, `Icon`, `MaterialTheme`) are
     not restricted.

2. **Custom Detekt Rule Set (`:detekt-rules`):**
   - Configured in `config/detekt/detekt.yml` under `lti-design-system:`.
   - Rules: `NoRawColorLiteral`, `NoRawAlphaLiteral`, `NoRawDimensionLiteral`, `NoRawMotionLiteral`, `NoBackdropEffectImport`.

## Backdrop System (Glass Morphism Effects)

The backdrop system provides GPU-accelerated glass morphism effects (blur, lens refraction, vibrancy) for UI components using Compose Skia APIs on desktop JVM.

### Core Architecture Components (`backdrop/` module)

**Core Files:**
- `DrawBackdropModifier.kt` - Modifier (`Modifier.drawBackdrop()`) that applies backdrop effects and draws custom surfaces
- `BackdropEffectScope.kt` - DSL scope for chaining Skia image filters and shader effects
- `LayerBackdrop.kt` - Captures GraphicsLayer content as a reusable backdrop via `Modifier.layerBackdrop()`
- `RuntimeShaderCache.kt` - Caches compiled SKSL RuntimeShaderBuilder instances

**Effect Files (`backdrop/effects/`):**
- `Blur.kt` - Gaussian blur using Skia `ImageFilter.makeBlur()`
- `ColorFilter.kt` - Color adjustments (vibrancy, opacity, brightness, contrast, saturation)
- `Lens.kt` - Glass refraction and chromatic aberration shaders
- `RenderEffect.kt` - Skia ImageFilter to Compose RenderEffect chaining utility

**Shader Files (`backdrop/`):**
- `Shaders.kt` - SKSL shader implementations for lens distortion and highlight effects

### Basic Usage Pattern

```kotlin
@Composable
fun MyGlassComponent() {
    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
        // 1. Capture background content
        WallpaperImage(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        )

        // 2. Apply backdrop with glass effect stack
        Box(
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { ContinuousRoundedRectangle(16.dp) },
                effects = {
                    vibrancy()
                    blur(4f.dp.toPx())
                    lens(16f.dp.toPx(), 32f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(GlassTheme.colors.glassSurface)
                }
            )
        )
    }
}
```

### Available Effects (`BackdropEffectScope`)

- `blur(radiusPx)` - Gaussian blur
- `vibrancy()` - Increases color saturation (1.5x)
- `lens(refractionHeightPx, refractionAmountPx, chromaticAberration, depthEffect)` - Glass refraction distortion
- `colorControls(brightness, contrast, saturation)` - Color balance adjustments
- `opacity(alpha)` - Transparency level
- `exposureAdjustment(ev)` - Exposure adjustment in EV stops
- `gammaAdjustment(power)` - Gamma correction
- `colorFilter(filter)` - Custom color filter application

### Highlight & Shadow Styles

- `Highlight.Default` - Directional gradient highlight (angle, falloff, intensity)
- `Highlight.Ambient` - 3D ambient occlusion style edge highlight
- `Highlight.Plain` - Solid color highlight with blend mode
- `Shadow(radius, color, offset)` - Drop shadow
- `InnerShadow(radius, alpha, offset)` - Inner shadow

### Nested Backdrops

```kotlin
val parentBackdrop = rememberLayerBackdrop()
val childBackdrop = rememberLayerBackdrop()

Box {
    Image(Modifier.layerBackdrop(parentBackdrop))

    // Parent surface exports backdrop for nested children
    Box(
        Modifier.drawBackdrop(
            backdrop = parentBackdrop,
            exportedBackdrop = childBackdrop
        )
    ) {
        // Child samples exported backdrop
        GlassCard(backdrop = childBackdrop) { ... }
    }
}
```

### Best Practices

✅ **DO:**
- Use `rememberLayerBackdrop()` to cache backdrop instances
- Apply `.layerBackdrop(backdrop)` to background content before sampling
- Use `CornerBasedShape` (ContinuousCapsule, ContinuousRoundedRectangle) for lens effects
- Chain effects in logical order: `vibrancy()` → `blur()` → `lens()`
- Share the ambient backdrop via `LtiTheme(sharedBackdrop = ...)` or `GlassTheme.backdrop`

❌ **DON'T:**
- Don't apply lens to non-CornerBasedShape
- Don't allocate backdrops inside recomposed blocks without `remember`
- Don't use heavy blur radii (> 16dp) on frequently animating or scrolling items

## Core Glass Components (`core:designsystem/component`)

All glass components are built on top of `Modifier.drawBackdrop()` and integrate with `GlassTheme`. Each component defines a tuned default effect stack tailored to its visual role.

### 1. `GlassSurface.kt`

Base glass surface component. Thin wrapper over `drawBackdrop()`.
- Default effect stack: `StandardGlassEffects` (`vibrancy()`, `blur(4.dp)`, `lens(16.dp, 32.dp)`).
- Parameters: `shape`, `backdrop`, `surfaceColor`, `tint`, `borderColor`, `effects`, `highlight`, `content`.

```kotlin
GlassSurface(
    modifier = Modifier.size(200.dp),
    shape = ContinuousRoundedRectangle(CornerRadius.Medium),
    effects = StandardGlassEffects,
) {
    Text("Base surface", color = GlassTheme.colors.onGlass)
}
```

### 2. `GlassCard.kt`

Container card with rounded corners and content padding.
- Default effect stack: `GlassCardEffects` (`vibrancy()`, `blur(8.dp)`, `lens(16.dp, 24.dp)`).
- Default shape: `ContinuousRoundedRectangle(CornerRadius.Card)`.
- Parameters: `shape`, `effects`, `backdrop`, `surfaceColor`, `tint`, `borderColor`, `contentPadding`, `content`.

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
- Default effect stack: `GlassPanelEffects` (`vibrancy()`, `blur(6.dp)`, `lens(12.dp, 24.dp)`).
- Default shape: `ContinuousRoundedRectangle(CornerRadius.Panel)`.
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

### 4. `GlassLayout.kt`

Complete 4-panel resizable IDE workspace layout with glass morphism, resizable dividers, and spring animation toggles.

```
┌────────────────────────────────────────────────────────┐
│ topBar() - GlassTopBar (40dp fixed)                    │
├────────────┬────────────────────────────┬──────────────┤
│            │                            │              │
│  LEFT      │   MAIN (Editor area)       │    RIGHT     │
│  Panel     ├────────────────────────────┤    Panel     │
│  (weight)  │   BOTTOM Panel (weight)    │   (weight)   │
│            │   [Optional/Collapsible]   │              │
├────────────┴────────────────────────────┴──────────────┤
│ statusBar() - GlassStatusBar (22dp fixed)              │
└────────────────────────────────────────────────────────┘
```

- State controller: `LtiLayoutState` (`rememberLtiLayoutState()`).
  - Controls panel visibility: `showLeft`, `showRight`, `showBottom`.
  - Controls panel weights: `leftWeight`, `rightWeight`, `bottomWeight`.
  - Methods: `toggleLeft()`, `toggleRight()`, `toggleBottom()`, `setLeftPanelVisible()`, `setRightPanelVisible()`, `setBottomPanelVisible()`.
- Composable parameters: `state`, `leftPanel`, `mainContent`, `bottomPanel`, `rightPanel`, `modifier`, `topBar`, `statusBar`, `showToggleButtons`, `appearance`.

### 5. `GlassTopBar.kt` & `GlassStatusBar.kt`

- **`GlassTopBar.kt`**:
  - 40dp height top bar with app logo gradient (`BrandColors.LogoGradientEnd`), layout panel toggle icons, search bar slot, notification/settings triggers, and desktop window controls (minimize, maximize/restore, close with `#E81123` hover).
  - Uses `LocalWindowControlActions` to trigger platform window actions.
- **`GlassStatusBar.kt`**:
  - 22dp height bottom bar displaying Git branch, file status, cursor position (line/column), indentation, encoding, and line endings (`LineEnding.LF`, `CRLF`, `CR`).
  - State controller: `GlassStatusBarState` (`rememberGlassStatusBarState()`).

## Interactive Components (`core:designsystem/component`)

### 1. `GlassButton.kt`

Interactive buttons with fluid press lighting (`InteractiveHighlight`), GPU backdrop sampling, and disabled state handling.
- Default effect stack: `GlassButtonEffects` (`vibrancy()`, `blur(2.dp)`, `lens(12.dp, 24.dp)`).
- Variants (`GlassButtonVariant`):
  - `GlassButtonVariant.Primary`: Accent tint fill, elevated surface.
  - `GlassButtonVariant.Secondary`: Secondary surface tint fill.
  - `GlassButtonVariant.Standard`: Standard glass surface with subtle tint.
  - `GlassButtonVariant.Text`: Transparent background without glass overhead.
- Composables:
  - `GlassButton(onClick, modifier, enabled, shape, variant, effects, backdrop, ...)`
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

Modal dialogs and loading indicators with backdrop blur and scrim.
- Effects helper: `glassDialogEffects(isLightTheme: Boolean)` (brightens and blurs heavier in light mode to maintain contrast).
- Composables:
  - `GlassDialog(onDismissRequest, confirmButton, modifier, title, dismissButton, shape, effects, backdrop, properties, content)`
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
- `GlassIcon(onClick, backdrop, modifier, isInteractive, tint, surfaceColor, icon)`

### 4. `GlassModifiers.kt`

Utility extension modifiers on `Modifier`:
- `Modifier.glassEffect(backdrop, shape, surfaceColor, tint, effects)`: applies full backdrop effect chain.
- `Modifier.glassBackground(color)`: lightweight glass surface color draw behind (skips shader compilation).
- `Modifier.glassBorder(color, width, shape)`: applies theme-aware glass border.
- `Modifier.glassInteractive(onClick, enabled, role)`: applies clickable semantics with glass styling.
- `Modifier.glassScrim(backdrop, blurRadius, surfaceColor)`: creates frosted modal scrim overlays.

```kotlin
Box(
    modifier = Modifier
        .size(240.dp)
        .glassEffect()
        .glassBorder(width = StrokeWidth.Standard)
) {
    Text("Custom Styled Glass Box", color = GlassTheme.colors.onGlass)
}
```

## Input & Navigation Components (`core:designsystem/component`)

### 1. `GlassTextField.kt`

Inputs with subtle glass blur, animated focus border transitions, and password/OTP specializations.
- Default effect stack: `GlassTextFieldEffects` (`vibrancy()`, `blur(2.dp)`, `lens(8.dp, 16.dp)`).
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

- `GlassSlider(value, onValueChange, valueRange, visibilityThreshold, backdrop, modifier)`: Drag-interpolated slider with velocity-squish thumb effect and track backdrop sampling.

### 3. `GlassToggle.kt`

- `GlassToggle(checked, onCheckedChange, modifier, state)`: Toggle switch with animated thumb offset and track highlighting (`GlassToggleState`, `rememberGlassToggleState`).

### 4. `GlassTabBar.kt` & `GlassBottomTabs.kt`

- `GlassTabBar(selectedTabIndex, onTabSelected, modifier, tabs)`: Horizontal tab bar with pill indicator and tab items (`GlassTab`).
- `GlassBottomTabs(...)`: Bottom navigation tabs with interactive spotlight positioning.

### 5. `GlassNavigation.kt`

- `GlassNavigationBar`, `GlassNavigationBarItem`: Material 3 glass bottom navigation bar.
- `GlassNavigationRail`, `GlassNavigationRailItem`: Material 3 glass vertical navigation rail.

## Modal & Developer Utilities

### 1. `GlassBottomSheet.kt`

Bottom sheet modal with drag-to-dismiss gesture handling and spring physics.
- Effect stack: `GlassBottomSheetEffects` (`colorControls(saturation = 1.5f)`, `blur(16.dp)`, `lens(24.dp, 40.dp, depthEffect = true)`).
- State controller: `GlassBottomSheetState` (`rememberGlassBottomSheetState()`)
  - State values (`GlassBottomSheetValue`): `Collapsed`, `Expanded`.
  - Methods: `expand()`, `collapse()`, `toggle()`.
- Composables:
  - `GlassBottomSheet(state, onDismissRequest, modifier, backdrop, effects, shape, scrimColor, dismissThreshold, content)`
  - `GlassBottomSheetScaffold(visible, onDismissRequest, modifier, title, headerContent, backdrop, effects, content)`
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
  - `activeBackdrops`: Number of active `Backdrop` instances
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

### Reduced Motion Support (`LocalReducedMotion`)

- In-memory composition local: `LocalReducedMotion = staticCompositionLocalOf { false }`.
- Accessor: `GlassTheme.reducedMotion` (`Theme.kt`).
- Theme configuration: pass `reducedMotion = true` to `LtiTheme(...)`.
- **Scope Notice**: `LocalReducedMotion` is an in-memory boolean flag. It does **NOT** perform OS-level preference auto-detection and has **NO** persistence layer. If OS auto-detection or persistent settings are needed in the future, they should be implemented as a dedicated platform bridge in `lti-desktop`.

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

And initialized at the app root in `lti-shared/src/commonMain/kotlin/org/ide/lti/shared/LtiSharedApp.kt`:

```kotlin
@Composable
fun LtiSharedApp(...) {
    val navController = rememberNavController()
    val backdrop = rememberLayerBackdrop()

    LtiTheme(sharedBackdrop = backdrop) {
        Box(Modifier.fillMaxSize()) {
            // Ambient aurora canvas captured by shared backdrop
            Canvas(Modifier.layerBackdrop(backdrop).fillMaxSize()) {
                // Background radial gradients...
            }

            RootNavGraph(
                navHostController = navController,
                startDestination = WORKSPACE_ROUTE,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
```

## Verified Component Matrix

| Component | File | Location | Interactive | Key Feature |
|-----------|------|----------|-------------|-------------|
| `GlassSurface` | `GlassSurface.kt` | `core:designsystem` | No | Base backdrop renderer with custom effect lambdas |
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

- [ ] **Design-System Guardrails**: Run `./gradlew verifyDesignSystemGuardrails` to verify no raw color (`Color(0x...)`), alpha, dimension (`.dp`), or motion literals bypass `theme/`.
- [ ] **Detekt Static Analysis**: Run `./gradlew detekt` to verify all custom `:detekt-rules` (`lti-design-system`) pass with 0 violations.
- [ ] **WCAG Contrast Ratios**: Run `./gradlew :core:designsystem:test` to ensure `GlassContrastTest` passes all AA contrast thresholds (>= 4.5:1 text, >= 3.0:1 UI/muted/focus ring).
- [ ] **Focus Ring Visibility**: Verify that focused interactive elements use `GlassTheme.colors.focusRing` or `glassBorderHighlight` for clear keyboard focus indication.
- [ ] **Reduced Motion Support**: Verify that components check `GlassTheme.reducedMotion` or `LocalReducedMotion` to disable or simplify intensive animations when requested.
- [ ] **Backdrop Sharing**: Ensure UI panels share the ambient backdrop from `LtiTheme` or `LocalGlassBackdrop` rather than creating redundant `LayerBackdrop` instances.
- [ ] **Performance Validation**: Enable `GlassPerformanceMonitor` in debug mode to verify target framerate (>= 55 FPS) and average frame time (< 18ms) during panel resizing and scrolling.
- [ ] **Manual Keyboard Navigation**: Verify Tab, Shift+Tab, Enter, and Space keys correctly navigate and activate interactive glass controls.

## Troubleshooting

**Problem: Low FPS or dropped frames during glass rendering**
- **Cause**: Too many independent `LayerBackdrop` instances or excessive blur radii.
- **Solution**: Share a single `Backdrop` instance across screens via `LtiTheme(sharedBackdrop = ...)`.
- **Solution**: Reduce blur radius on frequently recomposed items (e.g. use `blur(2.dp)` instead of `blur(16.dp)`).
- **Solution**: Use `Modifier.glassBackground()` instead of full `Modifier.glassEffect()` for secondary or non-critical panels.

**Problem: Glass effects are not rendering (flat colors only)**
- **Cause**: `effectsEnabled` is set to `false` or backdrop is not capturing background content.
- **Solution**: Ensure `LtiTheme(effectsEnabled = true)` is active.
- **Solution**: Ensure the background content has `.layerBackdrop(backdrop)` applied before glass components sample it.

**Problem: Text is hard to read on translucent glass surfaces**
- **Cause**: Direct hardcoded text colors or insufficiently opaque surface tint.
- **Solution**: Use `GlassTheme.colors.onGlass` for primary text and `GlassTheme.colors.onGlassSecondary` for secondary text.
- **Solution**: Verify text contrast against `glassSurface` by running `./gradlew :core:designsystem:test`.

**Problem: Guardrail or Detekt rule violation during build**
- **Cause**: Direct instantiation of `Color(0x...)`, `alpha = 0.5f`, or raw `.dp` inside UI files outside `theme/`.
- **Solution**: Import the corresponding centralized token from `org.ide.lti.core.designsystem.theme.*` (`GlassTheme.colors`, `AlphaTokens`, `Spacing`, `CornerRadius`, `ComponentSize`, `MotionDuration`, etc.).

## Credits

LtiRom Studio Glass Theme System is built on:
- Compose Multiplatform (JetBrains)
- Skia Graphics Engine (Google)
- Material 3 Design Tokens (Google)
- WCAG 2.1 Accessibility Guidelines (W3C)