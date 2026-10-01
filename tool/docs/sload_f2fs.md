# Sload_f2fs Tool Reference

## Metadata
- **Executable**: `sload_f2fs`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `HOST_WRITE`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `5a5e2295296454f05fc10f6567aba98ffc408a809b557dac56319617d47cb30b`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-a` | `A` | `STRING` | <algorithm> compression algorithm, default LZ4 |
| `-C` | `C` | `STRING` | fs_config |
| `-f` | `F` | `STRING` | source directory [path of the source directory] |
| `-i` | `I` | `STRING` | <ext> compress files with these extensions only. |
| `-L` | `L` | `STRING` | <log-of-blocks-per-cluster>, default 2 |
| `-m` | `M` | `STRING` | <num> min compressed blocks per cluster |
| `-p` | `P` | `STRING` | product out directory |
| `-r` | `R` | `BOOLEAN` | read only (to release unused blocks) for compressed files |
| `-s` | `S` | `STRING` | file_contexts |
| `-t` | `T` | `STRING` | mount point [prefix of target fs path, default:/] |
| `-x` | `X` | `STRING` | <ext> compress files except for these extensions. |

## Commands
### `execute`
Execute the tool with its options and positional operands

- **Subcommand argv path**: `_self`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `positionalArgs` | `STRING` | `false` | Tool-specific positional operands |
