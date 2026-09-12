# Mkdtboimg Tool Reference

## Metadata
- **Executable**: `mkdtboimg`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `HOST_WRITE`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `ea9507427f4f691e4de27ba6918db7880511448e9dd63a80874a79c1fcaffd82`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |

## Commands
### `cfgCreate`
usage: cfg_create [-h] [--dtb-dir [DTBDIR]] [conf_file]

- **Subcommand argv path**: `cfg_create`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--dtb-dir` | `dtbDir` | `BOOLEAN` | [DTBDIR], -d [DTBDIR] |
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |

### `create`
usage: mkdtboimg [--id ID] [--rev REV] [--flags FLAGS] [--custom0 CUSTOM0]

- **Subcommand argv path**: `create`
- **Risk Override**: `HOST_WRITE`

### `dump`
Traceback (most recent call last):

- **Subcommand argv path**: `dump`
- **Risk Override**: `READ_ONLY`

### `help`
mkdtboimg help all

- **Subcommand argv path**: `help`
- **Risk Override**: `READ_ONLY`
