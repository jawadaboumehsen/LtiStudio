# Basic Mod Project

A standalone Kotlin author project for the patch-mod SDK. Requires JDK 21.
The checked-in Gradle wrapper supplies Gradle; no manually copied JARs are needed.

## Setup

Publish the SDK and its dependencies once from your LtiRomGui checkout:

```sh
./gradlew publishSdkToMavenLocal
```

Copy this entire template folder, including `gradle/`, both `gradlew` scripts and
hidden files, to a new project directory outside LtiRomGui. Open that directory
in IntelliJ IDEA. Dependencies resolve from Maven Local, Maven Central and Google.
`patchmodVersion` in `gradle.properties` must match the SDK version you published.
On macOS/Linux, run `chmod +x gradlew` if copying did not preserve permissions.
On Windows, use `.\gradlew.bat` for the commands below.

## Write, check and package

Edit `src/main/kotlin/Mod.kt`. The sample copies `assets/example.conf` to the
system partition as a demonstration. Replace its identity, settings, operations,
README and LICENSE with your own before installing a mod.
`Main.kt` derives the manifest identity and compatibility from the Kotlin plan;
edit its display name and license when customizing the project.

```sh
./gradlew generateModPlan
./gradlew validateMod testMod
./gradlew checkMod -PmodDecoded=/path/to/decoded -PmodTree=/path/to/work
./gradlew packageMod
./gradlew inspectMod
```

Every author task except inspection compiles and executes the Kotlin declaration
first. Generated manifest, plan and settings schema live in `build/mod-project/`;
packages and diagnostics live in `build/mod-project/build/`. Do not edit the
JSON files: the next generation replaces them. `clean` removes generated files.
`scenarios.json` tests default, disabled and invalid settings.

For `checkMod`, the decoded folder contains one directory per APK/JAR filename
(e.g. `services.jar/` containing its decoded files). The work tree contains one
directory per partition (`system/`, `vendor/`, etc.). `-PmodTree` is optional;
file operations are reported as not checked without it. Hooks and compiled-class
merges are also explicitly reported as not checked: this is not device validation.

For the unchanged sample only, a directory containing an empty `system/` folder
is enough for its copy check; pass an empty directory as `modDecoded`. The check
works in memory and does not write to the work tree.

Inspection reads the existing package without regenerating it. To inspect a
specific archive, use `./gradlew inspectMod -PmodPackage=/path/to/mod.lti-mod.zip`.
Packages are unsigned; this template does not provide a signing task.

## Optional Java hooks

Add `hooks/**/*.java` and a `payload.json` naming the target firmware JARs (see
the SDK's `examples/compiled-hook` project), then run these as separate commands:

```sh
./gradlew compileModPayload
./gradlew checkMod -PmodDecoded=/path/to/decoded -PmodTree=/path/to/work
./gradlew packageMod
```

The hook compiler is resolved separately through Maven and writes smali assets
to `assets/payload/`, which the next generation stages for packaging. Hooks use
Java because the framework process has no Kotlin runtime. Check the generated
package, install it through Studio's Plugins page in your ROM workspace, build
and flash through your usual workflow, then verify boot and hook behavior on the
target phone. Successful packaging or edit checks do not establish device behavior.
