# Mkbootfs Tool Reference

## Metadata
- **Executable**: `mkbootfs`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `HOST_WRITE`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `66cd1d8a87d2cb81b3152211efa965bfcfc90ac9f984d8535eb6e5f5c9f1dec4`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--dirname, -d` | `dirname` | `PATH` | : fs-config directory |
| `--file, -f` | `file` | `PATH` | : Canned configuration file |
| `--help, -h` | `help` | `BOOLEAN` | : Print this help |
| `--nodes, -n` | `nodes` | `PATH` | : Dev nodes description file |

## Commands
### `execute`
Execute the tool with its options and positional operands

- **Subcommand argv path**: `_self`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `positionalArgs` | `STRING` | `false` | Tool-specific positional operands |
