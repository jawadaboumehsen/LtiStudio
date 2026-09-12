# Fsck.erofs Tool Reference

## Metadata
- **Executable**: `fsck.erofs`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `HOST_WRITE`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `658c729534e87e55089eafd6a1ded7604a49ba7f109ac9358eb264fce0a6217d`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-a` | `A` | `BOOLEAN` | -y             no-op, for compatibility with fsck of other filesystems |
| `-d` | `D` | `STRING` | set output verbosity; 0=quiet, 9=verbose (default=2) |
| `-p` | `P` | `BOOLEAN` | print total compression ratio of all files |
| `--device` | `device` | `STRING` | specify an extra device to be used together |
| `--extract` | `extract` | `STRING` | check if all files are well encoded, optionally |
| `--force` | `force` | `BOOLEAN` | allow extracting to root |
| `--help, -h` | `help` | `BOOLEAN` | display this help and exit |
| `--offset` | `offset` | `STRING` | skip # bytes at the beginning of IMAGE |
| `--overwrite` | `overwrite` | `BOOLEAN` | overwrite files that already exist |
| `--version, -V` | `version` | `BOOLEAN` | print the version number of fsck.erofs and exit |

## Commands
### `execute`
Execute the tool with its options and positional operands

- **Subcommand argv path**: `_self`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `positionalArgs` | `STRING` | `false` | Tool-specific positional operands |
