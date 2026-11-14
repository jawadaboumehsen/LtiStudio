# Contributing to LtiRom Studio

Thanks for taking the time to help. LtiRom Studio is a desktop app that builds custom Android ROMs from stock
firmware; changes here can end up in a phone's system partition, so correctness matters more than speed.

## Reporting a problem

Open an issue with:

- what you did, what you expected, and what happened;
- the device and firmware, when it is about a build;
- the build log or the Output panel text, when there is one.

Never attach firmware images, signing keys or anything from `keys/`.

## Setting up

You need Windows 11 with WSL 2 (Ubuntu LTS), JDK 21, and `LtiRomServer` checked out next to this repository:

```text
LtiRomProject/
├── LtiRomGui/      (this repository)
└── LtiRomServer/   (the build service that runs inside WSL)
```

Then run the app with `./gradlew :lti-desktop:run`. The first start walks you through Setup.

## Making a change

- Keep the module boundaries: domain logic in `core/domain`, platform code in `core/data`, screens in `feature/*`.
  `./gradlew checkArchitecture` enforces them.
- UI uses the design system's tokens and `Glass*` components only; raw colours, sizes and Material components are
  rejected by `./gradlew verifyDesignSystemGuardrails` and detekt.
- Every change to pipeline logic gets a test, written against real tool output where a tool is involved.
- Run the checks before you push (the pre-push hook runs the first three):

  ```bash
  ./gradlew detekt verifyDesignSystemGuardrails checkArchitecture --continue
  ./gradlew :core:domain:desktopTest   # and the desktopTest of each module you touched
  ```

- Never regenerate a detekt baseline to hide a new finding; fix it.

Architecture notes, the design system and each pipeline stage are described in [CLAUDE.md](CLAUDE.md).

## Pull requests

Describe what changed and why, link the issue, and add screenshots for anything visible. One topic per pull request.

## Licence

By contributing you agree that your work is released under the [Mozilla Public License 2.0](LICENSE).
