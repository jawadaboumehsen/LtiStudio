# Glass Switch (`GlassToggle`) Prototype Findings & Implementation Proposal

This document summarizes the empirical measurements, optical verifications, and visual comparisons conducted to address why the glass effect was not visible during switching in [`GlassToggle.kt`](core/designsystem/src/commonMain/kotlin/org/ide/lti/core/designsystem/component/inputs/GlassToggle.kt), along with the recommended production implementation plan.

---

## 1. Executive Summary & Clarification of Benefits

### Why the Switch Looked Stiff During Switching
1. **Static vs. Animated Track Tint**: In production `GlassToggle.kt`, `trackTint` and `trackSurfaceColor` popped abruptly on frame 1 while the thumb animated over $180\,\text{ms}$.
2. **Thumb Geometry & Area Coverage**: The $40 \times 24\,\text{dp}$ oblong pill thumb covers $\sim 54\%$ ($960 / 1792\,\text{dp}^2$) of the track's rectangular area and leaves only $20\,\text{dp}$ of horizontal track exposed with a thin $2\,\text{dp}$ vertical rim.
3. **Smooth Wallpaper Reality**: The switch sits over `GlassBackdrop.kt`, which consists of smooth radial gradients. There are no high-frequency background textures (e.g. text or album art) to refract.

### Separating "Better Switch Silhouette" from "Visible Haze Blur"
- **What Pair B Delivers**:
  - **Standard Switch Silhouette**: Replaces the awkward oblong pill with a canonical $28 \times 28\,\text{dp}$ circular thumb inside a $56 \times 32\,\text{dp}$ capsule track, uncovering corner crescents.
  - **$20\%$ Gain in Exposed Track Length**: Exposed horizontal track increases from $20\,\text{dp}$ to $24\,\text{dp}$. The thumb occupies $\sim 44\%$ of the track bounding box ($\sim 34\%$ of actual circle area vs. $54\%$ in Pair A).
  - **Smooth Continuous Color Animation**: Exposed track tint modulates smoothly across the $180\,\text{ms}$ stroke (measured at $\Delta = 0.412$ mid-flight vs. $0.561$ at completion).
  - **Decoupled $48\,\text{dp}$ Interactive Target**: Meets accessibility guidelines while maintaining a compact $32\,\text{dp}$ visual height.
  - **Visible Focus Ring**: Accessible via both Tab navigation and FocusRequester.
- **What We Do NOT Claim**:
  - Pair B does **not** produce dramatically stronger or higher Haze blur over the smooth wallpaper. Optical spread measurements ($W_{\text{trans}} = 3\,\text{px}$ vs $4\,\text{px}$) show that blur is mathematically authentic, but over smooth radial gradients, Haze refraction remains subtle in both pairs. Pair B is recommended for its **ergonomics, silhouette, and animated tint**, not for dramatically more visible blur.

---

## 2. Real Settings Screen Comparison (Pair A vs. Pair B Across All Themes)

Both Pair A and Pair B were rendered inside the real `AppearanceSettingsPane` in `feature/settings` on the "Glass & motion" card across the standard $1120 \times 700$ desktop viewport across all 3 themes and both effect modes:

### A. Blue Glass Theme
- **Effects ON**:
  - Pair A: `feature/settings/build/reports/screenshots/settings_card_blue_effects_on_pairA.png`
  - Pair B: `feature/settings/build/reports/screenshots/settings_card_blue_effects_on_pairB.png`
  - *Observation*: In Pair A, the active switch ("Show focus indicators") has its blue track largely covered by the white oblong thumb. In Pair B, the circular knob exposes $24\,\text{dp}$ of rich blue glass track and curved corner crescents.
- **Effects OFF (Opaque Fallback)**:
  - Pair A: `feature/settings/build/reports/screenshots/settings_card_blue_effects_off_pairA.png`
  - Pair B: `feature/settings/build/reports/screenshots/settings_card_blue_effects_off_pairB.png`
  - *Observation*: With effects disabled, Pair B's circular thumb on a $56 \times 32\,\text{dp}$ track provides a clean Material 3 / macOS switch look, whereas Pair A looks like an unstyled slider.

### B. Dark Theme
- **Effects ON**:
  - Pair A: `feature/settings/build/reports/screenshots/settings_card_dark_effects_on_pairA.png`
  - Pair B: `feature/settings/build/reports/screenshots/settings_card_dark_effects_on_pairB.png`
  - *Observation*: Pair B's circular thumb gives distinct shape contrast against the dark card surface.
- **Effects OFF**:
  - Pair A: `feature/settings/build/reports/screenshots/settings_card_dark_effects_off_pairA.png`
  - Pair B: `feature/settings/build/reports/screenshots/settings_card_dark_effects_off_pairB.png`

### C. Light Theme
- **Effects ON**:
  - Pair A: `feature/settings/build/reports/screenshots/settings_card_light_effects_on_pairA.png`
  - Pair B: `feature/settings/build/reports/screenshots/settings_card_light_effects_on_pairB.png`
  - *Observation*: In Light theme, the dark `onSurface` thumb against the pale translucent track looks significantly more intentional and balanced as a circle than as an elongated block.
- **Effects OFF**:
  - Pair A: `feature/settings/build/reports/screenshots/settings_card_light_effects_off_pairA.png`
  - Pair B: `feature/settings/build/reports/screenshots/settings_card_light_effects_off_pairB.png`

---

## 3. Quantitative Prototype Test Results

All prototype test suites pass 100% green (`:core:designsystem:desktopTest`):

### A. Exposed Track Tint Interpolation (Frame-by-Frame Motion)
Measured by sampling exposed track pixels directly during animation (`testNormalMotionAnimatesFrameByFrame`):
- **$t = 0\,\text{ms}$ (Inactive initial)**: Exposed track at $x = 120$ measures `Color(0.235, 0.298, 0.404)`.
- **$t = 90\,\text{ms}$ (Mid-flight)**: Thumb is at $x = 100$ (spans $86..114$), so $x = 120$ is STILL exposed track. Measured color is `Color(0.267, 0.475, 0.816)` ($\Delta_{\text{blue}} = 0.412$ from initial).
- **$t = 210\,\text{ms}$ (Complete)**: Newly uncovered track at $x = 80$ measures `Color(0.231, 0.510, 0.965)` ($\Delta_{\text{blue}} = 0.561$ from initial).
- **Result**: Proves continuous, smooth color interpolation across the stroke rather than a 1-frame pop.

### B. Reduced Motion $0\,\text{ms}$ Snap
- **On Frame 1**: Uncovered track at $x = 80$ immediately measures `Color(0.231, 0.510, 0.965)` ($\Delta_{\text{blue}} = 0.561$ on frame 1), confirming instantaneous snap without transition delay.

### C. Keyboard Focus Visibility (Tab Navigation & FocusRequester)
Measured pixel diff between unfocused and focused states:
- **Tab Key Navigation (`pressKey(Key.Tab)`)**:
  - Effects ON: $1.475\%$ diff ($354 / 24,000\,\text{px}$)
  - Effects OFF: $1.479\%$ diff ($355 / 24,000\,\text{px}$)
- **Direct FocusRequester**:
  - Effects ON: $1.475\%$ diff
  - Effects OFF: $1.479\%$ diff
- **Result**: Both keyboard Tab navigation and programmatic focus render the primary $2\,\text{dp}$ focus ring in both modes.

### D. Full-Region `captureBacking` Comparison
Sampled across all $120,000\,\text{px}$ in the toggle region over smooth `GlassBackdrop`:
- `maxChannelDelta = 0.0000`, `mismatchedPixels = 0`.
- **Result**: Confirms image-wide pixel equality between opaque backing and transparent backing over `GlassBackdrop`.

---

## 4. Coherent Production Integration Steps

To implement Pair B coherently into production without leaving orphaned defaults:

1. **Update `Dimens.kt` (`ComponentSize`)**:
   ```kotlin
   val ToggleTrackWidth: Dp = 56.dp
   val ToggleTrackHeight: Dp = 32.dp
   val ToggleThumbWidth: Dp = 28.dp
   val ToggleThumbHeight: Dp = 28.dp
   val ToggleMinTouchTarget: Dp = 48.dp
   ```

2. **Update `ToggleDimensions.kt`**:
   Ensure default constructor values reflect Pair B production tokens:
   ```kotlin
   @Immutable
   data class ToggleDimensions(
       val trackWidth: Dp = ComponentSize.ToggleTrackWidth, // 56.dp
       val trackHeight: Dp = ComponentSize.ToggleTrackHeight, // 32.dp
       val thumbWidth: Dp = ComponentSize.ToggleThumbWidth, // 28.dp
       val thumbHeight: Dp = ComponentSize.ToggleThumbHeight, // 28.dp
       val thumbIsCircle: Boolean = true, // Default to circular thumb!
       val padding: Dp = Spacing.ExtraExtraSmall, // 2.dp
       val minTouchTarget: Dp = ComponentSize.ToggleMinTouchTarget, // Default to 48.dp!
   )
   ```

3. **Update `GlassToggle.kt`**:
   - Enable animated track tint and backing (`animateColorAsState` with $180\,\text{ms}$ or $0\,\text{ms}$ snap on `reducedMotion`).
   - Draw visible keyboard focus border (`2.dp`, `colors.primary`) when `isFocused` is true.
   - Use `CircleShape` when `thumbIsCircle` is true.
   - Decouple interactive touch target ($48\,\text{dp}$) from visual track ($32\,\text{dp}$).

4. **Re-Record Roborazzi Golden Baselines**:
   - Run `./gradlew :feature:settings:recordRoborazziDesktop` to update golden images for `SettingsAppearanceScreenshotTest`.
   - Run `graphify update .` to keep knowledge graph current.
