# Ext2simg Tool Reference

## Metadata
- **Executable**: `ext2simg`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `HOST_WRITE`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `c297420cee3474f5bddfb311818982a6b0435016d192f836e7c851684026db94`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-c` | `C` | `BOOLEAN` | include CRC block |
| `-S` | `S` | `BOOLEAN` | don't use sparse output format |
| `-z` | `Z` | `BOOLEAN` | gzip output |

## Commands
### `execute`
Execute the tool with its options and positional operands

- **Subcommand argv path**: `_self`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `positionalArgs` | `STRING` | `false` | Tool-specific positional operands |
