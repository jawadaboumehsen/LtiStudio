# LtiRom Studio

A Windows desktop app for building custom Android ROMs. You configure and run the build in the app. The work runs in a
build service inside WSL Ubuntu.

Built with Kotlin and Compose Multiplatform (desktop/JVM). Licensed under the [MPL 2.0](LICENSE).

## What it does

- **Setup:** takes a clean Windows machine with WSL Ubuntu to a working toolchain. It installs the bundled build
  service, lists any missing apt packages as one command for you to run in your own terminal, and builds the tools
  (android-tools, erofs-utils, f2fs-tools, apktool, signapk, gh, payload-dumper-go and others) side by side in
  `~/LtiRomTools`.
- **Workspaces:** each workspace is a folder in the WSL work directory holding its device configuration
  (`device.json`), its settings (`config.json`), the firmware and the build outputs.
- **ROM pipeline:** eight stages, each with its own editor in the Studio:

  | # | Stage | What it does |
  |---|---|---|
  | 1 | Acquire | Downloads or imports the stock firmware and checks its SHA-256 |
  | 2 | Extract | Unpacks zip, 7z, tar, payload.bin, block OTA, sparse and Odin firmware, then mounts or dumps the EROFS, ext4 and F2FS images |
  | 3 | Assemble | Builds the work tree, boot images, mount tables and build properties |
  | 4 | Debloat | Removes the apps you pick, after checking what the device still needs; backups can be restored |
  | 5 | Patches/Mods | Applies installed mods (Kotlin SDK plugins) and project scripts |
  | 6 | Build & Sign | Builds the partition images and super, signs the boot chain, writes the flashable zip and/or A/B OTA |
  | 7 | Release Metadata | Writes the OTA manifest |
  | 8 | Publish | Exports locally or publishes a GitHub release, after you confirm |

  Each stage is cached by its inputs, and a run can stop after any stage.
- **Toolkit:** boot image unpack and repack, kernel facts, an image and archive browser, compare tools, a device vs
  firmware check, sidecar (`fs_config`/`file_contexts`) coverage, and restore points.

The package never contains firmware images (radio, bootloader) or vbmeta. Flashing the result is up to you.

## Requirements

- Windows 11 with WSL 2 and an Ubuntu LTS distribution
- JDK 21 to build the app
- `LtiRomServer` checked out next to this repository (it is included as a composite build):

  ```text
  LtiRomProject/
  ├── LtiRomGui/      (this repository)
  └── LtiRomServer/   (build service that runs inside WSL)
  ```

## Build and run

```bash
./gradlew :lti-desktop:run
```

This also builds the bundled build service. Packaged installers come from the `package*` tasks of `:lti-desktop`.

## Modules

- `core/`: `model`, `domain` (pipeline, stages, workbenches), `data`, `datastore`, `ui`, `designsystem` (glass
  theme on the vendored Haze library).
- `feature/`: `setup`, `workspace-*`, `configuration`, `plugins`, `settings`, and `rom-studio/` (the shell and one
  module per stage).
- `sdk/`: `patch-mod` (the mod SDK and `patchmod` CLI), `patch-mod-runtime`, `patch-mod-hooks`.
- `lti-desktop/`, `lti-shared/`: the desktop app.
- `tool/`, `detekt-rules/`, `build-logic/`: tooling, custom lint rules and Gradle conventions.

First-party mods live in a separate repository, [LtiRomMods](https://github.com/jawadaboumehsen/LtiRomMods).

## Checks

The pre-push hook runs these, and they must pass before a push:

```bash
./gradlew detekt verifyDesignSystemGuardrails checkArchitecture --continue
```

Tests: `./gradlew :core:domain:desktopTest` (and the matching task of each module you change).

Contributor notes on architecture, the design system and each pipeline stage are in [CLAUDE.md](CLAUDE.md).
