# Theme & Design System Rules

Read this file fully before editing anything under `core/designsystem/theme/`,
`core/designsystem/component/`, `core/ui/`, or any file that renders text/icons on a `Glass*`
surface. It is a companion to `CLAUDE.md` (which documents the token catalog and component API) —
this file documents the **hard rules and known blind spots** that were each discovered by a real
bug reaching the running app. Every rule below maps to a real incident, not a hypothetical.

If you are an agent about to edit theme/design-system code: read this file, then make your change,
then re-read the "Before you finish" checklist at the bottom.

This file covers *correctness* (colors, contrast, spacing — bugs a screenshot diff catches). For
*performance* rules (recomposition, allocation, phase discipline — regressions a screenshot diff
can't catch since the pixels are identical), see [`rules/jetpackcomposerule.md`](jetpackcomposerule.md).

---

## Rule 1: Every component that renders a `Text`/`Icon` inside a content lambda MUST make
## `LocalContentColor` resolve to something other than black

**The rule:** if you're building a new glass container (anything with a `content: @Composable
() -> Unit` slot), it must either be built on top of `GlassSurface` (which now provides
`LocalContentColor` — see below) or explicitly wrap its content in
`CompositionLocalProvider(LocalContentColor provides <a real GlassTheme.colors.* token>) { content() }`.
Never assume a bare `Text()`/`Icon()` inside your new component will "just work."

**Why:** Compose's `LocalContentColor` composition local defaults to pure black
(`#000000`) unless something up the tree explicitly overrides it. Material3's `Surface` does this
automatically. This app's glass components are mostly built on plain `Box`/`Row`/`Column` +
`drawBackdrop` — NOT Material3 `Surface` — so nothing provided a real value by default. This exact
bug was found and fixed **seven separate times** in one session, in `GlassChatInput`'s
`BasicTextField`, `GlassPanelHeader`'s title/icon, `GlassMessageBubble`'s timestamp,
`GlassPasswordField`'s toggle icon, `GlassIcon`'s content, `GlassBottomTab`'s items, and
`AppIcons`'s panel-toggle icons. Every instance rendered invisible/wrong-colored black text or
icons against the dark AMOLED theme.

**What's already fixed structurally** (don't re-fix, but understand why it now works): `GlassSurface`
(`core/designsystem/component/GlassSurface.kt`) now wraps its `content()` in
`CompositionLocalProvider(LocalContentColor provides glassColors.onGlass)` — so `GlassCard` and
`GlassPanel`, both built on `GlassSurface`, inherit this automatically. `LtiTheme` itself
(`core/designsystem/theme/Theme.kt`) also now provides `LocalContentColor` at the composition root,
covering anything that sits *outside* any glass container. `GlassButton`/`GlassIconButton` were the
original correct reference pattern — they've always done this correctly.

**What is still NOT automatically covered:** any component built directly on `Box`/`Row`/`Column` +
`drawBackdrop` that does *not* go through `GlassSurface` (e.g. a new custom component that doesn't
delegate to `GlassCard`/`GlassPanel`). If you write one, provide `LocalContentColor` yourself.

**This bug class is invisible to normal review.** It doesn't cause a compile error, doesn't trigger
`verifyDesignSystemGuardrails` or `:detekt-rules` (see Rule 2), and can survive a screenshot test if
that test's own background happens to give black text enough contrast to look "fine" at a glance
(see Rule 3). Verify by eye against the real dark AMOLED background, or by real pixel sampling
(`toPixelMap()`), not by glancing at a passing test.

---

## Rule 2: `verifyDesignSystemGuardrails` and `:detekt-rules` do NOT catch a missing color —
## only a wrong one

**The rule:** don't treat a clean `./gradlew verifyDesignSystemGuardrails detekt` run as proof that
colors are handled correctly. It proves something narrower: no raw `Color(0x...)` / `alpha = 0.x` /
raw `.dp` literal bypassing `theme/*`, and no forbidden raw Material3 import outside
`core:designsystem`. It says nothing about a `color`/`tint` parameter being **omitted entirely** —
that's Rule 1's failure mode, and these checks cannot see it. A feasibility review this session
concluded a naive Detekt rule for "flag any `Text`/`Icon` without an explicit `color`/`tint`" would
false-positive on every valid instance inside `GlassButton`/`GlassIconButton`/`GlassNavigationBarItem`
(which correctly inherit color) without real composition-ancestry analysis — so there is currently
no automated check for Rule 1. Structural fixes (Rule 1) are the real defense, not a lint rule.

---

## Rule 3: A screenshot test's own background can hide a real color bug — test against something
## dark, not flat gray

**The rule:** when writing or reviewing a screenshot test for a `Glass*` component, don't compose it
against a flat neutral/mid-gray background and call it representative. Compose it against something
closer to the real app's aurora/AMOLED backdrop, or at minimum a genuinely dark surface.

**Why:** `testGlassPanelContainer`'s existing, previously-approved baseline had literal
`#000000` "EXPLORER" text in it. It read as "fine" during review because the test's flat gray
background gave black text just enough contrast to be legible. The bug only became obviously,
visibly broken once viewed against the real app's much darker AMOLED surface. The baseline was
approved once with the bug already in it.

---

## Rule 4: `Color.copy(alpha = X)` REPLACES alpha, it does not multiply it — don't stack alpha
## by copying a token that already has alpha baked in

**The rule:** before calling `someToken.copy(alpha = SomeAlphaToken)`, check whether `someToken`
already has a non-1.0 alpha baked into its own definition in `Color.kt`. If it does, `.copy()` throws
that away and replaces it — it does not combine the two.

**Why:** `GlassTopBar` and `GlassStatusBar`'s outer border both did
`glassColors.glassBorder.copy(alpha = AlphaTokens.Border)` (0.50). But `glassBorder` is already
`colorScheme.onSurface.copy(alpha = AlphaTokens.Hover)` (0.20) in `Color.kt`. The `.copy()` call
discarded that 0.20 and re-stamped 0.50 — a 2.5x stronger, near-white rectangular outline around
both bars, while every other border in the same files just used `glassColors.glassBorder` directly
(keeping its intended 0.20). If you want a token's own baked-in alpha, reference it directly, don't
re-copy an alpha onto it. If you deliberately want a *different* alpha than the token's default, that
usually means you want a different, more specific token (or the base un-alpha'd color), not a
double-alpha stack.

---

## Rule 5: `ColorContrast.relativeLuminance()` ignores the alpha channel — `GlassContrastTest`
## cannot validate an alpha-based fix

**The rule:** if a text/border contrast fix relies on changing an `AlphaTokens.*` value (not the
underlying RGB), don't trust `./gradlew :core:designsystem:test`'s `GlassContrastTest` to prove it
worked — it mathematically can't see the change.

**Why:** `core/designsystem/theme/ColorContrast.kt`'s `relativeLuminance(color: Color)` only reads
`color.red/green/blue`. It does not read `color.alpha` at all. This was discovered when
`onGlassMuted` and `onGlassSecondary` — deliberately different in their alpha component — printed
the *identical* contrast figure. For any alpha-dependent visual fix, verify with real rendered pixel
sampling (`captureToImage().toPixelMap()`) against the real composited background, not the
mathematical contrast test.

---

## Rule 6: every `Icon()` call needs an explicit size from `IconSize.*` — an omitted size silently
## defaults to Material3's 24dp

**The rule:** always pass `modifier = Modifier.size(IconSize.<something>)` on `Icon()`. Don't rely
on the default.

**Why:** `GlassPanelHeader`'s leading icon omitted a size modifier entirely and silently rendered at
Material3's built-in 24dp default, next to 14sp header text and 16dp (`IconSize.Medium`) action
icons elsewhere in the same header — visually oversized and inconsistent. This is the sizing analog
of Rule 1's color gap: an implicit Compose default that's wrong for this app, invisible to every
existing automated check, and only obvious once you actually look at the rendered result next to
its siblings.

---

## Rule 7: check what a component's OWN implicit defaults resolve to before assuming the theme
## already covers it — not just color and size

**The rule:** when auditing a component for theme-correctness, don't only check for missing
`color`/`tint`. Check every implicit Compose default the component relies on: text/cursor color on
`BasicTextField` (defaults to solid black `cursorBrush`), disabled-state color (a `TextStyle.color`
that's set unconditionally regardless of an `enabled` param renders identically whether the field is
usable or not), `CircularProgressIndicator()`'s/`HorizontalDivider()`'s own Material3 default color
(not this app's `glassAccent`/`glassBorder`), and anything else that "just renders something" without
an explicit token.

**Why:** beyond the black-text bug class, this session separately found and fixed: `GlassChatInput`'s
`BasicTextField` had no `cursorBrush` at all (invisible black cursor on dark theme);
`GlassTextField`/`GlassChatInput` applied the *active* text color unconditionally regardless of
`enabled`, so a disabled field looked identical to an editable one; a bare `CircularProgressIndicator()`
in `GlassDialog`/`AIAssistantPanel` used Material3's default indicator color instead of `glassAccent`;
a bare `HorizontalDivider()` in `AIAssistantPanel` used Material3's default opaque `outlineVariant`
instead of the `glassBorder.copy(alpha = AlphaTokens.Faint)` + `StrokeWidth.Hairline` pattern used
everywhere else dividers appear in this app.

---

## Rule 8: layout/spacing conventions for the app chrome — ONE continuous framed window with hairline dividers

**The rule:** the IDE window is structured into **TWO glass surfaces** — a chrome "picture frame"
surface and an inner content surface:
- **Surface 1 (Chrome Frame):** Outer `GlassSurface` (`shape = ContinuousRoundedRectangle(appearance.panelCornerRadius)`,
  `StrokeWidth.Hairline` outer border, `glassSurface` + `glassTint` color). Forms a single full-window frame
  with ONE backdrop sample. Contains the `topBar()`, the left icon rail (`leftRail()`), the right icon rail
  (`rightRail()`), and the `statusBar()`.
- **Horizontal dividers:** Crisp hairline horizontal dividers (`StrokeWidth.Hairline` height, colored with
  `GlassTheme.colors.glassBorder.copy(alpha = AlphaTokens.Faint)`) separate the top bar and status bar from the content area.
- **Surface 2 (Content Surface):** Inner `GlassSurface` (`shape = ContinuousRoundedRectangle(ThemeCornerRadius.None)`,
  `surfaceColor = glassColors.canvasSurface`, `tint = Color.Transparent`, with `EnhancedPanelEffects` and ONE backdrop sample)
  painted between the left and right rails. Contains the left panel content (Explorer), center column (Editor + Terminal),
  and right panel content (AI Assistant), separated by interactive hairline drag dividers.
- **Rail vs. Panel separation:** `GlassSidePanelRail` renders ONLY the narrow vertical icon strip (36dp, drawing NO
  background of its own, so the chrome frame shows through) and an inner hairline divider. `GlassSidePanelTabs` is a
  convenience wrapper delegating to `GlassSidePanelRail`. Left/right rails belong to Surface 1 (chrome), while Explorer
  and AI Assistant content panes belong to Surface 2 (content).
- **Embedded chrome components:** `GlassTopBar` and `GlassStatusBar` support `drawOwnChrome: Boolean = true`. When
  embedded in `GlassLayout` (`drawOwnChrome = false`), they render NO own background, shape clip, or border, allowing
  the parent chrome frame's single backdrop sample to show through seamlessly.
- **Surface distinction:** Both surfaces remain liquid glass with real GPU refraction/blur, differing in base color
  (`glassSurface` vs. `canvasSurface`) to provide >= 10 luma separation while maintaining ~3-5 luma uniformity across
  all chrome regions.

**Why:** independent `drawBackdrop` calls on separate toolbars and panels created a patchy, mismatched 15-luma
spread across the window. The two-glass-surface architecture guarantees visual uniformity for the entire chrome frame
while keeping the editor and tool window content distinctly framed on their own high-contrast glass canvas.

---

## Rule 9: base container shape/spacing/border changes require baseline regeneration + 2x
## fresh-daemon verification, and a passing screenshot diff is not proof a layout *reads correctly*

**The rule:** after any visual change to a shared base component (`GlassSurface`, `GlassCard`,
`GlassPanel`, `GlassTopBar`, `GlassStatusBar`, `GlassLayout`), regenerate affected baselines with
`UPDATE_SCREENSHOTS=true ./gradlew :core:designsystem:test`, then re-run normally and confirm
`0.0000%` diff across **two independent fresh Gradle daemons** (`./gradlew --stop` between runs) —
a single green run can still be a fluke of daemon state. Then **look at the actual baseline image**
yourself. A `0.0000%` diff only proves the render is *stable/deterministic* — it does not prove the
layout is visually correct, especially right after intentionally changing that exact baseline.

**Also:** a `0.0000%` diff on an *unrelated* baseline after a base-component change is not
automatically reassuring — check whether that specific screenshot actually exercises the changed
code path before treating the lack of diff as confirmation. (E.g. `testGlassPanelContainer` calls
`GlassPanelHeader(title = "EXPLORER")` with no `icon` argument, so it never exercised the icon-sizing
bug in Rule 6 at all — the passing diff there proved nothing about that specific fix.)

---

## Rule 10: a UI-facing setting (theme, effects, motion) is not "done" until it's threaded all the
## way to where `LtiTheme` is actually invoked

**The rule:** if you add a settings toggle for anything theme-related, trace it all the way from the
UI control to the real `LtiTheme(...)` call site(s) before considering it complete. A toggle bound to
correct-looking local state that nothing downstream reads is exactly as broken as no toggle at all.

**Why:** `SettingsScreen`'s Dark Mode and Fluid Glass Effects toggles were bound to
`remember { mutableStateOf(true) }` with nothing reading them. `LtiTheme` already had working
`darkTheme`/`effectsEnabled`/`reducedMotion` parameters, but `LtiSharedApp.kt` called
`LtiTheme(sharedBackdrop = backdrop)` without passing any of them. The fix is a session-scoped Koin
singleton (`AppSettingsState`, in `core:data`, registered the same way as `NetworkMonitor`/
`TimeZoneMonitor`) that both the settings UI and the `LtiTheme` call site inject — verify with a real
before/after screenshot diff of the actual rendered app across the toggle, not just that both ends
compile.

---

## Rule 11: Material3's dynamic `colorScheme.primary` tonal-maps any seed to a fixed, desaturated
## tone — a vivid seed color does NOT pass through as-is

**The rule:** don't assume changing `LtiSeedColor` to a more vivid/saturated hex makes
`glassAccent`/`focusRing` (or anything else sourced from `colorScheme.primary`) render that vivid
color. Verify what the *actual composited pixel* looks like, not just the seed constant.

**Why:** `com.materialkolor.dynamicColorScheme()` tonal-maps any seed to a fixed lightness/chroma
tone per M3 role (dark-mode `primary` lands around HCT tone ~80, regardless of the seed's own
tone/chroma) — correct for general M3 surface roles, but it silently desaturates a punchy brand
seed. Changing `LtiSeedColor` from `#0B84FF` to a vivid `#00E5FF` cyan produced `colorScheme.primary
= #82D3E1`, a muted pastel steel-teal — verified by probing `dynamicColorScheme(...).primary`
directly in a temporary test, not by eyeballing a screenshot. Fixed by adding
`deriveVibrantAccent(isDark)` in `Color.kt`, which samples the seed's own HCT **hue** at a
deliberately high, fixed chroma (90) and a punchier tone (75 dark / 45 light) instead of routing
through `colorScheme.primary` — the same direct-HCT technique `deriveSyntaxColorScheme` already
uses for syntax token colors. `glassAccent`/`focusRing` use this vivid derivation;
`glassTint`/`glassTintSecondary`/`glassBorderHighlight`/`glassHighlight` deliberately keep
`colorScheme.primary` since those are alpha-blended *overlays*, where the softer tonal value is
correct — only a token meant to render as a solid, recognizable brand color needs the vivid path.

---

## Rule 12: a blend mode can mute a vivid accent color even when the underlying color value is
## correct — check what's UNDER the tint, not just the tint's own hex

**The rule:** if an accent-tinted surface still looks muted/desaturated after confirming the accent
color itself is correct (Rule 11), check *how* the tint is composited onto its base surface, not
just what color it is.

**Why:** after fixing Rule 11, `GlassButtonVariant.Primary` (and `GlassIconButton`'s selected state,
which reuses the same variant) still rendered a muted steel-blue button while the logo badge —
using the identical `glassAccent`/seed color — showed genuine vivid light blue right next to it.
`drawButtonSurface()` in `GlassButton.kt` composited the tint with `blendMode = BlendMode.Hue`,
which deliberately preserves the *lightness and saturation* of the base surface underneath
(`glassSurfaceElevated`, a dark translucent gray) and only borrows the tint's hue — so the button
could never show the pure accent, no matter how vivid `glassAccent` itself was. This is a correct,
deliberate technique for `Secondary`/`Standard` buttons' subtle glass look, but wrong for `Primary`,
whose whole purpose is to read as the solid brand color. Fixed with a `vividTint` parameter on
`drawButtonSurface()` that switches to a full-strength `BlendMode.SrcOver` fill for
`variant == Primary` only — verify a "why is this muted" bug by checking the actual `blendMode` in
the draw call, not just re-deriving the color value again.

---

## Rule 13: when matching a reference screenshot's exact colors, derive real theme-aware tokens —
## never hardcode a raw hex pair that only covers the theme mode you're looking at

**The rule:** when asked to match a specific reference image's colors, resist reaching for
`Color(0xFF090909)` literals dropped straight into a component file. Find (or add) the equivalent
*generated* token in `theme/Color.kt`, covering **both** dark and light branches, even if you were
only shown a dark-mode reference.

**Why:** asked to match a reference screenshot's near-black outer chrome / charcoal inner-panel
look, the first attempt added `TestOuterChromeColor = Color(0xFF090909)` /
`TestInnerPanelColor = Color(0xFF18191B)` directly and wired them into `GlassLayout.kt`. This built
and passed guardrails (raw hex is allowed inside `theme/`), but broke the light theme completely —
`dynamicGlassColorScheme`'s light branch never got equivalent values, so light mode rendered with a
dark chrome frame around a correctly-light center panel. The real fix: probe the actual M3
surface-container ladder both themes generate (`surfaceContainerLowest` / `surfaceContainerLow` /
etc. — see `com.materialkolor.dynamicColorScheme(...)`'s output, verified with a temporary probe
test, not guessed) and add real `GlassColorScheme` fields (`chromeSurface`, and a repointed
`canvasSurface`) derived from that ladder in **both** `isDark` branches of
`dynamicGlassColorScheme()`. The dark-mode result (`surfaceContainerLowest` = `#0C0E13`,
`surfaceContainerLow` = `#191C20`) landed almost pixel-identical to the reference's `#090909`/
`#18191B` anyway — the seed-derived ladder already produces the right values, hardcoding just threw
away theme-reactivity for no visual gain.

---

## Rule 14: resizing a container token does not proportionally resize the content token inside it —
## check the ratio, not just that both tokens exist

**The rule:** when you bump a `ComponentSize.*` container/button-box token, check every
`IconSize.*`/content-size token rendered *inside* that container. A content size that was correctly
proportioned for the old container size does not auto-scale — it silently becomes disproportionate
inside the larger box.

**Why:** the side-panel icon rail's button size grew from `ComponentSize.TopBarButtonSize` (28dp) to
a dedicated `ComponentSize.SidePanelRailButtonSize` (36dp) to fix a real touch-target-too-small
issue. The icon glyph inside kept using `IconSize.Segmented` (15dp) — a value that was correctly
sized for the old 28dp box but left ~40% empty space in the new 36dp one, reading as a small,
pixelated icon floating in an oversized button. Fixed with a new, dedicated
`IconSize.SidePanelRail` (18dp) token, keeping roughly the original 15/28 ≈ 0.54 glyph-to-container
ratio instead of reusing `IconSize.Segmented` (which is still correctly sized for its *other* 28dp
call sites — the top bar's segmented toggles — and must not be bumped globally just because one
consumer's container grew). Verify by pixel-measuring the rendered glyph's actual extent against its
container box, not by confirming an `IconSize.*` constant is present (that alone doesn't prove the
proportion is still right).

---

## Before you finish: verification checklist for any theme/design-system change

1. `JAVA_HOME` is not set in a fresh shell — set it to `C:\Users\Mohammad\.jdks\jbr-21.0.11` first.
2. `./gradlew compileKotlinDesktop detekt verifyDesignSystemGuardrails` — real output, zero
   violations. Remember this does NOT catch Rule 1/6/7's failure modes (see Rule 2) — you still have
   to check those by reading the actual code and/or looking at real rendered output.
3. If any `core:designsystem` screenshot baseline could be affected:
   `UPDATE_SCREENSHOTS=true ./gradlew :core:designsystem:test`, then `./gradlew --stop` and run
   normally twice from independent fresh daemons, confirm `0.0000%` diff both times.
4. Look at the actual baseline image(s) yourself — don't just trust the diff percentage (Rule 9).
5. For anything alpha-dependent, don't trust `GlassContrastTest` alone (Rule 5) — sample real
   rendered pixels.
6. `runDesktopComposeUiTest` at most once per `@Test` method (multiple calls in one method has
   caused multi-hour Gradle hangs in this repo).
7. Never screenshot `GlassPerformanceMonitor` — its real infinite frame loop prevents the test
   harness from reaching idle.
8. `git status` at the end — confirm only the files you intended to change are modified, and that
   any throwaway diagnostic/test file has been deleted.
9. Changing an accent/seed color? Don't trust the seed constant alone — probe the actual composited
   `colorScheme.primary` (Rule 11) and check what blend mode any tint is drawn with (Rule 12); a
   correct color value can still render muted.
10. Matching a reference screenshot's exact colors? Derive real tokens covering both theme branches
    (Rule 13) — never a hardcoded hex pair that only happens to be right for the mode you were shown.
11. Bumped a `ComponentSize.*` container token? Check the `IconSize.*`/content token rendered inside
    it for the same ratio (Rule 14) — a content size that compiles and "has a value" isn't proof it's
    still proportioned correctly.
