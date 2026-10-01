# Dump.erofs Tool Reference

## Metadata
- **Executable**: `dump.erofs`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `READ_ONLY`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `f0ebc440713aaf0f0960d9bff97b570de185bb73524c10b85752c55c60716c39`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-e` | `E` | `BOOLEAN` | show extent info (INODE required) |
| `-S` | `S` | `BOOLEAN` | show statistic information of the image |
| `--cat` | `cat` | `BOOLEAN` | show file contents (INODE required) |
| `--device` | `device` | `STRING` | specify an extra device to be used together |
| `--help, -h` | `help` | `BOOLEAN` | display this help and exit |
| `--ls` | `ls` | `BOOLEAN` | show directory contents (INODE required) |
| `--nid` | `nid` | `STRING` | show the target inode info of nid # |
| `--offset` | `offset` | `STRING` | skip # bytes at the beginning of IMAGE |
| `--path` | `path` | `STRING` | show the target inode info of path X |
| `--version, -V` | `version` | `BOOLEAN` | print the version number of dump.erofs and exit |

## Commands
### `execute`
Execute the tool with its options and positional operands

- **Subcommand argv path**: `_self`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `positionalArgs` | `STRING` | `false` | Tool-specific positional operands |
