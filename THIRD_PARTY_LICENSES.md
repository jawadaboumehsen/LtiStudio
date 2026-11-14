# Third-Party Licenses and Attributions

## IntelliJ Platform Icons (JetBrains Expressive UI)

The full set of 807 `ide*.svg` icon assets located under `core/designsystem/src/commonMain/composeResources/drawable/` are sourced from JetBrains' IntelliJ Platform Icons (`intellij.platform.ide.jar`'s `expui/` resources), licensed under the Apache License 2.0. A small subset of these files (spot-verified: 43) do not carry an inline per-file license comment but were individually confirmed against the public `https://github.com/JetBrains/intellij-community` repository to be genuine, unmodified files from that same Apache-2.0-licensed repository and directory.

- **Copyright**: Copyright 2000-2025 JetBrains s.r.o. and contributors.
- **License**: Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)

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
