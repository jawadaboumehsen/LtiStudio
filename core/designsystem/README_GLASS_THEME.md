# Lti Liquid Glass Theme System

A production-ready glass morphism theme system for Compose Multiplatform Desktop with comprehensive accessibility support, performance optimizations, and SOLID architecture.

![Glass Theme Banner](docs/glass-theme-banner.png)

## Features

✨ **Professional Glass Morphism**
- Blur, vibrancy, and lens refraction effects
- 4 built-in presets (Subtle, Standard, Intense, None)
- Customizable color schemes
- Material3 integration

♿ **WCAG-Compliant Accessibility**
- Automatic system preference detection
- Reduced motion support
- High contrast mode
- Screen reader optimization
- Contrast ratio validation (WCAG AA/AAA)

🚀 **Performance Optimized**
- Shader caching
- Shared backdrop architecture
- GPU-accelerated effects
- Real-time performance monitoring
- Frame budget management

🧪 **Comprehensive Testing**
- Unit test utilities
- Accessibility test scenarios
- Preset validation
- Color contrast assertions
- Mock providers

## Quick Start

### 1. Basic Setup

```kotlin
import org.ide.lti.core.designsystem.theme.glass.*

@Composable
fun App() {
    LtiLiquidGlassTheme {
        // Your app content with glass effects
        GlassCard {
            Text("Hello, Glass!", color = GlassTheme.colors.onGlass)
        }
    }
}
```

### 2. Component Gallery

```kotlin
// Button with glass background
GlassButton(onClick = { }) {
    Text("Click Me")
}

// Card container
GlassCard {
    Column {
        Text("Title", style = MaterialTheme.typography.titleLarge)
        Text("Content goes here")
    }
}

// Text input with glass
var text by remember { mutableStateOf("") }
GlassTextField(
    value = text,
    onValueChange = { text = it },
    label = "Username"
)

// Icon button for toolbars
GlassIconButton(onClick = { }) {
    Icon(Icons.Default.Settings, "Settings")
}

// Bottom sheet modal
var showSheet by remember { mutableStateOf(false) }
GlassBottomSheetScaffold(
    visible = showSheet,
    onDismissRequest = { showSheet = false },
    title = "Options"
) {
    // Sheet content
}
```

### 3. Accessibility Support

```kotlin
// Automatic system preference detection
AutoDetectAccessibility {
    LtiAccessibleGlassTheme {
        // Automatically adapts to:
        // - Reduced motion
        // - High contrast
        // - Reduced transparency
        // - Screen readers
        GlassButton(onClick = { }) {
            Text("Accessible Button")
        }
    }
}
```

### 4. Custom Configuration

```kotlin
val config = glassThemeConfig {
    withPreset(GlassPreset.Intense)
    withColors(GlassColorScheme.vibrant())
    withMaxShaderCacheSize(15)
    withFrameBudget(16.67f)  // 60 FPS
}

LtiLiquidGlassTheme(config = config) {
    // Custom themed content
}
```

## Architecture

### SOLID Principles

**Single Responsibility:**
- `GlassPreset` - Effect parameters only
- `GlassColorScheme` - Color tokens only
- `GlassThemeConfig` - Complete configuration

**Open/Closed:**
- Extensible via builder pattern
- Custom presets and color schemes
- Preset override at any level

**Liskov Substitution:**
- Compatible with Material3 theming
- All glass components work with any preset

**Interface Segregation:**
- `IGlassEffectConfig` - Effect parameters
- `IGlassColorConfig` - Color configuration
- `IGlassPerformanceConfig` - Performance settings

**Dependency Inversion:**
- `LocalGlassTheme` composition local
- `GlassTheme` typed access object
- Backdrop injection via `LocalGlassBackdrop`

### File Structure

```
core/designsystem/
├── theme/glass/
│   ├── GlassPreset.kt                 # Effect presets
│   ├── GlassColorScheme.kt            # Color tokens
│   ├── GlassThemeConfig.kt            # Configuration + builder
│   ├── LocalGlassTheme.kt             # Composition locals
│   ├── LtiLiquidGlassTheme.kt      # Main theme wrapper
│   ├── GlassAccessibility.kt          # Accessibility support
│   ├── GlassAnimations.kt             # Animation system
│   └── GlassModifiers.kt              # Utility modifiers
├── component/glass/
│   ├── GlassSurface.kt                # Base surface
│   ├── GlassCard.kt                   # Card container
│   ├── GlassPanel.kt                  # Panel/sidebar
│   ├── GlassButton.kt                 # Interactive button
│   ├── GlassTextField.kt              # Text input
│   ├── GlassIconButton.kt             # Icon button
│   ├── GlassDialog.kt                 # Modal dialog
│   ├── GlassBottomSheet.kt            # Bottom sheet
│   ├── GlassPerformanceMonitor.kt     # Performance overlay
│   ├── GlassTestUtils.kt              # Testing utilities
│   ├── GlassThemeShowcase.kt          # Interactive demo
│   └── WorkspaceScreenWithGlass.kt    # Integration example
└── src/desktopMain/kotlin/.../
    └── GlassAccessibility.desktop.kt  # Platform detection
```

## Component Reference

### Phase 1 - Core Components

| Component | Purpose | Preset |
|-----------|---------|--------|
| `GlassSurface` | Base customizable surface | Any |
| `GlassCard` | Card with padding | Standard |
| `GlassPanel` | Full-height sidebar | Standard |

### Phase 2 - Interactive Components

| Component | Purpose | Preset |
|-----------|---------|--------|
| `GlassButton` | Interactive button | Standard |
| `GlassPrimaryButton` | Emphasized button | Standard |
| `GlassTextButton` | Text-only button | None |
| `GlassDialog` | Modal dialog | Standard |
| `GlassAlertDialog` | Simple alert | Standard |
| `GlassConfirmDialog` | Confirmation dialog | Standard |

### Phase 3 - Input & Animation

| Component | Purpose | Preset |
|-----------|---------|--------|
| `GlassTextField` | Text input | Subtle |
| `GlassPasswordField` | Password input | Subtle |
| `GlassIconButton` | Toolbar icon button | Subtle |
| `GlassIconToggleButton` | Toggle icon button | Subtle |
| `animateGlassPreset()` | Preset transitions | - |
| `rememberAnimatedGlassPreset()` | Auto-animated preset | - |

### Phase 4 - Production Features

| Component | Purpose | Preset |
|-----------|---------|--------|
| `GlassBottomSheet` | Drag-to-dismiss modal | Standard |
| `GlassPerformanceMonitor` | Performance overlay | Subtle |
| `GlassAccessibility` | Accessibility utilities | - |
| `GlassTestUtils` | Testing infrastructure | - |
| `WorkspaceScreenWithGlass` | Integration example | Mixed |

## Presets Guide

### When to Use Each Preset

**GlassPreset.Subtle** (2dp blur, minimal refraction)
- ✅ Toolbars and status bars
- ✅ Icon buttons and controls
- ✅ Frequently updated surfaces
- ✅ Low-end hardware
- ❌ Hero sections or featured content

**GlassPreset.Standard** (4dp blur, moderate refraction) - **Default**
- ✅ Panels and sidebars
- ✅ Cards and containers
- ✅ Dialogs and modals
- ✅ General purpose use
- ❌ Main editor/content areas

**GlassPreset.Intense** (8dp blur, strong refraction)
- ✅ Hero sections
- ✅ Featured content
- ✅ Splash screens
- ❌ Toolbars or frequently used UI
- ❌ Text-heavy areas

**GlassPreset.None** (No effects)
- ✅ Accessibility mode
- ✅ Reduced motion preference
- ✅ High contrast mode
- ✅ Performance-critical sections
- ✅ Main editor/content areas

## Performance Best Practices

### Do's ✅

- Share a single backdrop across the entire app
- Use `Subtle` preset for toolbars and frequently updated UI
- Use `Standard` preset for panels and containers
- Reserve `Intense` preset for hero sections only
- Enable shader caching (default: 10 shaders)
- Use `GlassTextButton` for non-glass buttons
- Provide accessibility fallbacks

### Don'ts ❌

- Don't create multiple backdrops when one can be shared
- Don't use `Intense` preset everywhere
- Don't apply glass effects to main editor/content areas
- Don't ignore accessibility preferences
- Don't skip performance testing
- Don't hardcode colors (use `GlassTheme.colors`)

## Accessibility Guidelines

### WCAG Compliance

The glass theme system meets **WCAG 2.1 Level AA** requirements:

- ✅ **Contrast Ratio**: 4.5:1 for normal text, 3:1 for large text
- ✅ **Reduced Motion**: Automatically uses `Subtle` or `None` preset
- ✅ **High Contrast**: Disables effects, increases opacity to 95%
- ✅ **Reduced Transparency**: Uses opaque surfaces
- ✅ **Keyboard Navigation**: All interactive components keyboard accessible
- ✅ **Screen Reader**: Semantic structure and focus management

### Testing Accessibility

```kotlin
// Validate color scheme
val result = GlassTestUtils.assertColorSchemeAccessibility(
    colorScheme = GlassColorScheme.defaultDark(),
    minimumRatio = 4.5f  // WCAG AA
)

println(result.printReport())

// Test with accessibility preferences
val prefs = GlassAccessibilityPreferences(
    prefersReducedMotion = true,
    prefersHighContrast = false,
    prefersReducedTransparency = false
)

val config = GlassAccessibility.createAccessibleConfig(prefs)
// Config automatically adjusted for accessibility
```

## Performance Monitoring

### Enable Performance Overlay

```kotlin
@Composable
fun App() {
    var showMonitor by remember { mutableStateOf(BuildConfig.DEBUG) }

    Box(Modifier.fillMaxSize()) {
        LtiLiquidGlassTheme {
            MyAppContent()
        }

        // Performance monitor (debug only)
        if (showMonitor) {
            GlassPerformanceMonitor(
                visible = true,
                position = MonitorPosition.TopEnd
            )
        }
    }
}
```

### Performance Targets

| Metric | Excellent | Good | Fair | Poor |
|--------|-----------|------|------|------|
| FPS | 55+ | 45+ | 30+ | <30 |
| Frame Time | <18ms | <25ms | <35ms | >35ms |
| Dropped Frames | 0 | <5 | <10 | >10 |

## Testing

### Unit Testing

```kotlin
import org.ide.lti.core.designsystem.component.glass.GlassTestUtils
import org.ide.lti.core.designsystem.component.glass.GlassTestScenarios

@Test
fun validateGlassTheme() {
    // Run all validation scenarios
    val report = GlassTestScenarios.printFullTestReport()
    println(report)

    // All tests should pass
    assert(report.contains("Summary: 10/10 tests passed"))
}

@Test
fun testGlassButton() = runComposeUiTest {
    setContent {
        ProvideTestGlassTheme {
            GlassButton(onClick = {}) {
                Text("Test")
            }
        }
    }

    onNodeWithText("Test").assertExists()
    onNodeWithText("Test").performClick()
}
```

### Accessibility Testing

```kotlin
@Test
fun testAccessibility() {
    val colorScheme = GlassColorScheme.defaultDark()
    val result = GlassTestUtils.assertColorSchemeAccessibility(
        colorScheme = colorScheme,
        minimumRatio = 4.5f
    )

    assertTrue(result.passed, "Color scheme should meet WCAG AA")
}
```

## Examples

### Complete Workspace Example

See `WorkspaceScreenWithGlass.kt` for a full IDE-style workspace implementation with:
- Top toolbar with glass
- Left sidebar (file explorer) with glass
- Center editor area (solid background)
- Right sidebar (AI assistant) with glass
- Bottom panel (terminal) with glass
- Status bar with glass

```kotlin
@Composable
fun App() {
    LtiLiquidGlassTheme {
        WorkspaceScreenWithGlass()
    }
}
```

### Custom Themed Section

```kotlin
@Composable
fun HeroSection() {
    // Override preset for this section only
    ProvideGlassPreset(GlassPreset.Intense) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(32.dp)) {
                Text(
                    "Welcome to LtiRomGui",
                    style = MaterialTheme.typography.displayLarge,
                    color = GlassTheme.colors.onGlass
                )
                Text(
                    "Professional glass morphism theme",
                    color = GlassTheme.colors.onGlassSecondary
                )
            }
        }
    }
}
```

## Troubleshooting

### Glass effects not visible

**Problem**: Glass effects don't appear

**Solutions**:
1. Ensure `effectsEnabled = true` in `GlassThemeConfig`
2. Verify backdrop is provided (not null)
3. Confirm `preset.hasEffects()` returns true
4. Check that you're using a `CornerBasedShape` (RoundedCornerShape, ContinuousCapsule)

### Poor performance

**Problem**: FPS below 30 or janky animations

**Solutions**:
1. Use `GlassThemeConfig.performance()` configuration
2. Switch to `GlassPreset.Subtle` or `GlassPreset.None`
3. Ensure single shared backdrop (not creating multiple)
4. Check shader cache size (increase if needed)
5. Profile with `GlassPerformanceMonitor`

### Accessibility issues

**Problem**: Failing accessibility tests or poor contrast

**Solutions**:
1. Use `LtiAccessibleGlassTheme` instead of `LtiLiquidGlassTheme`
2. Enable `AutoDetectAccessibility` for system preferences
3. Validate colors with `GlassTestUtils.assertColorSchemeAccessibility()`
4. Use `GlassAccessibility.suggestAccessibleForeground()` to fix colors

## Migration Guide

### From Liquid Components

```kotlin
// Before (old Liquid components)
val backdrop = rememberLayerBackdrop()
LiquidButton(
    onClick = { },
    backdrop = backdrop,
    tint = Color(0xFF2979FF)
) {
    Text("Button")
}

// After (Glass theme)
GlassButton(onClick = { }) {
    Text("Button")
}
// Backdrop and colors automatically managed
```

**Benefits**:
- ✅ No manual backdrop management
- ✅ Centralized styling via theme
- ✅ Automatic accessibility support
- ✅ Better performance (shared backdrop)
- ✅ Consistent behavior across app

## FAQ

**Q: Can I use glass theme with Material3?**
A: Yes! `LtiLiquidGlassTheme` wraps `MaterialTheme`, so both are available.

**Q: How do I change the default preset?**
A: Pass a custom `GlassThemeConfig` with your desired preset to `LtiLiquidGlassTheme`.

**Q: Can I use different presets in different parts of my app?**
A: Yes! Use `ProvideGlassPreset()` to override the preset for a specific section.

**Q: Is the glass theme accessible?**
A: Yes! The theme fully supports WCAG 2.1 Level AA with automatic adaptations for reduced motion, high contrast, and reduced transparency.

**Q: Does it work on low-end hardware?**
A: Yes! Use `GlassThemeConfig.performance()` or `GlassPreset.Subtle` for optimal performance.

**Q: Can I test glass components?**
A: Yes! Use `GlassTestUtils` and `ProvideTestGlassTheme` for comprehensive testing.

**Q: How do I monitor performance?**
A: Use `GlassPerformanceMonitor` in debug builds to see real-time FPS, frame times, and metrics.

## Platform Support

- ✅ **Desktop** (Windows, macOS, Linux) - Fully supported
- 🚧 **Android** - Platform implementation needed
- 🚧 **iOS** - Platform implementation needed
- 🚧 **Web** - Platform implementation needed

## Contributing

The glass theme system is part of the Lti project. Contributions are welcome!

### Development Setup

1. Clone the Lti repository
2. Open in IntelliJ IDEA or Android Studio
3. Navigate to `core/designsystem`
4. Run `GlassThemeShowcase` to see all components

### Running Tests

```bash
./gradlew :core:designsystem:test
```

### Adding New Components

1. Create component in `component/glass/`
2. Follow existing patterns (GlassSurface, GlassButton, etc.)
3. Add to `GlassThemeShowcase`
4. Add tests using `GlassTestUtils`
5. Update documentation

## License

See the main Lti project for license information.

## Credits

Built with:
- **Compose Multiplatform** by JetBrains
- **Skia Graphics** by Google
- **Material3 Design** by Google
- **WCAG 2.1** by W3C

## Documentation

- **Full Documentation**: See `CLAUDE.md` in the project root
- **Component Reference**: `CLAUDE.md` - Complete component documentation
- **Architecture Guide**: `CLAUDE.md` - SOLID principles and design patterns
- **Backdrop System**: `CLAUDE.md` - Glass morphism implementation details

## Support

For issues, questions, or feature requests, please refer to the main Lti project repository.

---

**Lti Liquid Glass Theme System** - Professional glass morphism for Compose Multiplatform
Version 1.0.0 | Made with ❤️ for the Lti project
