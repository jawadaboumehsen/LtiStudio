<div align="center">

# LtiRom Studio

**Build custom Android ROMs from stock firmware, in one desktop app.**

Import the firmware, choose what to remove and patch, and get a signed flashable zip or A/B OTA.
Every step runs for real in WSL Ubuntu, and you can see and check each one.

![Platform](https://img.shields.io/badge/platform-Windows%2011%20%2B%20WSL%202-0078D4)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose%20Multiplatform-7F52FF)
![JDK](https://img.shields.io/badge/JDK-21-ED8B00)
[![License](https://img.shields.io/badge/license-MPL%202.0-brightgreen)](LICENSE)

![The workspace overview: build readiness, target device and the last build](docs/screenshots/overview.png)

</div>

## Contents

- [Why LtiRom Studio](#why-ltirom-studio)
- [A tour of the app](#a-tour-of-the-app)
- [The ROM pipeline](#the-rom-pipeline)
- [Features by stage](#features-by-stage)
- [Help while you work](#help-while-you-work)
- [Toolkit](#toolkit)
- [Mods and the Kotlin SDK](#mods-and-the-kotlin-sdk)
- [Device configuration and the connected phone](#device-configuration-and-the-connected-phone)
- [Caching and fast rebuilds](#caching-and-fast-rebuilds)
- [Safety rules](#safety-rules)
- [Design and user experience](#design-and-user-experience)
- [Getting started](#getting-started)
- [Project layout](#project-layout)
- [Quality checks](#quality-checks)

## Why LtiRom Studio

Building a ROM by hand means juggling dozens of tools, unpacking images, editing files without breaking a single
permission line, and hoping the result boots. LtiRom Studio turns that into a guided workflow:

- **One place for the whole build:** from the stock firmware archive to the signed package and the release.
- **Nothing is faked:** progress, sizes, hashes and app lists come from the real files in your workspace. If a
  step has not run yet, the screen says which stage produces it.
- **It checks before it builds:** the build refuses a tree that would not boot (missing permissions or SELinux
  labels, broken links, an unsigned app, a full super partition) and tells you why, in plain words. Most refusals
  come with a one-click fix.
- **Undo is built in:** removed apps and replaced files are backed up as restore points.
- **No secrets stored:** packages you lack are shown as one command to run in your own terminal. The app never asks
  for your password.

> [!IMPORTANT]
> The package never contains firmware images (radio, bootloader) or vbmeta. Flashing the result is up to you.

## A tour of the app

### Setup: from a clean machine to a working toolchain

Setup checks WSL, installs the bundled build service with its own Java runtime, checks the system packages and
builds every tool side by side. You can pin any tool to a tag, branch or commit, or to your own fork, and switch
versions without breaking a running build.

| Workspaces | Tools and versions |
|---|---|
| ![Recent workspaces with search](docs/screenshots/workspaces.png) | ![Tool versions with licences and the version inspector](docs/screenshots/tools.png) |

### The Studio: one stage at a time

Each workspace opens in the Studio. The rail on the left shows the eight stages. A stage opens once everything before
it is valid, and the status bar always shows the workspace, open problems and current activity. An Output panel
under every screen logs each toolkit job with OK, INF, WRN and ERR counts.

| Run the pipeline | Build & Sign |
|---|---|
| ![Pipeline run with stage durations and verified artifacts](docs/screenshots/pipeline-run.png) | ![Partition images with sizes, savings and status](docs/screenshots/build-images.png) |

| Debloat with app details | Patches and mods |
|---|---|
| ![App inventory with icons, versions, signers and dependencies](docs/screenshots/debloat.png) | ![Installed plugins that can be enabled per workspace](docs/screenshots/patches.png) |

## The ROM pipeline

| # | Stage | What it does |
|---|---|---|
| 1 | **Acquire** | Downloads or imports the stock firmware and checks its SHA-256 |
| 2 | **Extract** | Unpacks the firmware container and reads each partition image |
| 3 | **Assemble** | Builds the work tree, boot images, mount tables and build properties |
| 4 | **Debloat** | Removes the apps you pick, after checking what the device still needs |
| 5 | **Patches / Mods** | Applies installed mods (Kotlin SDK plugins) and project scripts |
| 6 | **Build & Sign** | Builds the partition images and super, signs the boot chain, writes the flashable zip and/or A/B OTA |
| 7 | **Release Metadata** | Writes the OTA manifest and changelog |
| 8 | **Publish** | Exports locally or publishes a GitHub release, after you confirm |

A run can cover the whole build or stop after any stage ("Run through"). Publish is never part of a run: it starts
only from its own confirmation dialog.

## Features by stage

### 1. Acquire

- Download by URL or import an archive from Windows; the copy happens inside WSL.
- The archive's SHA-256 is checked against the device configuration.
- The format is read from the file's first bytes, never from its name.

### 2. Extract

- Containers: zip, 7z and RAR, tar with gz, xz, lz4 or zstd, `payload.bin`, sparse images, block OTAs and Samsung
  Odin `.tar.md5`.
- Filesystems: EROFS and ext4 are mounted read-only, F2FS is dumped. The filesystem comes from the image superblock.
- Incremental OTAs, NTPI and unknown formats are refused with a plain reason. An image of zeros or with no known
  filesystem stops the build (`EMPTY_PARTITION`, `UNKNOWN_FILESYSTEM`).
- Replace any dynamic partition with your own EROFS, ext4 or F2FS image, pinned by its SHA-256.
- Re-extract one partition without running the whole stage.
- An archive browser shows any archive and the archives inside it, with sizes and compression methods.

### 3. Assemble

- Copies each partition into the work tree with its `fs_config` and `file_contexts` sidecars.
- Optionally folds `system_ext` into system, and purges stale precompiled (AOT) files.
- **Build type and properties:** `user`, `userdebug` or `eng`, plus your own `build.prop` overrides.
- **Boot preparation:** unpack and repack boot, vendor_boot and init_boot, edit the ramdisk and the device tree,
  re-sign with the workspace AVB key, patch with APatch, or import an image patched with Magisk on the phone.
- **Kernel facts:** version, page size, which EROFS compressions, F2FS compressions and ramdisk formats the kernel
  reads, dm-verity and fs-verity, and hardening such as CFI, KASLR, PAC and MTE.
- **Mount table:** every fstab as rows; remove verity or encryption flags one by one, add EROFS, ext4 and F2FS lines
  for each logical partition, or turn off `/data` encryption.
- **vbmeta flags:** four verified-boot modes described in words. The edited copy is never packaged.
- **Image browser:** browse any partition tree or any image file of the workspace, search across all partitions,
  see owners, modes, capabilities and SELinux labels, and rename or delete entries (kept as edits that every build
  applies).

### 4. Debloat

- **Inventory:** every app of the assembled tree with its name, icon, version, target SDK and signer, read from the
  manifests in about 7 seconds for a whole ROM.
- **Risk review:** an app the device needs (the last home screen, keyboard or camera, or one that provides a library
  a kept app needs) cannot be removed. Persistent and system-UID apps are warned about.
- **App map:** what relies on an app and what it relies on, coloured by what would be lost.
- **Suggestions:** apps nothing in the ROM relies on or looks for, that are not system apps and do not start at boot,
  largest first.
- **Presets and community lists:** save picks by package name so they work on another firmware, and import plain
  lists or Universal Android Debloater's `uad_lists.json` with its ratings shown next to each app.
- **Per phone:** your picks are remembered per device and offered to a new workspace of the same phone.
- Permission and sysconfig files that name only removed apps are removed with them. Every removal is a restore point.

### 5. Patches and mods

- Enable installed mods per workspace and set their order.
- A mod changes files, properties, text, smali methods and XML resources. Each change is checked against its anchor
  before it is applied.
- Project scripts run after the mods, each checked with `sh -n` first.
- New files get owners, modes and SELinux labels from the ROM's own rules, so they need no hand-written sidecar
  lines.
- **Compare:** two trees, two images or two files, from any workspace: added, removed and changed files, superblocks
  side by side, text diffs, zip entries, and changed methods inside JARs and APKs.
- **Deodex:** removes stale precompiled files, with a dry run and restore points.

### 6. Build & Sign

- **Filesystems:** EROFS, ext4 or F2FS per partition. EROFS takes lz4, lz4hc, lzma, deflate or zstd, a level,
  deduplication and a cluster size, and only the compressions the boot kernel can read are offered.
- **Keep as shipped:** package a partition from its stock image instead of building it, after checking that nothing
  in it changed.
- **Every image is checked:** mounted and compared with the work tree (files, owners, modes, labels), `fsck`-checked,
  and given an AVB hashtree footer.
- **Partition layout:** the super group budget with each image's size, and a size advisor that trial-builds a
  partition a few ways and recommends one.
- **Packages:** a flashable zip, an A/B OTA, or both. The A/B OTA is a full, signed payload for Virtual A/B devices.
- **Extra packages:** a fastboot images zip, Odin AP tar or LZ4 images, and the bare `payload.bin`, each with a
  `.sha256`.
- **Coverage:** finds work-tree files with no permission or SELinux line and fills them in from your rules or from
  their folder.

### 7. Release metadata

- Channel, OTA URL, compatibility and the generated `manifest.json`.
- **Changelog draft:** written from what the last build really did: the mods applied and the files they changed, the
  apps removed and the space freed, and the build type and properties.

### 8. Publish

- **Local export:** copies the package and manifest, verifies the hashes and writes a receipt.
- **GitHub:** a release with `gh` signed in inside WSL, large assets split into parts, remote hashes verified, and the
  OTA manifest committed to your manifest repository.
- A journal keeps every step, so publishing again resumes where it stopped.

## Help while you work

- **Will it build?** The Overview runs Build & Sign's own checks against your current settings and work tree in
  seconds, without building, and checks each enabled mod against the assembled tree.
- **Fix it buttons:** most refusals come with a fix that changes the setting for you or opens the screen that fixes
  it.
- **Why it ran:** the Run panel says why a stage ran instead of coming from cache, for example
  `build.brotliLevel: 6 → 7`, or which output file a later stage edited.
- **Same phone:** when you move to a new firmware of the same phone, take the assembly, debloat, mods and build
  settings of the earlier workspace in one step. What belongs to the old firmware stays behind.

## Toolkit

Beyond the stages, the toolkit works on the workspace directly. Each action is a real job with its own log entry.

| Area | What it does |
|---|---|
| Boot images | Unpack, edit, repack and re-sign; device-tree editing with a round-trip check; changes since unpack listed |
| Images | Browse any EROFS, ext4, F2FS or super image without unpacking it; superblocks side by side |
| Archives | Browse nested archives and copy selected members out |
| Compare | Trees, images, files and decoded code across workspaces |
| Device vs firmware | `build.prop` identity, boot-chain sizes and the dynamic partition list next to the device configuration |
| Restore points | Every debloat removal and replaced file can be put back |
| Disk usage | Space per workspace folder; free what the next build makes again |

## Mods and the Kotlin SDK

Mods are Kotlin projects built on the `sdk:patch-mod` SDK and packaged as `.lti-mod.zip`.

- Install them from a file, an HTTPS index or a GitHub repository's latest release. Downloads are checked against
  their SHA-256 before they are read.
- Smali edits are written as method edits that match by name, parameters, strings, references or opcodes, so they
  survive firmware updates better than line-based patches.
- Hook classes are written in Java and compiled to smali against the firmware's own classes.
- `patchmod checkMod` checks a mod against a decoded firmware and an assembled tree with the same engine the build
  uses.
- A mod declares the devices and firmware versions it supports, and the Studio will not enable it elsewhere.

First-party mods live in a separate repository, [LtiRomMods](https://github.com/jawadaboumehsen/LtiRomMods).

## Device configuration and the connected phone

- Each workspace has one device configuration, `device.json`, stored in the workspace folder so it travels with it.
- A build starts only when the device configuration and every stage are valid; the Run panel lists what is missing.
- **Connected phone (read only):** a phone attached to WSL through usbipd-win is read with adb and fastboot: its
  partitions, slot, lock state and properties, next to your configuration with "Use phone value". A flash plan maps
  your images to its partitions and refuses anything unsafe, but the app never writes a partition.

## Caching and fast rebuilds

- Each stage is cached by its inputs: settings, tool versions, input files and the device.
- Assemble, Debloat and Patches come from cache together when nothing changed, because a mod or a debloat must never
  be applied twice to one tree. A rebuild with no changes takes about 90 seconds instead of about an hour on the test
  firmware.
- An EROFS image whose contents and settings are unchanged is reused instead of rebuilt, and still goes through the
  same verify step.
- Tool versions are pinned per run, so switching a tool mid-build affects only the next run.

## Safety rules

- Firmware images (radio, bootloader) and vbmeta are never packaged.
- Identity, calibration and key partitions are never written, and the phone panel only reads.
- Outward actions (publishing, deleting, replacing settings) ask first.
- Closing the app during a build asks first, and an interrupted build never applies a half-finished step again.
- The app checks for symbolic links in its own folders before it writes, so a firmware link cannot redirect a write.

## Design and user experience

LtiRom Studio is built like a professional IDE: dense where you work, calm everywhere else.

- **Glass design system.** Window chrome, rails and controls use real blur and refraction (the vendored
  [Haze](https://github.com/chrisbanes/haze) library). Long content stays on readable, solid surfaces.
- **Themes and accents.** Blue Glass, Dark and Light, six accent colours, Comfortable or Compact density, and text
  at 100, 110 or 125%. A live preview shows each change before you leave Settings.
- **Accessible by rule.** Text colours are checked against WCAG AA contrast in tests, every focusable control has a
  visible focus ring, and turning off "Fluid animations" snaps motion instead of playing it.
- **Clear feedback.** Mistakes are shown next to the field that caused them ("A commit is 40 hex characters"),
  locked stages say what to finish first, and outward actions always ask first.
- **Keyboard friendly.** `Ctrl+O` opens a workspace, `Ctrl+N` creates one, `Ctrl+F` searches, and `Esc` closes
  panels.
- **One source of truth for style.** Colours, spacing, sizes, opacity and motion are design tokens. Lint rules and a
  Gradle guardrail reject raw values in feature code, so the app stays consistent as it grows.

## Getting started

### Requirements

- Windows 11 with WSL 2 and an Ubuntu LTS distribution
- JDK 21 to build the app
- `LtiRomServer` checked out next to this repository (it is included as a
  composite build):

  ```text
  LtiRomProject/
  ├── LtiRomGui/      (this repository)
  └── LtiRomServer/   (build service that runs inside WSL)
  ```

### Build and run

```bash
./gradlew :lti-desktop:run
```

This also builds the bundled build service. Installers come from the `package*` tasks of `:lti-desktop`.

### First build

1. **Setup:** let the check finish. If packages are missing, run the shown command in your terminal and press
   "I've run it, check again".
2. **New workspace:** pick a device template.
3. **Acquire:** import your stock firmware archive.
4. **Overview:** press "Check now" under "Will it build?" and apply any fixes it offers.
5. **Run:** open Run history, choose "Whole build" (or stop after a stage) and press Start Pipeline.
6. Find the package and its SHA-256 under **Artifacts**.

## Project layout

| Path | Contents |
|---|---|
| `core/` | `model`, `domain` (pipeline, stages, workbenches), `data`, `datastore`, `ui`, `designsystem` (glass theme on vendored Haze) |
| `feature/` | `setup`, `workspace-*`, `configuration`, `plugins`, `settings`, and `rom-studio/` (the shell and one module per stage) |
| `sdk/` | `patch-mod` (the mod SDK and `patchmod` CLI), `patch-mod-runtime`, `patch-mod-hooks` |
| `lti-desktop/`, `lti-shared/` | The desktop app |
| `tool/`, `detekt-rules/`, `build-logic/` | Tooling, custom lint rules and Gradle conventions |

## Quality checks

The pre-push hook runs these, and they must pass before a push:

```bash
./gradlew detekt verifyDesignSystemGuardrails checkArchitecture --continue
```

Tests: `./gradlew :core:domain:desktopTest` (and the matching task of each module you change). Pipeline logic is
tested against real tool output from sample firmware.

Contributor notes on architecture, the design system and each pipeline stage are in [CLAUDE.md](CLAUDE.md).

## License

[Mozilla Public License 2.0](LICENSE).
