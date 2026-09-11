# Vendored recovery updater

`META-INF/com/google/android/update-binary` is a prebuilt recovery updater and is **not** generated
by the product. Place the real binary from a known-good package for the target here:

- `update-binary` — the prebuilt (binary, not a script)
- `update-binary.sha256` — `sha256sum update-binary` output (first token is the pin)

`ResourceVendoredFileAdapter` refuses to load the file unless the digest matches the pin, and the
BUILD_FLASHABLE_ZIP stage fails with a remediation message when it is absent. No stub is ever
packaged.
