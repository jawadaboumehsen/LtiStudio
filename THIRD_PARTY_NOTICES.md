# Third-Party Notices

## Haze

- Repository: https://github.com/chrisbanes/haze.git
- Reference commit: `a9bc783540bd9dec472bab66057b4601586f09a3` (2026-09-19)
- License: Apache License 2.0

Haze source is vendored under `core/designsystem/haze/` in seven independent Gradle modules:
`core`, `utils`, `blur`, `glass`, `blur-material3`, `glass-material3`, and `materials`.
Their project paths use the `:core:designsystem:haze:` prefix. The container itself has no
implementation or aggregate dependency.

The sources retain their `dev.chrisbanes.haze.*` packages and existing license headers.
Relocating these modules does not change their rendering implementation or shaders.
