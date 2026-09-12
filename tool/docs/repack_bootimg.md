# Repack_bootimg Tool Reference

## Metadata
- **Executable**: `repack_bootimg`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `HOST_WRITE`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `eb02c40d1d85e8619e3b308459a3a7628ef28a7e2ceabe83c8060ad6378f1363`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--dst_bootimg` | `dstBootimg` | `STRING` | filename to destination boot image |
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--local` | `local` | `BOOLEAN` | use local files as repack source |
| `--ramdisk_add` | `ramdiskAdd` | `STRING` | a copy pair to copy into the ramdisk of --dst_bootimg (SRC_FILE:DST_FILE) |
| `--src_bootimg` | `srcBootimg` | `STRING` | filename to source boot image |

## Commands
### `execute`
Execute the tool with its options and positional operands

- **Subcommand argv path**: `_self`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `positionalArgs` | `STRING` | `false` | Tool-specific positional operands |
