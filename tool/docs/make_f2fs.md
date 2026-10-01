# Make_f2fs Tool Reference

## Metadata
- **Executable**: `make_f2fs`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `HOST_WRITE`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `566a674c7488850198035447f94ec1de348a4407a5196624ad748660f8214126`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-b` | `B` | `STRING` | filesystem block size [default:4096] |
| `-c` | `C` | `STRING` | [device_name[@alias_filename]] up to 7 additional devices, except meta device |
| `-d` | `D` | `STRING` | debug level [default:0] |
| `-e` | `E` | `STRING` | [cold file ext list] e.g. "mp3,gif,mov" |
| `-f` | `F` | `BOOLEAN` | force overwrite of the existing filesystem |
| `-g` | `G` | `STRING` | add default options |
| `-H` | `H` | `BOOLEAN` | support write hint |
| `-i` | `I` | `BOOLEAN` | extended node bitmap, node ratio is 20% by default |
| `-l` | `L` | `STRING` | label |
| `-m` | `M` | `BOOLEAN` | support zoned block device [default:0] |
| `-o` | `O` | `STRING` | overprovision percentage [default:auto] |
| `-q` | `Q` | `BOOLEAN` | quiet mode |
| `-r` | `R` | `BOOLEAN` | set checkpointing seed (srand()) to 0 |
| `-s` | `S` | `STRING` | # of segments per section [default:1] |
| `-t` | `T` | `STRING` | 0: nodiscard, 1: discard [default:1] |
| `-U` | `U` | `STRING` | uuid |
| `-V` | `V` | `BOOLEAN` | print the version number and exit |
| `-w` | `W` | `STRING` | wanted sector size |
| `-z` | `Z` | `STRING` | # of sections per zone [default:1] |

## Commands
### `execute`
Execute the tool with its options and positional operands

- **Subcommand argv path**: `_self`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `positionalArgs` | `STRING` | `false` | Tool-specific positional operands |
