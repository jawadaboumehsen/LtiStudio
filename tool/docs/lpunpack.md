# Lpunpack Tool Reference

## Metadata
- **Executable**: `lpunpack`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `HOST_WRITE`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `3e5f4ab16518462ca94bddc0ff58ea3909afefa0243c30a6b40a7c398f4567e2`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--image, -i` | `image` | `STRING` | Use the given file as an additional super image. |
| `--partition, -p` | `partition` | `STRING` | Extract the named partition. This can |
| `--slot, -S` | `slot` | `STRING` | Slot number (default is 0). |

## Commands
### `execute`
Execute the tool with its options and positional operands

- **Subcommand argv path**: `_self`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `positionalArgs` | `STRING` | `false` | Tool-specific positional operands |
