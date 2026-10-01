# Jetpack Compose Performance Rules

Companion to [`rules/systemdesignrule.md`](systemdesignrule.md). That file is about *correctness*
(colors, borders, spacing — bugs a screenshot diff can catch). This file is about *performance*
(recomposition, allocation, phase discipline — regressions a screenshot diff can NOT catch, because
the pixels are identical and only the frame time changed).

Read this before editing anything that renders per-frame (glass effects, animations, lazy lists) or
anything inside a hot recomposition scope (list item rows, drag/press handlers, anything under a
`LazyColumn`/`LazyRow`).

Where a rule is already enforced somewhere in this repo, that's noted — don't restate the same rule
in two files, follow the pointer instead.

---

### 1. Composables must be cheap to call

A composable can run many times per second during scroll/animation. Don't do file I/O, network
calls, JSON parsing, or heavy computation directly in a composable body — hoist it into a
`ViewModel`, a `remember { }` block, or a `LaunchedEffect`. If a composable's cost can't be
described as "cheap," it doesn't belong in the composition — move the work out and pass the result
in as a parameter.

### 2. Mark data classes `@Immutable` / `@Stable` where true, and verify it

Compose's smart-recomposition skipping depends on being able to prove a composable's inputs are
unchanged. A class Compose can't prove is stable (e.g. a `var` property, a `List` instead of
`ImmutableList`) makes every composable that takes it as a parameter recompose unconditionally,
even when nothing changed. Annotate data classes passed into composables with `@Immutable` (all
properties genuinely never change after construction) or `@Stable` (properties can change, but
notify Compose via `MutableState`) — and only do so when it's actually true; a false annotation is
worse than none, because Compose will skip recomposition when it shouldn't.

Use Layout Inspector's recomposition counts (or the compose compiler's stability report,
`freeCompilerArgs += "-P plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=..."`)
to verify a class is actually being treated as stable before relying on it — don't just add the
annotation and assume it worked.

### 3. Don't fear recomposition — fear *unnecessary* recomposition

Recomposition itself is cheap; Compose's diffing is designed for it to run often. The actual cost is
composables that recompose when their visible output wouldn't have changed anyway. Don't
over-engineer to prevent all recomposition (extra `remember`s, `key()` wrapping, manual memoization
"just in case") — that adds allocation and complexity for no benefit. Optimize only where profiling
(Layout Inspector recomposition counts, or a slow/jank-y interaction reported by the user) shows a
composable recomposing more often than its output changes.

### 4. Use `derivedStateOf` when a computed value changes less often than its inputs

If a composable reads a value computed from frequently-changing state, but the *computed* value
itself only changes occasionally (e.g. "is the user scrolled past item 3" derived from a
continuously-updating scroll offset), wrap it in `derivedStateOf { }`. Without it, every scroll-pixel
update recomposes everything reading the derived value; with it, only an actual change in the
derived result triggers recomposition.

### 5. Give every `LazyColumn`/`LazyRow` `items(...)` a stable `key`

Without `key = { ... }`, Compose keys list items by position — inserting/removing/reordering an item
mid-list forces every item after it to recompose (state gets shuffled onto the wrong row) instead of
just moving. Pass a stable, unique identifier (an ID field, a path — not the item's index, not the
object's `hashCode()` if the object itself is a `data class` that gets reconstructed each recompose).

**Check before editing these — none currently pass a `key`:**
- `core/ui/.../GlassFileTree.kt` — `items(children) { node -> ... }`
- `feature/workspace/.../ai/AIAssistantPanel.kt` — `items(messages) { message -> ... }`
- `feature/setup/.../SetupScreen.kt` — `items(recentProjects) { project -> ... }`

These aren't flagged as *broken* — file trees, chat logs, and recent-project lists are typically
append/replace-whole-list operations where the absence of a key is harmless in practice. But if you
add reordering, filtering, or mid-list insertion/removal to any of them, add `key = { it.<stableId> }`
first, or you'll get state-swapping bugs (e.g. a selected/expanded row jumping to the wrong item).

### 6. Read rapidly-changing state as late (deferred) as possible — ideally not in the composable body at all

If a composable reads state that changes every frame (an animation progress `Float`, a drag offset,
a scroll position), reading it directly in the composable body forces that whole scope to recompose
every frame. Prefer:
- A lambda-based modifier parameter that reads the state internally (so only *that* modifier
  re-evaluates, not the whole composable) — e.g. `Modifier.graphicsLayer { alpha = progress }`
  instead of `Modifier.alpha(progress)`.
- State reads inside `drawWithContent`/`Canvas`/effect lambdas, which run in the draw phase and
  don't trigger recomposition at all.

**This repo's working example**: every `backdrop.drawBackdrop(...)` call's `effects = { }` /
`highlight = { }` / `layerBlock = { }` lambdas are exactly this pattern — animation state (e.g.
`GlassButton`'s `InteractiveHighlight` press progress) is read *inside* the lambda, so only the
draw phase re-runs on each animation tick, not the composable itself. See the
`kmp-liquid-glass` skill's "Animating a glass element" section for the full pattern. When adding a
new animated glass effect, follow this shape — don't hoist the animated value up into a `Modifier`
parameter computed in the composable body.

### 7. Know Compose's three phases, and keep expensive work in the latest phase that can do it

Composition (what to show) → Layout (where/how big) → Draw (pixels). Each phase re-runs
independently when only *its* inputs change — a value read only in Draw doesn't trigger Composition
or Layout. Prefer draw-phase modifiers (`drawWithContent`, `Canvas`, `graphicsLayer`) for anything
that's purely visual (color, alpha, blur, transform) over composition-phase state that forces a full
recompose.

**In this repo**: `Modifier.drawBackdrop`/`drawPlainBackdrop` (the entire glass-effect system) is a
draw-phase modifier by design — that's *why* the liquid-glass effects can animate every frame
without recomposing the surrounding UI. Don't undermine this by reading animated glass state one
level up in the composable body (see Rule 6).

### 8. Never trigger a side effect or write state directly in a composable body

A composable function can be called, skipped, or re-run by Compose at its own discretion — code in
the body must be a pure function of its inputs. Side-effecting calls (network requests, singleton
initialization, mutating external state) belong in `LaunchedEffect`, `SideEffect`, or
`remember { }` (for idempotent one-time setup), never as a bare statement in the composable body.

**This repo hit exactly this bug, as a crash, not just a slowdown**: `lti-desktop/src/jvmMain/kotlin/main.kt`
originally called `initKoin()` as a bare statement inside `nucleusApplication(args) { }`. Adding
`var showSettingsWindow by remember { mutableStateOf(false) }` at the same scope meant clicking
Settings recomposed that scope and re-ran `initKoin()` — Koin throws
`KoinApplicationAlreadyStartedException` on a second `startKoin()`. Fixed by wrapping in
`remember { initKoin() }` so it runs exactly once. Treat any bare non-composable function call
inside a composable body as a defect until proven idempotent and cheap.

### 9. Keep composables small and single-purpose

A large composable recomposes as one unit — if any part of its state changes, the whole function
re-runs, including the parts whose output didn't change. Splitting a large composable into smaller
ones lets Compose skip the parts that didn't need to change. This repo's component structure
(`GlassTopBar`/`GlassSecondaryTopBar` sharing `DesktopWindowControls`/`WindowControlButton` as
separately-composable units, rather than one monolithic top-bar function) already follows this —
keep new components similarly decomposed rather than adding large conditional blocks to an existing
composable.

### 10. Don't pass a large state-holder object when a composable only needs a few fields

Passing an entire view-model or state object into a composable makes Compose treat the composable as
depending on the *whole* object — any field changing anywhere in it invalidates every composable
that received it, even ones that only read an unrelated field. Destructure and pass only the
specific values (and callbacks) a composable actually reads.

Check any new composable taking `appSettingsState: AppSettingsState` (see
`core/data/.../settings/AppSettingsState.kt`) directly: if it only reads one of
`isDarkTheme`/`effectsEnabled`/`reducedMotion`, prefer passing that single `Boolean` instead of the
whole state object, unless the composable genuinely needs to react to all three.

### 11. Use `remember` correctly — cache expensive/identity-sensitive values, not everything

`remember { }` survives recomposition but is discarded on structural changes (a `key()` change, or
the composable leaving composition). Use it for: expensive computations, object identity that other
code depends on (a `Backdrop`, a `MutableState`, a coroutine `Job`), and anything referenced via
`==`/reference equality elsewhere. Don't wrap trivial constant expressions in `remember` — that's
pure overhead. This repo's own real bugs run in both directions: `remember { initKoin() }` (Rule 8,
too little caching originally) and CLAUDE.md's "Don't recreate the backdrop every recomposition" /
"Don't allocate backdrops inside recomposed blocks without `remember`" (too little caching on
`rememberLayerBackdrop()` call sites — see CLAUDE.md's Backdrop "❌ DON'T" list). Follow that
existing guidance rather than duplicating it here.

### 12. Be deliberate about image loading

Image decoding and scaling are expensive. Don't decode a full-resolution image just to display it at
thumbnail size — request an appropriately sized/sampled version. Cache decoded bitmaps so scrolling
past the same image twice doesn't re-decode it. If a dedicated image-loading library is introduced
to this repo (none currently is — check before assuming), let it own decoding/caching/sizing rather
than hand-rolling `painterResource` + manual `Bitmap` manipulation in hot paths.

### 13. Avoid nesting scrollable/lazy layouts; use `contentType` when items vary

A `LazyColumn` inside another scrollable container (or a `LazyRow` inside a `LazyColumn` item)
defeats Compose's item-recycling and measurement optimizations, and often causes double-measurement
or broken nested-scroll behavior. Flatten instead — use a single lazy list with mixed item types, or
`LazyVerticalGrid`/`span` where a grid is actually needed. When a lazy list mixes visually different
row types (e.g. a chat list with text messages, code blocks, and tool-call cards, or a file tree with
files vs. folders), pass `contentType = { ... }` in `items(...)` alongside `key` — this lets Compose
reuse layout/measurement nodes across items of the same shape instead of remeasuring from scratch
every time item types are interleaved. `AIAssistantPanel.kt`'s message list is the most likely
candidate in this repo if/when it grows multiple message-card shapes — check whether `contentType`
is warranted before adding a second visual message type there.

### 14. Don't allocate new `Modifier` instances (or lambdas that capture per-recomposition state) in hot paths

Every `Modifier.xxx(...)` chain call and every lambda literal is an allocation. In a composable that
recomposes frequently (list item rows, drag handlers, animated components), building a fresh
`Modifier` chain — or a fresh lambda closing over changing state — on every recomposition adds GC
pressure and can defeat Compose's modifier-node reuse. Hoist modifier chains that don't depend on
per-recomposition values out of the hot path (module-level `val` or `remember`), and prefer the
lambda-based effect/state-reading modifiers from Rule 6 over rebuilding a `Modifier` chain each time
a value changes.

**Already documented in this repo** — see CLAUDE.md's Backdrop "❌ DON'T" list: "Don't allocate
backdrops inside recomposed blocks without `remember`" and the Troubleshooting section's "Reduce
blur radius on frequently recomposed items" / "Share a single `Backdrop` instance across screens via
`LtiTheme(sharedBackdrop = ...)`." Same underlying rule — a `Backdrop` and its effect chain are
exactly the kind of hot-path allocation this rule warns about.

### 15. Shared/long-lived objects with mutable content require tracked snapshot reads for consumer invalidation — and writes MUST happen in `SideEffect`, never in `draw`

**The general pattern:** when a shared object with **stable identity across recompositions** (created
once via `remember`, held in a `CompositionLocal`, or shared at the root scaffold, e.g.
`LtiTheme(sharedBackdrop = ...)`) has its internal content mutated/re-recorded by a "producer" (e.g.
`CosmicAuroraBackdrop` drawing into a shared `GraphicsLayer`), and several "consumers" (every
`GlassSurface`, `GlassPanel`, `GlassTopBar`) sample from it:
- `@Stable` / `@Immutable` annotations do **NOT** solve this. They govern parameter comparison and
  skip semantics during **recomposition**, not reactivity for arbitrary field reads or offscreen graphic
  buffers at draw time.
- If consumer modifier nodes check element equality (`equals()`), the shared object's reference identity
  has not changed (`===`), so `update()` / `invalidateDraw()` will never be scheduled by Compose.
  Consumers continue sampling stale buffers until an unrelated layout event (like a window resize) happens
  to force a redraw.

**The concrete fix pattern (revision counter + `SideEffect`):**
- The shared object exposes a `mutableStateOf`-backed revision/version counter (`drawRevision` on
  `LayerBackdrop`).
- A public method (e.g. `LayerBackdrop.notifyContentChanged()` in
  `backdrop/src/{skiaMain,androidMain}/kotlin/com/kashif_e/backdrop/backdrops/LayerBackdrop.kt`, also declared
  on the `expect class LayerBackdrop` in `commonMain` because callers live in separate Gradle modules)
  increments this counter.
- The **producer composable** calls `notifyContentChanged()` inside a `SideEffect { ... }` block (see
  `CosmicAuroraBackdrop.kt`) — running once per genuine recomposition (e.g. on a dark/light theme flip).
- Every consumer's draw function reads this counter (`val revision = drawRevision` inside `drawBackdrop()`).
  Because the read occurs in the Draw phase, Compose snapshot tracking automatically subscribes the consumer
  node's draw phase to the revision state and schedules redraws whenever the counter changes.

**CRITICAL: Never write state or revision counters inside a `draw()`/`DrawScope` callback:**
- Writing a `mutableStateOf` (or calling `notifyContentChanged()`) directly inside
  `override fun ContentDrawScope.draw()` or any `DrawScope` lambda is a catastrophic regression trap.
- `draw()` re-runs on every single repaint of the frame for any reason. Writing a Compose `MutableState`
  during draw is a state write that marks the draw phase dirty and schedules another frame, whose `draw()`
  writes it again — creating a **self-sustaining infinite render loop**.
- In the running desktop application, this loop may not appear visibly broken because vsync-coalesced
  rendering repaints correct pixels forever while pegging a CPU core at 100%. But in offscreen and automated
  test harnesses, it hangs `waitForIdle()` indefinitely (`"Suspicious! waitForIdle has not finished after N seconds"`
  climbing past 40s, eventually killing the Gradle daemon under load).
- Signals that content has changed must **always** be written from `SideEffect` (tied to composition) or an
  explicit event handler, never from the draw phase.

**Beware `shouldAutoInvalidate = false` on custom `Modifier.Node`s:**
- Setting `override val shouldAutoInvalidate: Boolean = false` on a custom `DrawModifierNode` or
  `LayoutModifierNode` silently disables Compose's default "redraw when this node's input changed" behavior.
- In `LayerBackdropNode`, this flag combined with stable backdrop identity comparison in `equals()` to make the
  stale-theme bug completely invisible to normal tests.
- Note that `runDesktopComposeUiTest`'s `captureToImage()` forces a full redraw of the entire node tree
  regardless of invalidation state. A passing screenshot diff does **NOT** prove invalidation is wired
  correctly — it cannot distinguish a broken invalidation path from a working one, and only reveals the
  infinite render loop trap if the test hangs.

---

## Before you finish (performance-specific checklist)

- Does any new/edited composable do I/O, parsing, or heavy computation directly in its body? (Rule 1)
- Does any new data class passed into a composable deserve `@Immutable`/`@Stable` — and did you
  verify it's actually treated as stable, not just annotated? (Rule 2)
- Does any new `items(...)` call in a `LazyColumn`/`LazyRow` have a stable `key`? Does it need
  `contentType` if item shapes vary? (Rules 5, 13)
- Does any new animated/frequently-changing value get read inside a lambda-based modifier
  (`effects = { }`, `graphicsLayer { }`, `drawWithContent { }`) rather than in the composable body?
  (Rules 6, 7)
- Is there a bare, non-idempotent function call anywhere in a composable body that isn't wrapped in
  `remember { }`, `LaunchedEffect`, or `SideEffect`? (Rule 8 — this is the class of bug that crashed
  Settings; treat it as a blocker, not a style note)
- Does a new `Backdrop`/effect chain get created fresh on every recomposition instead of being
  `remember`ed or shared? (Rules 11, 14; CLAUDE.md Backdrop "❌ DON'T")
- When adding/reviewing a custom `DrawModifierNode`/`LayoutModifierNode`, or any code where one part of the
  app mutates a long-lived/shared object's content and another part reads it outside of normal recomposition:
  (a) is the read a tracked Compose state read (e.g. a `mutableStateOf` revision counter)? (b) does the
  corresponding WRITE happen at composition time (`SideEffect`) rather than inside a `draw()`/`DrawScope`
  callback? and (c) if `shouldAutoInvalidate = false` is used, is there a reliable invalidation path for all
  dynamic inputs? (Rule 15)

See also [`rules/systemdesignrule.md`](systemdesignrule.md) for the correctness-side rules
(colors, contrast, spacing) and its own "Before you finish" checklist — both apply to any glass
component edit.
