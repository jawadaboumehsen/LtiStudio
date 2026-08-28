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

## Rule 8: layout/spacing conventions for the app chrome — 12dp outer margin, 8dp inter-card gap

**The rule:** the top bar, status bar, and the 3-panel workspace cluster all use one consistent
spacing system: `Spacing.ExtraSmall` (4dp) as a base outer inset on the whole layout `Column`, plus
each floating card getting `Spacing.PanelPadding` (8dp) on the edge(s) facing the window's outer
edge and `Spacing.ExtraSmall` (4dp) on the edge(s) facing another card — so every seam in the layout
(bar-to-panel, panel-to-panel, cluster-to-window-edge) resolves to the same 12dp outer margin / 8dp
inter-card gap. If you add a new floating chrome element (another bar, another panel), match this
pattern rather than inventing new spacing values. See `GlassLayout.kt`'s `GlassLeftPanel`/
`GlassCenterColumn`/`GlassRightPanel` and the top/status bar `Box` wrappers for the reference
implementation, and note the conditional case: when a side panel is hidden, the element next to
where it would have been (main content's edge) switches from the inner 4dp to the outer 8dp, to keep
the 12dp-from-window-edge margin consistent regardless of which panels are visible.

**Why:** the top bar and status bar used to be flush, sharp-cornered, edge-to-edge rectangles while
the panel cluster floated with rounded corners and gaps — a real, user-reported "looks disconnected
from the app" defect, fixed by unifying the corner radius (`CornerRadius.Panel` on the bars, matching
the panels) and this spacing system.

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
