# Lpdump Tool Reference

## Metadata
- **Executable**: `lpdump`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `READ_ONLY`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `08653f76b6daec0a35f8869a03a4e6167f56d722ac5c216635def3ebe159ed0c`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--all, -a` | `all` | `BOOLEAN` | Dump all slots (not available in JSON mode). |
| `--dump-metadata-size, -d` | `dumpMetadataSize` | `BOOLEAN` | Print the space reserved for metadata to stdout |
| `--json, -j` | `json` | `BOOLEAN` | Print in JSON format. |
| `--slot, -s` | `slot` | `STRING` | Slot number or suffix. |

## Commands
### `execute`
Execute the tool with its options and positional operands

- **Subcommand argv path**: `_self`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `positionalArgs` | `STRING` | `false` | Tool-specific positional operands |
