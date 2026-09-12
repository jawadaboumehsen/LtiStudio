# Img2sdat Tool Reference

## Metadata
- **Executable**: `img2sdat`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `HOST_WRITE`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `1f78bcbd577e620e23b750a99fb052c1c52e27c9c2ab560c60f7a17e51b03b57`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-B` | `blockListFile` | `PATH` | BLOCK_LIST_FILE, --block-list-file BLOCK_LIST_FILE |
| `-c` | `cacheSize` | `INT` | CACHE_SIZE, --cache-size CACHE_SIZE |
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `-o` | `outDir` | `PATH` | OUTDIR, --outdir OUTDIR |

## Commands
### `execute`
Execute the tool with its options and positional operands

- **Subcommand argv path**: `_self`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `positionalArgs` | `STRING` | `false` | Tool-specific positional operands |
