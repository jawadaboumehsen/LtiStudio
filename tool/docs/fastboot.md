# Fastboot Tool Reference

## Metadata
- **Executable**: `fastboot`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `READ_ONLY`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `03fa73febcbd80d7aff8831228262a2030d2d5cb93720de353c86be2f87d2a39`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--disable-fastboot-info` | `disableFastbootInfo` | `BOOLEAN` | Will collects tasks from image list rather than fastboot-info. |
| `--disable-super-optimization` | `disableSuperOptimization` | `BOOLEAN` | Disables optimizations on flashing super partition. |
| `--disable-verification` | `disableVerification` | `BOOLEAN` | Sets disable-verification when flashing vbmeta. |
| `--disable-verity` | `disableVerity` | `BOOLEAN` | Sets disable-verity when flashing vbmeta. |
| `--exclude-dynamic-partitions` | `excludeDynamicPartitions` | `BOOLEAN` | Excludes flashing of dynamic partitions. |
| `--force` | `forceOperation` | `BOOLEAN` | Force a flash operation that may be unsafe. |
| `--fs-options` | `fsOptions` | `STRING` | Enable filesystem features. OPTION supports casefold, projid, compress |
| `-s` | `selectDevice` | `STRING` | Specify a USB device. |
| `--set-active` | `setActiveSlot` | `STRING` | Sets the active slot before rebooting. |
| `--skip-reboot` | `skipReboot` | `BOOLEAN` | Don't reboot device after flashing. |
| `--skip-secondary` | `skipSecondarySlots` | `BOOLEAN` | Don't flash secondary slots in flashall/update. |
| `--slot` | `slotTarget` | `STRING` | Use SLOT; 'all' for both slots, 'other' for the other slot. |
| `-S` | `sparseSize` | `STRING` | break into sparse files no larger than SIZE |
| `--unbuffered` | `unbufferedOutput` | `BOOLEAN` | Don't buffer input or output. |
| `--verbose, -v` | `verboseMode` | `BOOLEAN` | Verbose output. |
| `-w` | `wipeUserdata` | `BOOLEAN` | Wipe userdata. |

## Commands
### `boot`
download and boot kernel

- **Subcommand argv path**: `boot`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `imagePath` | `PATH` | `true` | path to kernel image |

### `createLogicalPartition`
create logical partition

- **Subcommand argv path**: `create-logical-partition`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `name` | `STRING` | `true` | partition name |
| `size` | `STRING` | `true` | partition size |

### `deleteLogicalPartition`
delete logical partition

- **Subcommand argv path**: `delete-logical-partition`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `name` | `STRING` | `true` | partition name |

### `devices`
list bootloader devices

- **Subcommand argv path**: `devices`
- **Risk Override**: `READ_ONLY`

### `erase`
erase a partition

- **Subcommand argv path**: `erase`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `partition` | `STRING` | `true` | partition name |

### `fetch`
fetch a partition to file

- **Subcommand argv path**: `fetch`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `partition` | `STRING` | `true` | partition name |
| `outputPath` | `PATH` | `true` | output file path |

### `flash`
flash partition with image

- **Subcommand argv path**: `flash`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `partition` | `STRING` | `true` | partition name |
| `imagePath` | `PATH` | `false` | path to image file (optional) |

### `flashRaw`
create and flash a boot image

- **Subcommand argv path**: `flash:raw`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `partition` | `STRING` | `true` | partition name |
| `kernel` | `PATH` | `true` | kernel image |
| `ramdisk` | `PATH` | `false` | optional ramdisk |
| `second` | `PATH` | `false` | optional second image |

### `flashall`
flash all partitions

- **Subcommand argv path**: `flashall`
- **Risk Override**: `DEVICE_WRITE`

### `flashing`
lock/unlock partitions

- **Subcommand argv path**: `flashing`
- **Risk Override**: `IRREVERSIBLE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `action` | `STRING` | `true` | lock, unlock, lock_critical, unlock_critical, get_unlock_ability |

### `format`
format a partition

- **Subcommand argv path**: `format`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `partition` | `STRING` | `true` | partition name |

### `getStaged`
retrieve staged file

- **Subcommand argv path**: `get_staged`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `output` | `PATH` | `true` | output file |

### `getvar`
display a bootloader variable

- **Subcommand argv path**: `getvar`
- **Risk Override**: `READ_ONLY`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `variable` | `STRING` | `true` | variable name |

### `gsi`
manage GSI installation

- **Subcommand argv path**: `gsi`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `args` | `STRING` | `false` | GSI arguments |

### `oem`
execute OEM command

- **Subcommand argv path**: `oem`
- **Risk Override**: `IRREVERSIBLE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `oemCmd` | `STRING` | `true` | OEM command and arguments |

### `reboot`
reboot device

- **Subcommand argv path**: `reboot`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `target` | `STRING` | `false` | reboot target: bootloader, recovery, fastboot |

### `resizeLogicalPartition`
resize logical partition

- **Subcommand argv path**: `resize-logical-partition`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `name` | `STRING` | `true` | partition name |
| `size` | `STRING` | `true` | partition size |

### `setActive`
set active slot

- **Subcommand argv path**: `set_active`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `slot` | `STRING` | `true` | slot name |

### `snapshotUpdate`
manage snapshot update

- **Subcommand argv path**: `snapshot-update`
- **Risk Override**: `IRREVERSIBLE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `action` | `STRING` | `true` | cancel or merge |

### `stage`
stage a file

- **Subcommand argv path**: `stage`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `input` | `PATH` | `true` | input file |

### `update`
flash device from update.zip

- **Subcommand argv path**: `update`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `zipPath` | `PATH` | `true` | path to update zip file |

### `wipeSuper`
wipe super partition

- **Subcommand argv path**: `wipe-super`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `image` | `PATH` | `false` | optional empty super image |
