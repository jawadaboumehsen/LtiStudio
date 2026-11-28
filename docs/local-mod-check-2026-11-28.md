# Local mod verification — 2026-11-28

The seven previously reported failures were double-application failures on an already modified fw-baseline work tree: HMA framework/services, GameAssist, GameSpace, SELinux policy hash, and two LogPersist policy anchors. The old assembly stamp dated November 15 preceded the modified framework JAR dated November 20. A positive receipt recorded 42 applied operations. Extracted inputs were intact: the stock framework had no HMA helper and the stock policy hash matched the mod's expected preimage.

Preflight previously let an Assemble/Debloat stage stamp override that positive receipt. Assembly also left the receipt outside its per-partition rsync destinations, so it survived restoring the input tree. The repair makes a positive or malformed receipt refuse a second application regardless of an older stage stamp. A successful assembly explicitly writes the zero-operation receipt after restoring and stamping the tree. An assembly-plan revision invalidates old assembly cache keys. Receipt parsing now reads the JSON operation count instead of matching an unformatted substring.

Focused regression validation passed: AppliedModulesReceiptTest (4), WorkTreeAssemblyStageTest (14), ModuleApplicationStageTest (7), zero failures or skips. Coverage includes formatted JSON, malformed/unknown receipts, nested zero counts, successful reset ordering, and invalid assembly plans not resetting the receipt. These are targeted tests, not a full suite.

The rebuilt app was launched with the managed Gradle workflow and JBR 21. Run-through was limited to Assemble. The preview showed Acquire and Extract reused from cache; acquisition was configured to import the existing firmware/downloaded/update.zip. Assembly completed successfully in 9 seconds of reported stage execution. Afterwards the receipt was exactly {"enabled":[],"operations":0}, and hashes of the six affected files matched firmware/extracted byte-for-byte. No firmware was downloaded and no phone was flashed.

The combined enabled-mod check was then run from Patches → Enabled ordered modules with APK/JAR decoding enabled. The running app displayed **42 checked · 4 left out**, with no refused or unchecked operations and no reported problems. All seven previous failures disappeared on the restored baseline. Four operations were excluded by their plan conditions; they are not claimed as tested. This is a firmware compatibility simulation, not a rebuilt-ROM boot test or phone installation.

Managed validation questions:

- “Do receipt parsing and assembly reset regressions pass without breaking assembly plans?” — passed, 25 targeted tests. An initial compilation failure accessing private receipt constants was corrected by sharing only the required constants within the module before rerunning successfully.
- “Does Gradle Studio reject the prepatched baseline, restore local assembly and check all enabled mods without the seven double-application failures?” — receipt refusal is covered by the automated tests; live Studio verified restoration and the 42-operation decoded check. The prepatched guard was not separately clicked in the running app before restoring it.

The patch packages themselves required no changes. At the end of that check, the workspace contained the freshly assembled, unpatched baseline. Code changes remain uncommitted.

## Actual Patches & mods application

On the subsequent requested run, Studio was launched again with JBR 21 through the managed Gradle `:lti-desktop:run` workflow. Run-through was explicitly limited to Patches & mods. Acquire, Extract and Assemble reused their cached local results; Debloat completed in 15 seconds. Patches & mods completed successfully in 548 seconds. Build & sign and release metadata were marked “Not in this run.”

The successful receipt contains all 13 enabled packages and 42 applied operations. The package set exactly matches the saved configuration; the operation count matches both the saved plan's operation summaries and operation IDs. The plan pins Keybox, PIF, PropHide and Settings to 1.4.1, with the other nine packages at 1.4.0. Every operation's expected output-presence/deletion check passed. ZIP integrity checks passed for all seven copied/rebuilt APK/JAR outputs: framework.jar, services.jar, GameAssist, GameSpace, LtiSettings, LtiUpdater and the system Settings APK. The workspace provenance is now MODULE_APPLICATION: work/ deliberately contains the applied mods.

The managed verification question was “Can the actual Patches and mods stage apply all enabled packages to local firmware?” — yes. Studio showed the successful stage, independent receipt/output checks passed, and the Gradle application exited normally with status 0. No further source/package repair was needed during this actual run. Existing Skiko version warnings remained. No firmware was downloaded, no image-build stage was run, and no phone was flashed. This verifies actual file mutation and component rebuilding, not device boot/runtime behavior.

The installed JDK's `jarsigner -verify` also reported “jar verified” with exit 0 for all five APK outputs. This checks their JAR signatures; it does not establish certificate trust or device compatibility. Neither framework JAR contained a signature block, so they are covered by successful assembly and ZIP integrity rather than a signature-verification claim.
