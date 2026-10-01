# Unpack_bootimg Tool Reference

## Metadata
- **Executable**: `unpack_bootimg`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `HOST_WRITE`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `06b54dd9a07c5281778e29e234e76f6e3faee8bf0c904a5ef88fdee30eeed12e`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--boot_img` | `bootImg` | `STRING` | path to the boot, recovery or vendor_boot image |
| `--format` | `format` | `CHOICE` | {info,mkbootimg} |
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--null, -0` | `nullValue` | `BOOLEAN` | output null-terminated argument strings |
| `--out` | `out` | `STRING` | output directory of the unpacked images |

## Commands
### `execute`
Execute the tool with its options and positional operands

- **Subcommand argv path**: `_self`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `positionalArgs` | `STRING` | `false` | Tool-specific positional operands |
