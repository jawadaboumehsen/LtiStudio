# Fec Tool Reference

## Metadata
- **Executable**: `fec`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `HOST_WRITE`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `0674ba6cba142e20337b8d963d85acb5b2be2248430dd773a3bb28674d0417de`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-h` | `H` | `BOOLEAN` | show this help |
| `-S` | `S` | `BOOLEAN` | treat data as a sparse file |
| `-v` | `V` | `BOOLEAN` | enable verbose logging |
| `--inplace, -i` | `inplace` | `BOOLEAN` | correct <data> in place |
| `--padding, -p` | `padding` | `STRING` | add padding after ECC data |
| `--roots, -r` | `roots` | `STRING` | number of parity bytes |
| `--threads, -j` | `threads` | `STRING` | number of threads to use |

## Commands
### `execute`
Execute the tool with its options and positional operands

- **Subcommand argv path**: `_self`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `positionalArgs` | `STRING` | `false` | Tool-specific positional operands |
