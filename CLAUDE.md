- to memorize donk breake the project arch and moduarization, and system desgin

## Architecture: core:designsystem vs core:ui

Add to core:designsystem when:

  - Creating a new branded variant of Material3 component
  - Defining theme tokens (colors, typography, shapes)
  - Adding app icons
  - Building stateless, reusable UI primitives
  - Something every feature might need

  Examples:
  - Custom slider with app branding
  - Branded snackbar
  - App-specific icon set
  - Color palette updates
  - IDE color scheme (IDEColors.kt)
  - Top app bar, status bar, bottom panel components

Add to core:ui when:

  - Building complex widgets that combine multiple components
  - Creating stateful composables with ViewModels
  - Adding utility functions for navigation, effects, lifecycle
  - Implementing business logic in UI (like password validation)
  - Platform-specific UI utilities (share, file pickers)

  Examples:
  - User profile card (combines avatar, text, buttons)
  - Form validation indicators
  - Custom navigation transitions
  - ViewModel base classes
  - Chat input, message bubbles
  - Code editor, file tree components

## IDE Design System (Professional Dark Theme)

Lti uses a professional IDE design matching VS Code/JetBrains Fleet aesthetic.

### Color System (IDEColors.kt)

Main surfaces (VERY DARK - matching main.png exactly):
- `ideBackgroundDark` = #181818 (main background, top bar, status bar, editor)
- `idePanelDark` = #1F1F1F (side panels: left, right, bottom)
- `ideSurfaceElevated` = #252525 (elevated surfaces, dropdowns)
- `ideInputBackground` = #303030 (input fields, search boxes)

Borders and dividers:
- `ideBorderSubtle` = #2A2A2A (panel borders)
- `ideDivider` = #3A3A3A (resizable dividers)
- `ideBorderActive` = #007ACC (active/focused border - blue)

Text colors:
- `ideTextPrimary` = #CCCCCC (primary text)
- `ideTextSecondary` = #858585 (secondary text, placeholders)
- `ideTextMuted` = #6A6A6A (disabled, muted text)
- `ideTextWhite` = #FFFFFF (high emphasis text)

Terminal colors: terminalGreen, terminalCyan, terminalYellow, terminalRed, terminalWhite
Syntax highlighting: syntaxKeyword, syntaxString, syntaxProperty, syntaxNumber, syntaxComment
UI accents: ideBluePrimary (#4A9EFF), ideBlueHover, ideOrange, ideGreen, ideRed

### Key Components (core:designsystem/component)

**LtiTopAppBar.kt**
- Professional IDE-style top bar (40dp height)
- Features: logo, menu, 4 layout toggles, search, notifications, settings, window controls
- Flat design with subtle hover effects, no ripple
- Close button with red hover (#E81123)

**LtiStatusBar.kt**
- Bottom status bar (22dp height, 11sp text)
- Shows: file path, module, cursor position, encoding, line ending, file type
- Uses ideBackgroundDark and ideTextSecondary

**LtiBottomPanel.kt**
- Collapsible bottom panel for tools (terminal, problems, output)
- Empty state with terminal icon and "Open Tool" button
- Uses idePanelDark background

**LtiScaffoldDesktop.kt**
- Main desktop scaffold with 4-panel layout
- Structure: topBar (40dp) / leftPanel (240dp) | mainPanel | rightPanel (300dp) + optional bottomPanel (250dp) / statusBar (22dp)
- Resizable panels with 1dp dividers (ideDivider color)
- Main panel uses ideBackgroundDark (editor area)
- Side panels use idePanelDark

**LayoutMode enum**
- SINGLE_PANEL, TWO_PANEL, THREE_PANEL, FOUR_PANEL
- Controls workspace layout configuration

### Theme Usage

Enable IDE theme in app:
```kotlin
LtiTheme(useIDETheme = true) {
    // app content
}
```

The IDE theme converts IDEColorScheme to Material 3 ColorScheme for seamless integration.

### Panel Color Guidelines

When creating or updating panels:
- Use `ideBackgroundDark` for editor/main content areas
- Use `idePanelDark` for side panels (explorer, terminal, AI assistant)
- Use `ideTextPrimary` for primary text
- Use `ideTextSecondary` for secondary text, labels, placeholders
- Use `ideBluePrimary` for active states and highlights
- Use `ideDivider` for separators between UI sections

### Important Notes

- ALL UI components MUST be in core:designsystem or core:ui
- Feature modules MUST NOT define their own UI components directly
- Panel backgrounds MUST use IDE colors (not Material 3 colors)
- Maintain flat design aesthetic (no elevation, sharp corners, minimal borders)
- Use `@OptIn(ExperimentalComposeUiApi::class)` for hover effects with `onPointerEvent`

## Backdrop System (Glass Morphism Effects)

The backdrop system provides glass morphism effects (blur, lens refraction, vibrancy) for UI components. Fully implemented for Compose Desktop using Skia APIs.

### Architecture Components:

**Core Files:**
- `DrawBackdropModifier.kt` - Modifier that applies backdrop with effects
- `BackdropEffectScope.kt` - DSL scope for effect chaining
- `LayerBackdrop` - Captures GraphicsLayer content as backdrop
- `RuntimeShaderCache.kt` - Caches RuntimeShaderBuilder instances

**Effect Files (backdrop/effects/):**
- `Blur.kt` - Gaussian blur using ImageFilter.makeBlur()
- `ColorFilter.kt` - Color adjustments (vibrancy, opacity, brightness, contrast, saturation)
- `Lens.kt` - Glass refraction using RuntimeShaderBuilder + ImageFilter.makeRuntimeShader()
- `RenderEffect.kt` - Effect chaining utility

**Shader Files (backdrop/):**
- `Shaders.kt` - SKSL shader code for lens and highlight effects

### Basic Usage Pattern:

```kotlin
@Composable
fun MyComponent() {
    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
        // Capture background content
        Image(...)
            .layerBackdrop(backdrop)

        // Apply backdrop with effects
        Box(
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(16.dp) },
                effects = {
                    vibrancy()
                    blur(4f.dp.toPx())
                    lens(16f.dp.toPx(), 32f.dp.toPx())
                }
            )
        )
    }
}
```

### Available Effects:

- `blur(radius)` - Gaussian blur
- `vibrancy()` - Increase saturation by 1.5x
- `lens(refractionHeight, refractionAmount, depthEffect, chromaticAberration)` - Glass refraction
- `colorControls(brightness, contrast, saturation)` - Color adjustments
- `opacity(alpha)` - Transparency
- `exposureAdjustment(ev)` - Exposure in EV stops
- `gammaAdjustment(power)` - Gamma correction
- `colorFilter(filter)` - Custom color filter

### Highlight Styles:

- `Highlight.Default` - Directional gradient (angle, falloff, intensity)
- `Highlight.Ambient` - 3D ambient occlusion style
- `Highlight.Plain` - Solid color with blend mode

### Shadow Styles:

- `Shadow(radius, color, offset)` - Drop shadow
- `InnerShadow(radius, alpha, offset)` - Inner shadow

### Liquid Components (core:designsystem/component):

Pre-built components using backdrop system:
- `LiquidButton` - Button with glass effect
- `LiquidIcon` - Icon button with glass effect
- `LiquidToggle` - Toggle switch with glass effect
- `LiquidSlider` - Slider with glass effect
- `LiquidTopAppBar` - Top app bar with glass effect
- `BackdropDemoScaffold` - Demo scaffold for testing effects

### Advanced: Nested Backdrops

```kotlin
val parentBackdrop = rememberLayerBackdrop()
val childBackdrop = rememberLayerBackdrop()

Box {
    Image(...).layerBackdrop(parentBackdrop)

    // Parent panel exports backdrop
    Box(
        Modifier.drawBackdrop(
            backdrop = parentBackdrop,
            exportedBackdrop = childBackdrop  // Export for children
        )
    ) {
        // Child uses parent's exported backdrop
        LiquidSlider(backdrop = childBackdrop)
    }
}
```

### Technical Implementation:

**Effect Chain:**
1. Effects are declared in `BackdropEffectScope`
2. Each effect modifies `renderEffect` property (Compose RenderEffect)
3. Skia ImageFilter converted to RenderEffect via `.asComposeRenderEffect()`
4. Final `renderEffect` applied to GraphicsLayer (line 307 in DrawBackdropModifier.kt)

**Shader Uniforms:**
- Lens effect uses `RuntimeShaderBuilder.uniform()` to pass parameters
- Highlight shaders pack uniforms as ByteBuffer with native byte order
- Shaders written in SKSL (Skia Shading Language)

### Best Practices:

✅ **DO:**
- Use `rememberLayerBackdrop()` to cache backdrop instances
- Apply `.layerBackdrop(backdrop)` to content you want to capture
- Use `CornerBasedShape` (RoundedCornerShape, ContinuousCapsule) for lens effects
- Chain effects in logical order: vibrancy → blur → lens
- Use `exportedBackdrop` for nested glass UI

❌ **DON'T:**
- Don't apply lens to non-CornerBasedShape (throws UnsupportedOperationException)
- Don't create backdrops without remembering them
- Don't use heavy effects without performance consideration
- Don't modify backdrop system files - it's fully implemented and working

### Component Examples:

See `GlassPlaygroundContent.kt` for interactive demo with adjustable parameters.
See `LiquidButton.kt`, `LiquidIcon.kt`, `LiquidSlider.kt` for production component examples.

## Lti Liquid Glass Theme System

Professional glass morphism theme system following SOLID principles. Integrates the backdrop system with Material3 theming for a complete, production-ready glass UI solution.

### Architecture (SOLID Principles)

The glass theme system is designed with clean architecture principles:

**Single Responsibility:**
- `GlassPreset` - Encapsulates effect parameters only
- `GlassColorScheme` - Manages glass-specific colors only
- `GlassThemeConfig` - Combines preset and colors with performance settings

**Open/Closed:**
- Four built-in presets (Subtle, Standard, Intense, None)
- Custom presets via data class
- Extensible via builder pattern and DSL

**Interface Segregation:**
- `IGlassEffectConfig` - Effect parameters interface
- `IGlassColorConfig` - Color configuration interface
- `IGlassPerformanceConfig` - Performance settings interface

**Dependency Inversion:**
- Components depend on `LocalGlassTheme` composition local
- `GlassTheme` object provides typed access
- Backdrop injection via `LocalGlassBackdrop`

### File Structure

```
core/designsystem/theme/glass/
├── GlassPreset.kt              - Effect preset configurations
├── GlassColorScheme.kt         - Glass-specific color tokens
├── GlassThemeConfig.kt         - Complete theme configuration + builder
├── LocalGlassTheme.kt          - Composition locals + providers
└── LtiLiquidGlassTheme.kt   - Main theme wrapper

core/designsystem/component/glass/
├── GlassSurface.kt             - Base glass surface component
├── GlassCard.kt                - Glass card with padding
└── GlassPanel.kt               - Glass panel for sidebars
```

### Basic Usage

**Simple setup with defaults:**
```kotlin
@Composable
fun App() {
    LtiLiquidGlassTheme {
        GlassCard {
            Text("Hello, Glass!", color = GlassTheme.colors.onGlass)
        }
    }
}
```

**With custom preset:**
```kotlin
LtiLiquidGlassTheme(
    config = GlassThemeConfig.quality()
) {
    GlassCard {
        Text("High quality glass effects")
    }
}
```

**Performance-optimized:**
```kotlin
LtiLiquidGlassTheme(
    config = GlassThemeConfig.performance()
) {
    // Minimal effects, maximum caching
    GlassCard { ... }
}
```

**Accessibility mode (no effects):**
```kotlin
LtiLiquidGlassTheme(
    config = GlassThemeConfig.accessibility()
) {
    // Solid colors, no transparency
    GlassCard { ... }
}
```

### Glass Presets

Four built-in presets optimize for different use cases:

**GlassPreset.Subtle**
- 2dp blur, minimal refraction
- No chromatic aberration
- Best for: Buttons, controls, subtle overlays
- Performance: Excellent

**GlassPreset.Standard** (default)
- 4dp blur, moderate refraction
- Depth effect enabled
- Best for: Panels, dialogs, main surfaces
- Performance: Good

**GlassPreset.Intense**
- 8dp blur, strong refraction
- Chromatic aberration + depth effect
- Best for: Hero sections, featured content
- Performance: Moderate (use sparingly)

**GlassPreset.None**
- All effects disabled
- Best for: Accessibility, reduced motion
- Performance: Excellent (no rendering overhead)

**Custom preset:**
```kotlin
val myPreset = GlassPreset(
    blurRadius = 6.dp,
    refractionHeightFraction = 0.25f,
    refractionAmountFraction = 0.25f,
    vibrancyEnabled = true,
    chromaticAberration = false,
    depthEffect = true,
    highlightIntensity = 0.4f
)
```

### Glass Color Schemes

Three built-in color schemes for different aesthetics:

**GlassColorScheme.defaultDark()** - Professional dark glass
- Semi-transparent dark surfaces (~30% opacity)
- Subtle blue tints
- High contrast text colors
- Best for: Dark mode applications

**GlassColorScheme.defaultLight()** - Light glass
- Semi-transparent light surfaces
- Subtle blue tints
- Dark text on glass
- Best for: Light mode applications

**GlassColorScheme.vibrant()** - Vibrant glass with stronger colors
- Purple-blue surfaces
- Teal accents
- Stronger tints (~20% opacity)
- Best for: Hero sections, featured UI

### Glass Components

**GlassSurface** - Base component with full customization
```kotlin
GlassSurface(
    modifier = Modifier.size(200.dp),
    shape = ContinuousRoundedRectangle(16.dp),
    preset = GlassPreset.Standard,
    surfaceColor = GlassTheme.colors.glassSurface,
    tint = GlassTheme.colors.glassTint
) {
    Text("Content")
}
```

**GlassCard** - Card with rounded corners and padding
```kotlin
GlassCard(
    modifier = Modifier.fillMaxWidth(),
    contentPadding = 24.dp
) {
    Column {
        Text("Title", style = MaterialTheme.typography.titleLarge)
        Text("Description")
    }
}
```

**GlassPanel** - Full-height panel for sidebars
```kotlin
GlassPanel(
    width = 240.dp,
    shape = RectangleShape
) {
    Column {
        Text("Menu Item 1")
        Text("Menu Item 2")
    }
}
```

### Theme Configuration

**Using builder DSL:**
```kotlin
val config = glassThemeConfig {
    withPreset(GlassPreset.Intense)
    withColors(GlassColorScheme.vibrant())
    withMaxShaderCacheSize(15)
    withFrameBudget(33.33f)  // ~30 FPS
}

LtiLiquidGlassTheme(config = config) {
    // App content
}
```

**Built-in configurations:**
- `GlassThemeConfig.default()` - Balanced quality/performance
- `GlassThemeConfig.performance()` - Maximum performance
- `GlassThemeConfig.quality()` - Maximum visual fidelity
- `GlassThemeConfig.accessibility()` - No effects, solid colors

### Accessing Theme Values

**Using GlassTheme object:**
```kotlin
@Composable
fun MyComponent() {
    val glassColors = GlassTheme.colors
    val glassPreset = GlassTheme.preset
    val effectsEnabled = GlassTheme.effectsEnabled

    Box(Modifier.background(glassColors.glassSurface)) {
        Text("Content", color = glassColors.onGlass)
    }
}
```

**Direct composition local access:**
```kotlin
val config = LocalGlassTheme.current
val backdrop = LocalGlassBackdrop.current
```

### Providing Custom Values

**Override config for subtree:**
```kotlin
ProvideGlassTheme(
    config = GlassThemeConfig.quality()
) {
    // Only this section uses quality preset
    GlassCard { Text("Premium") }
}
```

**Override only preset:**
```kotlin
ProvideGlassPreset(GlassPreset.Intense) {
    GlassCard { Text("Hero Section") }
}
```

**Override only colors:**
```kotlin
ProvideGlassColors(GlassColorScheme.vibrant()) {
    GlassPanel { ... }
}
```

### Performance Optimizations

The glass theme system includes several performance optimizations:

1. **Shader Caching**: RuntimeShaderBuilder instances cached (default: 10)
2. **Backdrop Sharing**: Single backdrop shared across components
3. **Smart Recomposition**: Uses `remember` and `derivedStateOf`
4. **Early Returns**: Skips rendering if no effects active
5. **Effect Batching**: Multiple effects applied in single pass

**Performance configuration:**
```kotlin
val config = GlassThemeConfig(
    preset = GlassPreset.Subtle,
    colors = GlassColorScheme.defaultDark(),
    maxShaderCacheSize = 15,      // More caching
    enableBackdropCaching = true,  // Cache between frames
    frameBudgetMs = 16.67f         // Target 60 FPS
)
```

### Integration with Existing Themes

**Combining with LtiTheme:**
```kotlin
LtiLiquidGlassTheme(
    materialColorScheme = darkScheme  // From LtiTheme
) {
    // Glass theme wraps Material theme
    // Both are available to components
    Box(Modifier.background(MaterialTheme.colorScheme.background)) {
        GlassCard {
            Text(
                "Hybrid UI",
                color = GlassTheme.colors.onGlass,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
```

### Best Practices

✅ **DO:**
- Use built-in presets for consistency
- Provide shared backdrop via `LtiLiquidGlassTheme`
- Override configs locally for specific sections (hero, modals)
- Use `GlassPreset.None` or `accessibility()` for reduced motion
- Test performance on target devices
- Use `GlassTheme.colors.onGlass` for text on glass surfaces

❌ **DON'T:**
- Don't use `GlassPreset.Intense` everywhere (performance impact)
- Don't create multiple backdrops when one can be shared
- Don't ignore accessibility - provide fallback with no effects
- Don't mix glass and non-glass components inconsistently
- Don't hardcode glass colors - use `GlassTheme.colors`

### Example: Complete App Setup

```kotlin
@Composable
fun LtiApp() {
    LtiLiquidGlassTheme(
        config = GlassThemeConfig.default(),
        materialColorScheme = darkScheme
    ) {
        Scaffold { padding ->
            Row(Modifier.padding(padding)) {
                // Sidebar with glass
                GlassPanel(width = 240.dp) {
                    NavigationMenu()
                }

                // Main content
                Column(Modifier.weight(1f)) {
                    // Hero section with intense glass
                    ProvideGlassPreset(GlassPreset.Intense) {
                        GlassCard {
                            HeroContent()
                        }
                    }

                    // Standard content with default glass
                    GlassCard {
                        MainContent()
                    }
                }

                // Right panel with subtle glass
                ProvideGlassPreset(GlassPreset.Subtle) {
                    GlassPanel(width = 300.dp) {
                        ToolsPanel()
                    }
                }
            }
        }
    }
}
```

### Debugging

Check if effects are active:
```kotlin
val effectsEnabled = GlassTheme.effectsEnabled
val hasEffects = GlassTheme.preset.hasEffects()

if (!effectsEnabled || !hasEffects) {
    Log.d("Glass", "Effects disabled or preset has no effects")
}
```

Check backdrop availability:
```kotlin
val backdrop = GlassTheme.backdrop
if (backdrop == null) {
    Log.d("Glass", "No shared backdrop - component will create own")
}
```

## Glass Theme System - Phase 2 Components

Extended component library built on top of the core glass theme system.

### Interactive Components

**GlassButton.kt** - Production-ready interactive button
```kotlin
// Standard button
GlassButton(onClick = { }) {
    Text("Click Me")
}

// Primary button variant
GlassPrimaryButton(onClick = { }) {
    Icon(Icons.Default.Star, contentDescription = null)
    Spacer(Modifier.width(8.dp))
    Text("Primary Action")
}

// Text-only button
GlassTextButton(onClick = { }) {
    Text("Cancel")
}

// Custom variant
GlassButton(
    onClick = { },
    variant = GlassButtonVariant.Secondary,
    preset = GlassPreset.Intense,
    enabled = false
) {
    Text("Disabled")
}
```

**Button Variants:**
- `GlassButtonVariant.Primary` - Accent tint, elevated surface
- `GlassButtonVariant.Secondary` - Subtle tint
- `GlassButtonVariant.Standard` - Default styling (default)
- `GlassButtonVariant.Text` - No glass background

**Features:**
- Interactive press animations via InteractiveHighlight
- Automatic theme integration
- Disabled state support
- Icon + text layouts
- Customizable padding and sizing

### Dialog Components

**GlassDialog.kt** - Modal dialogs with glass scrim

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
        Text("Are you sure you want to continue?")
    }
}
```

**Convenience Functions:**

```kotlin
// Simple alert
GlassAlertDialog(
    onDismissRequest = { },
    title = "Success",
    message = "Changes saved successfully.",
    confirmText = "OK",
    onConfirm = { }
)

// Confirmation with destructive action
GlassConfirmDialog(
    onDismissRequest = { },
    title = "Delete Item",
    message = "This cannot be undone.",
    onConfirm = { /* delete */ },
    isDestructive = true
)
```

### Utility Modifiers (GlassModifiers.kt)

**glassEffect()** - Apply full glass effect stack
```kotlin
Box(
    Modifier
        .size(200.dp)
        .glassEffect()  // Uses GlassTheme.preset
) {
    Text("Glass Content")
}

// With custom preset
Box(Modifier.glassEffect(preset = GlassPreset.Intense)) {
    Text("Intense Glass")
}
```

**glassBackground()** - Simple glass color (no effects)
```kotlin
Box(
    Modifier
        .glassBackground()  // Performance-friendly
) {
    Text("Simple Glass")
}
```

**glassBorder()** - Add glass-aware border
```kotlin
Box(
    Modifier
        .glassBackground()
        .glassBorder(width = 2.dp)
) {
    Text("Glass with Border")
}
```

**glassInteractive()** - Clickable with glass styling
```kotlin
Box(
    Modifier
        .glassEffect()
        .glassInteractive(onClick = { })
) {
    Text("Clickable Glass")
}
```

**glassScrim()** - Dialog/overlay background
```kotlin
Box(
    Modifier
        .fillMaxSize()
        .glassScrim(blurRadius = 16.dp)
) {
    // Modal content
}
```

### Theme Showcase

**GlassThemeShowcase.kt** - Interactive demo and testing
```kotlin
@Composable
fun App() {
    GlassThemeShowcase()
}
```

Features:
- Live preset switching (Subtle, Standard, Intense, None)
- Color scheme switching (Dark, Light, Vibrant)
- Component gallery with all glass components
- Preset comparison view
- Before/after demonstrations
- Code examples

Use for:
- Visual testing of glass effects
- Generating documentation screenshots
- Debugging rendering issues
- Demonstrating capabilities to stakeholders

### Component Summary Table

| Component | Purpose | Interactive | Best Preset |
|-----------|---------|-------------|-------------|
| GlassButton | Primary actions | Yes | Standard |
| GlassPrimaryButton | Emphasized actions | Yes | Standard |
| GlassTextButton | Secondary actions | Yes | None |
| GlassDialog | Modal dialogs | No | Standard |
| GlassAlertDialog | Simple alerts | No | Standard |
| GlassConfirmDialog | Confirmations | No | Standard |
| GlassCard | Content containers | No | Standard |
| GlassSurface | Custom surfaces | No | Any |
| GlassPanel | Sidebars, navigation | No | Subtle |

### Performance Guidelines

**Component Selection:**
- Use `GlassTextButton` for high-frequency actions (no glass overhead)
- Use `GlassButton` with `Subtle` preset for toolbars
- Use `Standard` preset for main UI surfaces
- Reserve `Intense` preset for hero sections only

**Backdrop Management:**
- Share single backdrop via `LtiLiquidGlassTheme`
- Don't create backdrops in frequently recomposed components
- Use `glassBackground()` when effects not needed

**Dialog Best Practices:**
- Dialogs automatically use optimized scrim blur
- Dialog content uses `Standard` preset by default
- Don't nest multiple glass dialogs

### Migration from Liquid Components

The new glass components are fully theme-aware replacements for the original Liquid components:

**Before (Liquid):**
```kotlin
val backdrop = rememberLayerBackdrop()

LiquidButton(
    onClick = { },
    backdrop = backdrop,
    tint = Color(0xFF2979FF)
) {
    Text("Button")
}
```

**After (Glass):**
```kotlin
GlassButton(onClick = { }) {
    Text("Button")
}
// Backdrop and colors automatically managed by theme
```

**Benefits:**
- No manual backdrop management
- Centralized styling via theme
- Consistent behavior across components
- Easier customization
- Better performance (shared backdrop)

### Advanced Usage Patterns

**Nested glass with preset override:**
```kotlin
GlassCard {  // Uses theme preset
    Column {
        Text("Main Content")

        ProvideGlassPreset(GlassPreset.Intense) {
            GlassButton(onClick = { }) {
                Text("Emphasized Action")
            }
        }
    }
}
```

**Mixed glass and solid components:**
```kotlin
Column {
    GlassCard { /* Glass card */ }

    Spacer(Modifier.height(16.dp))

    // Solid Material3 card for contrast
    Card {
        Text("Solid Card")
    }
}
```

**Custom modifier composition:**
```kotlin
fun Modifier.customGlassCard() = this
    .glassEffect(preset = GlassPreset.Standard)
    .glassBorder(width = 1.dp)
    .padding(16.dp)

Box(Modifier.customGlassCard()) {
    Text("Custom Style")
}
```

## Glass Theme System - Phase 3 Advanced Components

Additional professional components and utilities for complete glass UI development.

### Input Components

**GlassTextField.kt** - Text input with glass styling

```kotlin
var text by remember { mutableStateOf("") }

GlassTextField(
    value = text,
    onValueChange = { text = it },
    label = "Username",
    placeholder = "Enter username",
    leadingIcon = {
        Icon(Icons.Default.Person, contentDescription = null)
    }
)
```

**Features:**
- Uses `GlassPreset.Subtle` for minimal distraction
- Animated border on focus (2dp when focused, 1dp normal)
- Border color animation (blue when focused, red on error)
- Label, placeholder, helper text support
- Error state with red border and error message
- Leading/trailing icon slots
- Password visibility toggle via `GlassPasswordField`

**With error state:**
```kotlin
var email by remember { mutableStateOf("") }
val isError = email.isNotEmpty() && !email.contains("@")

GlassTextField(
    value = email,
    onValueChange = { email = it },
    label = "Email",
    errorText = if (isError) "Invalid email address" else null,
    helperText = if (!isError) "We'll never share your email" else null
)
```

**Password field:**
```kotlin
var password by remember { mutableStateOf("") }

GlassPasswordField(
    value = password,
    onValueChange = { password = it },
    label = "Password"
)
// Automatically includes visibility toggle icon
```

### Icon Components

**GlassIconButton.kt** - Optimized icon button for toolbars

```kotlin
// Simple icon button
GlassIconButton(onClick = { }) {
    Icon(Icons.Default.Settings, contentDescription = "Settings")
}

// With custom size and preset
GlassIconButton(
    onClick = { },
    size = 48.dp,
    preset = GlassPreset.Standard
) {
    Icon(Icons.Default.Favorite, contentDescription = null)
}
```

**Toggle icon button:**
```kotlin
var isFavorite by remember { mutableStateOf(false) }

GlassIconToggleButton(
    selected = isFavorite,
    onSelectedChange = { isFavorite = it }
) {
    Icon(
        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
        contentDescription = "Toggle favorite"
    )
}
```

**Features:**
- Circular shape by default (ContinuousCapsule)
- Compact 40dp size (customizable)
- Selected state with accent tint
- Interactive press animations
- Optimized for toolbars (uses Subtle preset)
- Toggle variant for on/off states

**Toolbar usage:**
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    GlassIconButton(onClick = { }) {
        Icon(Icons.Default.Menu, null)
    }
    GlassIconButton(onClick = { }) {
        Icon(Icons.Default.Search, null)
    }
    GlassIconButton(onClick = { }) {
        Icon(Icons.Default.Settings, null)
    }
}
```

### Animation System

**GlassAnimations.kt** - Smooth transitions and effects

**Preset transitions:**
```kotlin
var targetPreset by remember { mutableStateOf(GlassPreset.Standard) }

// Automatic animation between presets
val animatedPreset = rememberAnimatedGlassPreset(targetPreset)

GlassSurface(preset = animatedPreset) {
    Text("Smoothly animated")
}

// When targetPreset changes, glass effects smoothly morph
Button(onClick = { targetPreset = GlassPreset.Intense }) {
    Text("Switch to Intense")
}
```

**Manual preset animation:**
```kotlin
val animatedPreset = animateGlassPreset(
    from = GlassPreset.Subtle,
    to = GlassPreset.Intense,
    durationMs = 300
)
```

**Enter/Exit animations:**
```kotlin
var visible by remember { mutableStateOf(false) }

GlassSurface(
    modifier = Modifier.glassEnterAnimation(visible)
) {
    Text("Fades in with scale")
}

// Or exit animation
GlassSurface(
    modifier = Modifier.glassExitAnimation(visible)
) {
    Text("Fades out with scale")
}
```

**Hover animations:**
```kotlin
val hoverState = rememberGlassHoverState()
val scope = rememberCoroutineScope()

Box(
    modifier = Modifier
        .onPointerEvent(PointerEventType.Enter) {
            scope.launch { hoverState.animateHover(true) }
        }
        .onPointerEvent(PointerEventType.Exit) {
            scope.launch { hoverState.animateHover(false) }
        }
        .graphicsLayer {
            scaleX = hoverState.scale  // 1.05x on hover
            scaleY = hoverState.scale
        }
) {
    GlassSurface(
        preset = GlassPreset.Standard.copy(
            blurRadius = GlassPreset.Standard.blurRadius * hoverState.blurMultiplier
        )
    ) {
        Text("Hover Me")
    }
}
```

**Property animations:**
```kotlin
// Animate blur radius
val blurRadius = animateBlurRadius(
    targetBlurRadius = if (isActive) 8.dp else 4.dp
)

// Animate opacity
val alpha = animateGlassAlpha(
    targetAlpha = if (isVisible) 1f else 0f
)
```

### Animation Features

**Available Animations:**
- `animateGlassPreset()` - Morph between two presets
- `rememberAnimatedGlassPreset()` - Auto-animate preset changes
- `glassEnterAnimation()` - Fade in + scale modifier
- `glassExitAnimation()` - Fade out + scale modifier
- `rememberGlassHoverState()` - Hover scale + blur animations
- `animateBlurRadius()` - Smooth blur transitions
- `animateGlassAlpha()` - Opacity transitions

**Animation Characteristics:**
- Uses spring physics for natural motion
- FastOutSlowInEasing for property transitions
- Medium bounce for interactive animations
- Configurable durations (default: 250-300ms)
- Smooth interpolation of all preset properties

### Updated Component Summary

| Component | Use Case | Preset | New in Phase 3 |
|-----------|----------|--------|----------------|
| GlassTextField | Text input | Subtle | ✅ Yes |
| GlassPasswordField | Password input | Subtle | ✅ Yes |
| GlassIconButton | Toolbar icons | Subtle | ✅ Yes |
| GlassIconToggleButton | Toggle states | Subtle | ✅ Yes |
| GlassButton | Primary actions | Standard | Phase 2 |
| GlassDialog | Modal dialogs | Standard | Phase 2 |
| GlassCard | Content containers | Standard | Phase 1 |
| GlassSurface | Custom surfaces | Any | Phase 1 |
| GlassPanel | Sidebars | Subtle | Phase 1 |

### Form Example with Phase 3 Components

```kotlin
@Composable
fun GlassLoginForm() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.width(400.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Sign In",
                style = MaterialTheme.typography.headlineMedium,
                color = GlassTheme.colors.onGlass
            )

            GlassTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                placeholder = "you@example.com",
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null)
                },
                errorText = if (email.isNotEmpty() && !email.contains("@")) {
                    "Invalid email"
                } else null
            )

            GlassPasswordField(
                value = password,
                onValueChange = { password = it }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassIconToggleButton(
                    selected = rememberMe,
                    onSelectedChange = { rememberMe = it }
                ) {
                    Icon(
                        if (rememberMe) Icons.Default.CheckBox
                        else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = null
                    )
                }
                Text("Remember me", color = GlassTheme.colors.onGlassSecondary)
            }

            GlassPrimaryButton(
                onClick = { /* sign in */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign In")
            }
        }
    }
}
```

### Toolbar Example with Glass Icons

```kotlin
@Composable
fun GlassToolbar() {
    GlassCard(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left icons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GlassIconButton(onClick = { }) {
                    Icon(Icons.Default.Menu, "Menu")
                }
                GlassIconButton(onClick = { }) {
                    Icon(Icons.Default.Home, "Home")
                }
            }

            // Center
            Text("Lti", color = GlassTheme.colors.onGlass)

            // Right icons with toggle
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                var searchActive by remember { mutableStateOf(false) }

                GlassIconToggleButton(
                    selected = searchActive,
                    onSelectedChange = { searchActive = it }
                ) {
                    Icon(Icons.Default.Search, "Search")
                }

                GlassIconButton(onClick = { }) {
                    Icon(Icons.Default.Settings, "Settings")
                }
            }
        }
    }
}
```

### Performance Best Practices - Phase 3

**TextField Usage:**
- GlassTextField uses Subtle preset by default (minimal overhead)
- Focus animations are lightweight (border only)
- Use `readOnly = true` for display-only fields (skips input handling)

**IconButton Usage:**
- GlassIconButton optimized for frequent use (toolbars, lists)
- Circular shape reduces rendering complexity vs rounded rectangles
- Use `GlassTextButton` for text-only toolbar items (no glass effects)

**Animation Performance:**
- Preset transitions interpolate properties smoothly
- Use `rememberAnimatedGlassPreset()` to cache animation state
- Hover animations only apply to interactive surfaces
- Enter/exit animations use `graphicsLayer` (GPU accelerated)

**When to Skip Animations:**
- List items with many icons (use static GlassIconButton)
- Rapidly changing states (cache preset, don't animate)
- Low-end devices (use GlassThemeConfig.performance())

## Glass Theme System - Phase 4 Production Features

Phase 4 completes the glass theme system with production-ready features including accessibility support, advanced components, performance monitoring, and comprehensive testing utilities.

### Accessibility System

**GlassAccessibility.kt** - WCAG-compliant accessibility support

**Accessibility Preferences:**
```kotlin
@Immutable
data class GlassAccessibilityPreferences(
    val prefersReducedMotion: Boolean = false,
    val prefersHighContrast: Boolean = false,
    val prefersReducedTransparency: Boolean = false,
    val screenReaderActive: Boolean = false,
    val minimumContrastRatio: Float = 4.5f,
    val forceKeyboardNavigation: Boolean = false
)

// Built-in presets
GlassAccessibilityPreferences.Default
GlassAccessibilityPreferences.HighAccessibility  // WCAG AAA
```

**Auto-configuration:**
```kotlin
// Create accessible config from preferences
val accessibleConfig = GlassAccessibility.createAccessibleConfig(
    preferences = userPreferences,
    baseConfig = GlassThemeConfig.default()
)

// Automatic adjustments:
// - Reduced motion → Subtle or None preset
// - High contrast → Disable effects, increase opacity
// - Reduced transparency → 95% opaque surfaces
```

**Contrast ratio validation:**
```kotlin
// Calculate contrast ratio (WCAG)
val ratio = GlassAccessibility.calculateContrastRatio(
    foreground = textColor,
    background = surfaceColor
)

// Check if meets requirement
val passes = GlassAccessibility.meetsContrastRequirement(
    foreground = textColor,
    background = surfaceColor,
    minimumRatio = 4.5f  // WCAG AA
)

// Suggest accessible color
val adjustedColor = GlassAccessibility.suggestAccessibleForeground(
    foreground = originalColor,
    background = surfaceColor,
    minimumRatio = 7.0f  // WCAG AAA
)
```

**Platform detection (Desktop):**
```kotlin
// Automatic system preference detection
AutoDetectAccessibility {
    LtiAccessibleGlassTheme {
        // Automatically adapts to:
        // - macOS: Reduce Motion, Increase Contrast, Reduce Transparency, VoiceOver
        // - Windows: Show Animations, High Contrast, Transparency, Narrator/NVDA
        // - Linux: Desktop environment specific settings
    }
}
```

**Manual configuration:**
```kotlin
val preferences = GlassAccessibilityPreferences(
    prefersReducedMotion = true,
    prefersHighContrast = false,
    prefersReducedTransparency = false
)

ProvideGlassAccessibility(preferences = preferences) {
    LtiAccessibleGlassTheme {
        // Theme automatically respects preferences
        GlassCard { ... }  // Will use Subtle preset
    }
}
```

### WorkspaceScreenWithGlass - Real Integration Example

**Complete IDE-style workspace** demonstrating production usage.

**Architecture:**
- Top toolbar with glass (Subtle preset)
- Left sidebar: File explorer with glass (Standard preset)
- Center: Editor area (solid background for readability)
- Right sidebar: AI assistant with glass (Standard preset)
- Bottom panel: Terminal with glass (Standard preset)
- Status bar with glass (Subtle preset)

**Usage:**
```kotlin
@Composable
fun App() {
    LtiLiquidGlassTheme {
        WorkspaceScreenWithGlass()
    }
}
```

**Key features:**
- Shared backdrop for optimal performance
- Preset override per section (toolbar uses Subtle, panels use Standard)
- Editor uses solid background (no glass for better text readability)
- Interactive glass components throughout (buttons, icons, text fields)
- File tree with glass interactive items
- Chat interface with glass message bubbles

**Best practices demonstrated:**
- Don't use glass on main editor/content areas (readability)
- Use Subtle preset for toolbars and status bars
- Use Standard preset for panels and sidebars
- Share single backdrop across entire workspace
- Override presets locally for specific sections

### GlassBottomSheet - Drag-to-Dismiss Modal

**Interactive bottom sheet with glass morphism.**

**Basic usage:**
```kotlin
var showSheet by remember { mutableStateOf(false) }

GlassBottomSheetScaffold(
    visible = showSheet,
    onDismissRequest = { showSheet = false },
    title = "Settings"
) {
    Column(Modifier.padding(16.dp)) {
        Text("Sheet content here")
        GlassPrimaryButton(onClick = { showSheet = false }) {
            Text("Close")
        }
    }
}
```

**Advanced with state control:**
```kotlin
val state = rememberGlassBottomSheetState(
    initialValue = GlassBottomSheetValue.Collapsed
)
val scope = rememberCoroutineScope()

GlassButton(onClick = { scope.launch { state.expand() } }) {
    Text("Show Sheet")
}

GlassBottomSheet(
    state = state,
    onDismissRequest = { scope.launch { state.collapse() } },
    backdrop = sharedBackdrop,
    preset = GlassPreset.Standard,
    dismissThreshold = 0.3f  // 30% drag to dismiss
) {
    // Content
}
```

**Features:**
- Smooth slide-in/out animations with spring physics
- Drag-to-dismiss gesture (downward)
- Configurable dismiss threshold (default 30%)
- Scrim background with blur
- Drag handle indicator
- Automatic snap-back if not dismissed
- Glass morphism effects on sheet surface

**State management:**
```kotlin
enum class GlassBottomSheetValue {
    Collapsed,  // Hidden
    Expanded    // Visible
}

// Control programmatically
scope.launch {
    state.expand()    // Show
    state.collapse()  // Hide
    state.toggle()    // Toggle
}
```

### GlassPerformanceMonitor - Developer Tool

**Real-time performance monitoring overlay.**

**Usage:**
```kotlin
var showMonitor by remember { mutableStateOf(true) }

Box(Modifier.fillMaxSize()) {
    // Your app content
    MyApp()

    // Performance overlay
    GlassPerformanceMonitor(
        visible = showMonitor,
        position = MonitorPosition.TopEnd
    )
}
```

**Metrics tracked:**
- **FPS**: Current frames per second
- **Frame Time**: Average, min, max in milliseconds
- **Dropped Frames**: Count of frames exceeding 16.67ms (60 FPS target)
- **Shader Cache Size**: Number of cached shaders
- **Active Backdrops**: Number of backdrop instances
- **Components Rendered**: Total glass components
- **Memory Usage**: Estimated memory (KB)
- **Current Preset**: Active glass preset

**Performance rating:**
- **Excellent** (green): 55+ FPS, <18ms avg frame time
- **Good** (orange): 45+ FPS, <25ms avg frame time
- **Fair** (yellow): 30+ FPS, <35ms avg frame time
- **Poor** (red): <30 FPS or >35ms avg frame time

**Position options:**
```kotlin
enum class MonitorPosition {
    TopStart,
    TopEnd,
    BottomStart,
    BottomEnd
}
```

**Custom collector:**
```kotlin
val collector = remember { GlassPerformanceCollector() }

// Record frames manually
collector.recordFrame(frameTimeMs = 14)

// Get metrics
val metrics = collector.computeMetrics(
    shaderCacheSize = 10,
    activeBackdrops = 2,
    componentsRendered = 50,
    currentPreset = GlassPreset.Standard
)

// Reset
collector.reset()
```

**Monitoring best practices:**
- Enable in debug builds only
- Use TopEnd or BottomEnd to avoid content occlusion
- Monitor uses Subtle preset to minimize overhead
- Updates at 30 Hz to reduce performance impact
- Disable in production builds

### GlassTestUtils - Testing Infrastructure

**Comprehensive testing utilities for glass components.**

**Test config creation:**
```kotlin
// Minimal config for fast tests (no effects)
val testConfig = GlassTestUtils.createTestConfig()

// Config with effects for visual tests
val testConfigWithEffects = GlassTestUtils.createTestConfigWithEffects(
    preset = GlassPreset.Subtle
)

// Test color scheme
val testColors = GlassTestUtils.createTestColorScheme()
```

**Accessibility testing:**
```kotlin
// Test color scheme accessibility
val result = GlassTestUtils.assertColorSchemeAccessibility(
    colorScheme = GlassColorScheme.defaultDark(),
    minimumRatio = 4.5f  // WCAG AA
)

// Print report
println(result.printReport())
// Output:
// Accessibility Test Result: PASSED
// ✓ onGlass / glassSurface: 7.21 (PASS)
// ✓ onGlassSecondary / glassSurface: 4.87 (PASS)
// ✓ glassAccent / glassSurface: 3.45 (PASS)
```

**Preset testing:**
```kotlin
val result = GlassTestUtils.assertPresetProperties(
    preset = GlassPreset.Standard,
    expectedBlurRadius = 4f,
    expectedVibrancy = true,
    expectedHasEffects = true
)

println(result.printReport())
// Output:
// Preset Test Result: PASSED
```

**Test wrappers:**
```kotlin
@Test
fun testGlassButton() = runComposeUiTest {
    setContent {
        ProvideTestGlassTheme {
            GlassButton(onClick = {}) {
                Text("Test")
            }
        }
    }
    // Assertions...
}

@Test
fun testGlassButtonAccessibility() = runComposeUiTest {
    val accessibilityPrefs = GlassTestUtils.createTestAccessibilityPreferences(
        reducedMotion = true
    )

    setContent {
        ProvideTestGlassThemeWithAccessibility(
            accessibilityPreferences = accessibilityPrefs
        ) {
            GlassButton(onClick = {}) { Text("Test") }
        }
    }
    // Button should use Subtle or None preset
}
```

**Test scenarios:**
```kotlin
// Validate all presets
val presetResults = GlassTestScenarios.validateAllPresets()
// Returns: Map<String, PresetTestResult> for Subtle, Standard, Intense, None

// Validate all color schemes
val colorResults = GlassTestScenarios.validateAllColorSchemes()
// Returns: Map<String, AccessibilityTestResult> for all schemes

// Validate accessibility configs
val accessibilityResults = GlassTestScenarios.validateAccessibilityConfigs()
// Returns: Map<String, Boolean> for each accessibility scenario

// Full test report
val report = GlassTestScenarios.printFullTestReport()
println(report)
```

**Example test report:**
```
============================================================
Glass Theme System - Test Report
============================================================

Preset Validation:
------------------------------------------------------------
Subtle: ✓ PASS
Standard: ✓ PASS
Intense: ✓ PASS
None: ✓ PASS

Color Scheme Accessibility:
------------------------------------------------------------
DefaultDark: ✓ PASS
  ✓ onGlass / glassSurface: 7.21
  ✓ onGlassSecondary / glassSurface: 4.87
  ✓ glassAccent / glassSurface: 3.45
DefaultLight: ✓ PASS
  ✓ onGlass / glassSurface: 8.15
  ✓ onGlassSecondary / glassSurface: 5.23
  ✓ glassAccent / glassSurface: 4.02
Vibrant: ✓ PASS
  ✓ onGlass / glassSurface: 6.89
  ✓ onGlassSecondary / glassSurface: 4.56
  ✓ glassAccent / glassSurface: 3.78

Accessibility Configuration:
------------------------------------------------------------
ReducedMotion_UsesSubtle: ✓ PASS
HighContrast_DisablesEffects: ✓ PASS
ReducedTransparency_DisablesEffects: ✓ PASS

============================================================
Summary: 10/10 tests passed
============================================================
```

### Phase 4 Component Summary

| Component | Purpose | File |
|-----------|---------|------|
| GlassAccessibility | WCAG accessibility support | GlassAccessibility.kt |
| PlatformAccessibilityDetector | System preference detection | GlassAccessibility.desktop.kt |
| WorkspaceScreenWithGlass | Real-world integration example | WorkspaceScreenWithGlass.kt |
| GlassBottomSheet | Drag-to-dismiss modal | GlassBottomSheet.kt |
| GlassPerformanceMonitor | Real-time performance overlay | GlassPerformanceMonitor.kt |
| GlassTestUtils | Testing infrastructure | GlassTestUtils.kt |

### Complete Component Matrix (All Phases)

| Component | Phase | Preset | Interactive | Accessibility |
|-----------|-------|--------|-------------|---------------|
| GlassSurface | 1 | Any | No | ✓ |
| GlassCard | 1 | Standard | No | ✓ |
| GlassPanel | 1 | Standard | No | ✓ |
| GlassButton | 2 | Standard | Yes | ✓ |
| GlassPrimaryButton | 2 | Standard | Yes | ✓ |
| GlassTextButton | 2 | None | Yes | ✓ |
| GlassDialog | 2 | Standard | No | ✓ |
| GlassAlertDialog | 2 | Standard | No | ✓ |
| GlassConfirmDialog | 2 | Standard | No | ✓ |
| GlassTextField | 3 | Subtle | Yes | ✓ |
| GlassPasswordField | 3 | Subtle | Yes | ✓ |
| GlassIconButton | 3 | Subtle | Yes | ✓ |
| GlassIconToggleButton | 3 | Subtle | Yes | ✓ |
| GlassBottomSheet | 4 | Standard | Yes | ✓ |

### Production Checklist

**Before deploying glass theme to production:**

✅ **Accessibility:**
- [ ] Test with screen readers (VoiceOver, Narrator, NVDA)
- [ ] Verify reduced motion support
- [ ] Validate high contrast mode
- [ ] Check reduced transparency handling
- [ ] Test keyboard navigation
- [ ] Verify all contrast ratios meet WCAG AA (4.5:1)

✅ **Performance:**
- [ ] Monitor FPS in target scenarios (should be 55+ FPS)
- [ ] Measure frame times (should be <18ms average)
- [ ] Check shader cache efficiency
- [ ] Verify backdrop sharing works correctly
- [ ] Test on minimum spec hardware
- [ ] Profile memory usage

✅ **Testing:**
- [ ] Run GlassTestScenarios.printFullTestReport()
- [ ] All preset validations pass
- [ ] All color scheme accessibility tests pass
- [ ] Accessibility config tests pass
- [ ] Component integration tests pass
- [ ] Visual regression tests (screenshots)

✅ **Configuration:**
- [ ] Choose appropriate default preset (Standard recommended)
- [ ] Configure shader cache size (10 recommended)
- [ ] Set frame budget (16.67ms for 60 FPS)
- [ ] Enable backdrop caching
- [ ] Document customization points

✅ **Documentation:**
- [ ] Document accessibility features
- [ ] Provide preset selection guide
- [ ] Include performance best practices
- [ ] Add troubleshooting section
- [ ] Create migration guide (if applicable)

### Troubleshooting

**Problem: Low FPS (<30)**
- Solution: Use GlassThemeConfig.performance() or GlassPreset.Subtle
- Check: Are multiple backdrops being created? Use shared backdrop
- Check: Is Intense preset overused? Reserve for hero sections only

**Problem: High memory usage**
- Solution: Reduce shader cache size
- Check: Disable backdrop caching if memory constrained
- Check: Use GlassPreset.None for non-critical sections

**Problem: Accessibility failures**
- Solution: Use LtiAccessibleGlassTheme instead of LtiLiquidGlassTheme
- Check: Enable AutoDetectAccessibility for automatic system preferences
- Check: Validate colors with GlassTestUtils.assertColorSchemeAccessibility()

**Problem: Glass effects not visible**
- Solution: Ensure effectsEnabled = true in GlassThemeConfig
- Check: Verify backdrop is provided (not null)
- Check: Confirm preset.hasEffects() returns true

**Problem: Animations janky**
- Solution: Enable reduced motion or disable animations
- Check: Frame budget - increase to 33ms (30 FPS) if needed
- Check: Use rememberAnimatedGlassPreset() to cache animation state

### Migration Guide

**From Liquid components to Glass theme:**

```kotlin
// Before (Liquid)
val backdrop = rememberLayerBackdrop()
LiquidButton(
    onClick = { },
    backdrop = backdrop,
    tint = Color(0xFF2979FF)
) {
    Text("Button")
}

// After (Glass)
GlassButton(onClick = { }) {
    Text("Button")
}
// Backdrop and colors managed by theme
```

**Benefits:**
- No manual backdrop management
- Centralized styling
- Automatic accessibility support
- Better performance (shared backdrop)
- Consistent behavior

### Future Enhancements

**Potential Phase 5 additions:**
- GlassTooltip - Tooltip with glass background
- GlassDropdown - Dropdown menu with glass
- GlassSnackbar - Notification with glass
- GlassNavigationRail - Side navigation with glass
- GlassTabRow - Tab bar with glass
- Animated backdrop transitions
- Custom shader support
- iOS/Android platform implementations

### Credits

Lti Liquid Glass Theme System is built on:
- Compose Multiplatform (JetBrains)
- Skia Graphics (Google)
- Material3 Design System (Google)
- WCAG 2.1 Accessibility Guidelines (W3C)