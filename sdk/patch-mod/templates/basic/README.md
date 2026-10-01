# Basic Mod Project Template

This template provides a standalone author project for developing ROM mods:
- Open this project in IntelliJ IDEA to author mod plans and hook sources with IDE completion.
- Edit `src/main/kotlin/Mod.kt` using the `romMod { }` DSL.
- Hook Kotlin code (in `hooks/`) is compiled only on the author's machine via `./gradlew compileModPayload` into smali bytecode that the runtime merges; the target Android framework process has no Kotlin runtime.

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
   If your mod includes Kotlin hooks in `hooks/`:
   ```bash
   ./gradlew compileModPayload
   ```
   This compiles Kotlin hook sources against local Android framework stubs using `kotlinc -> d8 -> baksmali` into smali payload assets. Hook sources are compiled strictly on the author's development machine.
