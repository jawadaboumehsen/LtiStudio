# Lpmake Tool Reference

## Metadata
- **Executable**: `lpmake`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `HOST_WRITE`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `67b64896dd0d28f955c9b59edd1383782ae68dfa2eccc84999861845cb606d1e`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--alignment, -a` | `alignment` | `STRING` | Optimal partition alignment in bytes. |
| `--alignment-offset, -O` | `alignmentOffset` | `STRING` | Alignment offset in bytes to device parent. |
| `--auto-slot-suffixing, -x` | `autoSlotSuffixing` | `BOOLEAN` | Mark the block device and partition names needing |
| `--block-size, -b` | `blockSize` | `STRING` | Physical block size, defaults to 4096. |
| `--device, -D` | `device` | `STRING` | Add a block device that the super partition |
| `--device-size, -d` | `deviceSize` | `STRING` | Size of the block device for logical partitions. |
| `--force-full-image, -F` | `forceFullImage` | `BOOLEAN` | Force a full image to be written even if no |
| `--group, -g` | `group` | `STRING` | :SIZE         Define a named partition group with the given |
| `--image, -i` | `image` | `PATH` | If building a sparse image for fastboot, include |
| `--metadata-size, -m` | `metadataSize` | `STRING` | Maximum size to reserve for partition metadata. |
| `--metadata-slots, -s` | `metadataSlots` | `STRING` | Number of slots to store metadata copies. |
| `--output, -o` | `output` | `PATH` | Output file. |
| `--partition, -p` | `partition` | `STRING` | Add a partition given the data, see below. |
| `--sparse, -S` | `sparse` | `BOOLEAN` | Output a sparse image for fastboot. |
| `--super-name, -n` | `superName` | `STRING` | Specify the name of the block device that will |
| `--virtual-ab` | `virtualAb` | `BOOLEAN` | Add the VIRTUAL_AB_DEVICE flag to the metadata |

## Commands
### `execute`
Execute the tool with its options and positional operands

- **Subcommand argv path**: `_self`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `positionalArgs` | `STRING` | `false` | Tool-specific positional operands |
