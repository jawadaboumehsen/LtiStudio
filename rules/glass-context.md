# Glass Material System (`GlassStyle` / `GlassStyles` / `Modifier.glass`)

Read this before adding a new `GlassStyles` role, migrating a component off `GlassEffects.*` onto
`Modifier.glass`, or touching `IdeTopAppBar`/`IdeStatusBar`/`IdePipelineRail`/`IdeNavigatorPanel`/
`Modifier.ideCardSurface` (the real production shell chrome). Companion to `CLAUDE.md` (token/
component catalog) and `rules/systemdesignrule.md` (content-color correctness); this file is about
the material system specifically — `GlassStyle`
(`core/designsystem/theme/glass/GlassStyle.kt`), the seven named roles in `GlassStyles.kt`, and
the single rendering facade `Modifier.glass` (`component/primitives/GlassModifiers.kt`). Full
design rationale: `tmp/backdrop-refactor-plan-20260920.md`.

Every rule below maps to a real bug caught during the migration, not a hypothetical.

---

## Rule 1: there are two different chrome systems in this app - know which one you're in

`GlassLayout`/`GlassTopBar`/`GlassStatusBar` (in `core/designsystem/component/layout/`) are real,
tested, working components — but they are **catalog- and test-only**. The actual shipped Studio
shell (`StudioScreen.kt` → `IdeAppFrame` → `StudioTopAppBar`/`GlobalAppTopBar` → `IdeTopAppBar`,
plus `IdePipelineRail`/`IdeNavigatorPanel` directly) is a separate, `IdeShellColorScheme`-driven
system. Before assuming a `Glass*` component is "the real thing," grep for its actual real-code
callers (`feature/`, not just `catalog/`) — `GlassLayout` has zero, `GlassCard` has 51.

**Why:** an hour was spent verifying `GlassLayout`'s migration against goldens before discovering
it doesn't render in the shipped app at all — real, correct, thoroughly-tested work, but the wrong
target. Checking real usage *first* (`grep -rl "Component(" feature/`) would have caught this
immediately.

---

## Rule 2: `Modifier.glass`'s `enabled` param exists specifically for `isBlueGlass`-style gating -
## use it, don't drop it

The real shell components (`IdeTopAppBar`, `IdeStatusBar`, `IdePipelineRail`, `IdeNavigatorPanel`,
`Modifier.ideCardSurface`) all follow the same pattern:

```kotlin
val isBlueGlass = GlassTheme.appTheme == AppTheme.Blue && GlassTheme.effectsEnabled
// real glass chrome only when isBlueGlass; Dark/Light always render flat, deliberately
```

This is real, load-bearing product behavior, not drift to unify away. When migrating a component
like this, pass `Modifier.glass(style, enabled = isBlueGlass)` — never just
`Modifier.glass(style)`, which would give Dark/Light themes real glass blur they were never
supposed to have.

**Verifying this matters**: a plain golden diff at default theme settings won't catch a broken
gate (it'll look fine either way at whatever theme the test happens to run under —
`LtiTheme(darkTheme = true)` resolves to `AppTheme.Blue` by default, which is *already* the
real-glass branch). Test both branches explicitly with `AppTheme.Blue` vs `AppTheme.Dark`, and
check the Dark-theme render is a **uniform, near-zero-variance fill** — not "still shows the raw
background," since these components' flat-fallback colors (`topBarBackground`, `cardBackground`,
...) are fully opaque, so a working gate and a broken one that also happens to be opaque both
"hide the stripes." See `IdeShellGlassChromeScreenshotTest` for the working pattern.

---

## Rule 3: a `GlassStyles` role's `tint`/`surface`/`shadow`/`rim` defaults are not free to invent -
## trace the real component's actual default chain first

`GlassCard`'s old `tint: Color = Color.Unspecified` fell through to `GlassSurface`'s own
substitution (`if (tint.isSpecified) tint else glassColors.glassTint`) — a real translucent layer
drawn on *every* card. `GlassStyles.card` initially defaulted `tint` to `Color.Transparent`
instead, silently dropping that layer. 8 goldens failed at 49–82% pixel diff before this was
caught — large enough to look like something was badly broken, not a one-line color swap.

Separately, `GlassStyles.navigator` was built from the catalog-only `GlassPanel`'s
`GlassEffects.Panel` (lens visible, depth+chromatic aberration on) — the real production
`IdeNavigatorPanel` actually uses `GlassEffects.DockedNav`, optically identical to `rail` (no
lens, `RectangleShape`). `navigator` now just delegates to `rail`.

**The rule:** before writing a `GlassStyles.x` role's values, find the *real* component that owns
that visual tier today and read its actual default-resolution chain end to end (not just its
`effects` param — `surfaceColor`/`tint`/`borderColor`/`highlight`/`shadow` too, including what the
*next* layer down substitutes when a param is left unspecified). Don't assume a value based on
which catalog component happens to have the most plausible-sounding name.

---

## Rule 4: `Modifier.glass`'s rim→border fallback only applies in the fallback path — a bug that
## shipped once already

`Modifier.glass`'s border-color resolution ("borrow `glassColors.glassBorderHighlight` when a rim
was requested and no explicit border is set") is documented as a *fallback-only* compensation —
the rim's shader already draws its own edge in the real backdrop path, so substituting a border
stroke there too draws a redundant, unintended outline on top of it. The first implementation
computed this unconditionally (outside the `effectsEnabled`/backdrop-available check), so it fired
in the real path too. Caught while tracing `GlassDialog`'s real behavior before migrating it, not
by a failing test — a full-perimeter stroke double-outlining a modal would have been visibly wrong.

**The rule:** if you touch `Modifier.glass`'s border/rim logic, gate any fallback-only
substitution on the *same* condition that selects the fallback background branch
(`usingRealBackdrop` in the current code), not on `rim != null` alone.

---

## Rule 5: not every `GlassEffects.*` stack maps onto the plan's 7 `GlassStyles` roles - don't
## force one that doesn't fit

`GlassStyles` has exactly 7 roles (`bar`, `rail`, `navigator`, `selected`, `card`, `button`,
`dialog`), matching `tmp/backdrop-refactor-plan-20260920.md`'s own table. `GlassChip`,
`GlassTextField`, `GlassToggle`, `GlassSlider`, and `GlassButton`'s own compact-icon variant
(`GlassEffects.Button`, distinct from `button`'s `ButtonPill`) all use real, distinct, tuned
optics tiers that don't match any of the 7. Several of these (`GlassChip`, `GlassButton`) also
have a *dynamic*, interaction-reactive highlight (`if (isHovered || selected) Highlight.Ambient
else null`) — `GlassStyle.rim` is a static value, not a lambda, so this doesn't fit the facade
either, by design (`Modifier.glass`'s whole point is a caller passing a value, not a lambda).

**The rule:** if a component's real effects don't match an existing role and/or its highlight is
interaction-reactive, don't force it onto the nearest-sounding role (this has produced two real
bugs already — Rule 3). Either it needs the `GlassButton` treatment (keep calling `drawBackdrop`
directly with its own highlight/layerBlock, only centralize the *static* optics portion through
`GlassStyles.x.toEffects()`), or the role set genuinely needs to grow — a real design decision,
not something to default into silently.

---

## Before you finish

- [ ] If you migrated a component off `GlassEffects.*`/raw `drawBackdrop`: did you check its real
      callers in `feature/*` (not just `catalog/`) actually exercise the new path? A component
      with zero real callers doesn't need this level of scrutiny; one with 20+ does.
- [ ] If the component had an `isBlueGlass`-style gate: did you preserve it via `enabled =`, and
      does a test actually exercise *both* branches (Blue and non-Blue), not just the default?
- [ ] Did you trace the real component's full default-resolution chain (surface, tint, border,
      highlight, shadow — not just `effects`) before writing the `GlassStyles` role's values?
- [ ] Real render test, not just a compile check: does a visible-blur assertion (or a saved
      golden you've actually looked at) prove the real backdrop path is reached, and does an
      opaque-fallback assertion prove the disabled/gated-off path renders correctly too?
