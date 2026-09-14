# Example Compiled Hook Mod

This mod demonstrates a compiled bytecode hook author project:
- Hook Kotlin source in `hooks/ExampleHook.kt` compiled by author tooling into smali bytecode.
- A `CompiledClassMerge` operation that merges the compiled smali classes into `/system/framework/services.jar`.
- A `HookInjection` operation whose anchor uses an opcode sequence and invoke signature to inject a call to `ExampleHook.onSignatureCheck`.

## Payload Configuration

The compilation pipeline is configured via `payload.json`:
```json
{
  "minRuntimeApi": 34,
  "frameworkStubJars": [],
  "dependencyJars": []
}
```

Because framework stubs are machine-specific, point `frameworkStubJars` to your local framework stub jar (for example, `<Android-SDK>/platforms/android-34/android.jar` or a framework stub jar extracted from your target ROM):
```json
{
  "minRuntimeApi": 34,
  "frameworkStubJars": [
    "/path/to/android-sdk/platforms/android-34/android.jar"
  ],
  "dependencyJars": []
}
```

## Compiling the Payload

Run the author compiler task:
```bash
./gradlew compileModPayload -PmodProject=examples/compiled-hook
```
This runs the `kotlinc -> d8 -> baksmali` pipeline to compile `hooks/*.kt` into smali bytecode in `assets/payload/` and generates `build/payload-receipt.json`.
