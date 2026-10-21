# Basic Mod Project Template

This template provides a standalone author project for developing ROM mods:
- Open this project in IntelliJ IDEA to author mod plans and hook sources with IDE completion.
- Edit `src/main/kotlin/Mod.kt` using the `romMod { }` DSL.
- Hook Java code (in `hooks/`) is compiled only on the author's machine via `./gradlew compileModPayload`, against the target firmware's own jars named in `payload.json`, into smali that the runtime merges. Hooks are Java because the framework process has no Kotlin runtime.

## Author Workflow

1. **Open in IntelliJ IDEA**:
   Open this folder directly in IntelliJ IDEA. With `patchmod-sdk.jar` placed in `libs/` (or resolved via your repository), IntelliJ provides full code completion and type checking for the mod DSL.

2. **Validate the Mod**:
   ```bash
   ./gradlew validateMod
   ```

3. **Run Scenario Tests**:
   ```bash
   ./gradlew testMod
   ```

4. **Package the Mod**:
   ```bash
   ./gradlew packageMod
   ```
   This generates the `.lti-mod.zip` package ready for publication and installation.

5. **Compiled Hooks (Optional)**:
   If your mod includes Java hooks in `hooks/`:
   ```bash
   ./gradlew compileModPayload
   ```
   This compiles the Java hook sources against the firmware files `payload.json` lists (javac, d8 and baksmali, in-process) into smali payload assets. Hook sources are compiled strictly on the author's development machine.
