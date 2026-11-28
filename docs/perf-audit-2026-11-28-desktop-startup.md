# Desktop startup and import audit — 2026-11-28

Measured the Windows Gradle-launched application with JBR 21 and JFR profile recordings. These are diagnostic development runs, one launch per configuration, with existing local workspace data. They are not release benchmarks or a complete audit of every application path. No firmware was downloaded.

## Findings and repairs

1. Dependency initialization and initial preferences resolution ran inside Compose composition on the AWT event thread. The preferences file was approximately 5.7 MB; baseline JFR sampled its parsing on the event thread. Initialization now completes on the entry thread before the AWT application starts, passing the persisted settings into the UI. This removes that synchronous work from the event loop while preserving the saved first-frame theme; it does not promise less total startup work.
2. Setup refreshed tool inventory for every toolchain-state emission after server connectivity succeeded, including progress changes during verification. Verification itself also scans tools. Refresh now waits for completed verification and reacts only to readiness transitions. A regression test verifies zero scans during checking, one after completion, no extra scans for five progress updates, and another scan after a subsequent completed check.
3. Dismissing or replacing a package-update review left its lab-check coroutines running. These jobs are now tracked under the existing view-model scope and cancelled when the review is dismissed or replaced. Existing identity checks still reject late UI results. Cancellation of every external process has not been independently measured.
4. Successful package import cleared the selected archive and displayed the empty selection prompt with an unfinished stepper. A dedicated installed state now shows package identity and three completed steps; selecting another archive resets the flow. A desktop rendering regression test covers completion and reset.

## Launch observations

Times are milliseconds from the application's startup marker, excluding Gradle compilation and launch overhead.

| Event | Before | After |
| --- | ---: | ---: |
| Koin initialization starts | 365 | 19 |
| Koin initialization completes | 513 | 230 |
| Preferences resolved before UI | not separately instrumented | 330 |
| Main-window composition | 1746 | 2123 |
| Native window attached | 2447 | 2779 |
| First layout | 2756 | 3106 |
| First draw | 2874 | 3244 |
| Environment verification completes | 19354 | 10503 |

First draw was 370 ms slower in the second run. Environment verification finished 8.851 seconds earlier, but WSL, OS caches and warm-up differ between launches. Neither delta establishes a causal or statistically significant speedup. JFR adds profiling overhead. The after recording had no sampled preference-parser stacks; sampling absence alone is not proof, while entrypoint ordering and the preferences timestamp establish that initial resolution precedes window creation.

## Automated validation

Targeted checks passed: SetupViewModelTest (19), ImportCompletionTest (1), PluginManagerViewModelTest (7), and AppSettingsStateWiringTest (5): 32 tests, zero failures or skips. The eight plugin tests passed again after adding review cancellation. This was not a full-suite run. Existing unrelated working-copy changes were preserved.

Application recordings and logs remain under ignored build/startup-audit/. Android Macrobenchmark, R8 and Baseline Profiles do not measure this Windows desktop target. Repeated release-mode launches and longer scrolling traces remain necessary before claiming production performance gains or exhaustive coverage.

## Live import and update verification

The Gradle-launched app inspected and installed local PIF and Settings 1.4.1 archives. Inspection reported SDK/content checks passing and dependencies resolved; both packages are unsigned, with the local-source trust decision shown explicitly. PIF completion displayed its identity and all three completed steps. Keeping current versions dismissed its review. Selecting the Settings archive reset the import flow; installing it opened the combined review again.

The review displayed Keybox patch-content changes, PIF's exact Keybox dependency update, PropHide script/binary changes, and the Settings APK change. Its local fw-baseline check completed with 23 candidate operations checked and seven existing failures: two HMA, two Game Assist, one SELinux preimage mismatch, and two LogPersist anchors. No new failures were reported. This is not a clean build or device validation.

Applying the reviewed update displayed `fw-baseline: updated`. A separate read of config.json confirmed Keybox, PIF, PropHide and Settings all pinned to 1.4.1. No ROM build, phone installation, flashing or firmware download was performed. The profiled app closed normally and the managed Gradle `:lti-desktop:run` exited successfully. Existing Skiko-version warnings remain uninvestigated; they also appeared before these changes.
