# Lpadd Tool Reference

## Metadata
- **Executable**: `lpadd`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `HOST_WRITE`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `4cbed2cf021674ee3f03cd83913f1961f554e25891ea1b294cc6eb62080b7578`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--readonly` | `readonly` | `BOOLEAN` | The partition should be mapped read-only. |
| `--replace` | `replace` | `BOOLEAN` | The partition contents should be replaced with |

## Commands
### `execute`
Execute the tool with its options and positional operands

- **Subcommand argv path**: `_self`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `positionalArgs` | `STRING` | `false` | Tool-specific positional operands |
