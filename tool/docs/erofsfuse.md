# Erofsfuse Tool Reference

## Metadata
- **Executable**: `erofsfuse`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `HOST_WRITE`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `006305913987975af059fccab8229b9df67280378c6d28bc825d1a6508c688e2`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-d` | `D` | `BOOLEAN` | debug          enable debug output (implies -f) |
| `-f` | `F` | `BOOLEAN` | foreground operation |
| `-o` | `O` | `STRING` | FUSE mount option (repeatable, e.g. -o clone_fd -o max_idle_threads=N) |
| `-s` | `S` | `BOOLEAN` | disable multi-threaded operation |
| `--dbglevel` | `dbglevel` | `STRING` | set output message level to # (maximum 9) |
| `--device` | `device` | `STRING` | specify an extra device to be used together |
| `--help, -h` | `help` | `BOOLEAN` | print help |
| `--offset` | `offset` | `STRING` | skip # bytes at the beginning of IMAGE |
| `--version, -V` | `version` | `BOOLEAN` | print version |

## Commands
### `execute`
Execute the tool with its options and positional operands

- **Subcommand argv path**: `_self`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `positionalArgs` | `STRING` | `false` | Tool-specific positional operands |
