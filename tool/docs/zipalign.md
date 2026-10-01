# Zipalign Tool Reference

## Metadata
- **Executable**: `zipalign`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `HOST_WRITE`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `3f6c73cdc3f61ccc1dcc55a77eecc2f798a5410998ff1d756f210ae73790d0d6`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-c` | `checkOnly` | `BOOLEAN` | check alignment only (does not modify file) |
| `-f` | `overwrite` | `BOOLEAN` | overwrite existing outfile.zip |
| `-p` | `pageAlign` | `BOOLEAN` | 4kb page-align uncompressed .so files |
| `-P` | `pageSizeKb` | `INT` | Align uncompressed .so files to specified page size (4, 16, 64) |
| `-v` | `verbose` | `BOOLEAN` | verbose output |
| `-z` | `zopfli` | `BOOLEAN` | recompress using Zopfli |

## Commands
### `align`
align or verify zip file

- **Subcommand argv path**: `_self`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `alignment` | `INT` | `true` | alignment in bytes (e.g. 4) |
| `infile` | `PATH` | `true` | input zip file |
| `outfile` | `PATH` | `false` | output zip file (optional in check-only mode) |
