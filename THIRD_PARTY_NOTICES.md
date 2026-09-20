# Third-Party Notices

## Haze

- Repository: https://github.com/chrisbanes/haze.git
- Reference commit: `a9bc783540bd9dec472bab66057b4601586f09a3` (2026-09-19)
- License: Apache License 2.0

`backdrop/` (this project's own glass-morphism rendering engine, `com.kashif_e.backdrop`) is an
independent implementation. It does not depend on, embed, or redistribute Haze's compiled
artifacts or source tree. A small number of algorithms and design policies were read from the
Haze reference commit above and reimplemented locally, adapted to this engine's own API shape.
Each adapted file credits Haze in its own header comment. As of this notice:

- `backdrop/src/commonMain/kotlin/com/kashif_e/backdrop/OpticalSizeValue.kt` — the
  `Fixed`/`Responsive` optical-value model and shortest-side interpolation policy, adapted from
  Haze's `OpticalSizeValue`.
- `backdrop/src/commonMain/kotlin/com/kashif_e/backdrop/DrawBackdropModifier.kt` and
  `backdrop/src/skiaMain/kotlin/com/kashif_e/backdrop/DrawBackdropModifier.kt` — the reduced-
  resolution input-sampling policy (`inputScale`), adapted from Haze's `GlassInputScalePolicy`.

No other files in this repository are derived from Haze. This notice should be updated whenever a
new file is adapted from the Haze reference commit, per `tmp/backdrop-refactor-plan-20260920.md`.
