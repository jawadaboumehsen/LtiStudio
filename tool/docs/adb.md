# Adb Tool Reference

## Metadata
- **Executable**: `adb`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `READ_ONLY`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `e853bd0ffb1a50df2772d9bc135fa30f567b88c093cf4c040f2e2b87ee5583cb`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--exit-on-write-error` | `exitOnWriteError` | `BOOLEAN` | exit if stdout is closed |
| `-a` | `listenAll` | `BOOLEAN` | listen on all network interfaces, not just localhost |
| `--one-device` | `oneDevice` | `STRING` | only allowed with 'start-server' or 'server nodaemon', server will only connect to one USB device, specified by a serial number or USB device address. |
| `-s` | `serial` | `STRING` | use device with given serial (overrides $ANDROID_SERIAL) |
| `-H` | `serverHost` | `STRING` | name of adb server host [default=localhost] |
| `-P` | `serverPort` | `INT` | port of adb server [default=5037] |
| `-L` | `serverSocket` | `STRING` | listen on given socket for adb server [default=tcp:localhost:5037] |
| `-t` | `transportId` | `STRING` | use device with given transport id |
| `-e` | `useTcp` | `BOOLEAN` | use TCP/IP device (error if multiple TCP/IP devices available) |
| `-d` | `useUsb` | `BOOLEAN` | use USB device (error if multiple devices connected) |

## Commands
### `attach`
attach a detached device

- **Subcommand argv path**: `attach`
- **Risk Override**: `HOST_WRITE`

### `bugreport`
write a bug report

- **Subcommand argv path**: `bugreport`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `path` | `PATH` | `false` | bug report path |

### `connect`
connect to device via TCP/IP

- **Subcommand argv path**: `connect`
- **Risk Override**: `READ_ONLY`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `host` | `STRING` | `true` | device hostname or host:port |

### `detach`
detach a device

- **Subcommand argv path**: `detach`
- **Risk Override**: `HOST_WRITE`

### `devices`
list connected devices

- **Subcommand argv path**: `devices`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-l` | `longListing` | `BOOLEAN` | long output listing |

### `disableVerity`
disable dm-verity

- **Subcommand argv path**: `disable-verity`
- **Risk Override**: `DEVICE_WRITE`

### `disconnect`
disconnect from TCP/IP device

- **Subcommand argv path**: `disconnect`
- **Risk Override**: `READ_ONLY`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `host` | `STRING` | `false` | device hostname or host:port (optional) |

### `emu`
run emulator console command

- **Subcommand argv path**: `emu`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `args` | `STRING` | `false` | emulator arguments |

### `enableVerity`
enable dm-verity

- **Subcommand argv path**: `enable-verity`
- **Risk Override**: `DEVICE_WRITE`

### `execOut`
run remote command and output binary stream

- **Subcommand argv path**: `exec-out`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `command` | `STRING` | `false` | command to run |

### `forward`
forward sockets

- **Subcommand argv path**: `forward`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `args` | `STRING` | `false` | forward arguments |

### `getDevpath`
get device path

- **Subcommand argv path**: `get-devpath`
- **Risk Override**: `READ_ONLY`

### `getSerialno`
get device serial

- **Subcommand argv path**: `get-serialno`
- **Risk Override**: `READ_ONLY`

### `getState`
get device state

- **Subcommand argv path**: `get-state`
- **Risk Override**: `READ_ONLY`

### `help`
show help

- **Subcommand argv path**: `help`
- **Risk Override**: `READ_ONLY`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `args` | `STRING` | `false` | optional help arguments |

### `install`
push package to device and install it

- **Subcommand argv path**: `install`
- **Risk Override**: `DEVICE_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-d` | `downgrade` | `BOOLEAN` | allow version code downgrade |
| `-g` | `grantPermissions` | `BOOLEAN` | grant all runtime permissions |
| `-r` | `replace` | `BOOLEAN` | replace existing application |
| `-t` | `testPackages` | `BOOLEAN` | allow test packages |

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `packagePath` | `PATH` | `true` | path to APK package |

### `installMultiPackage`
install packages atomically

- **Subcommand argv path**: `install-multi-package`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `packages` | `PATH` | `false` | APK packages |

### `installMultiple`
install multiple APKs

- **Subcommand argv path**: `install-multiple`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `packages` | `PATH` | `false` | APK packages |

### `jdwp`
list JDWP processes

- **Subcommand argv path**: `jdwp`
- **Risk Override**: `READ_ONLY`

### `keygen`
generate adb keys

- **Subcommand argv path**: `keygen`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `file` | `PATH` | `true` | private key output |

### `killServer`
stop adb server

- **Subcommand argv path**: `kill-server`
- **Risk Override**: `HOST_WRITE`

### `logcat`
view or clear device logs

- **Subcommand argv path**: `logcat`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-c` | `clearLogs` | `BOOLEAN` | clear logs and exit |
| `-d` | `dumpLogs` | `BOOLEAN` | dump logs to stdout and exit |
| `-v` | `logFormat` | `STRING` | format style of logs |
| `-f` | `outputFile` | `PATH` | write logs to specified file |

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `filters` | `STRING` | `false` | log filter specifications |

### `mdns`
mdns discovery

- **Subcommand argv path**: `mdns`
- **Risk Override**: `READ_ONLY`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `args` | `STRING` | `false` | mdns arguments |

### `pair`
pair with a device

- **Subcommand argv path**: `pair`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `args` | `STRING` | `false` | host and pairing arguments |

### `pull`
copy files/dirs from device

- **Subcommand argv path**: `pull`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-z` | `compression` | `STRING` | enable compression with specified algorithm |
| `-Z` | `disableCompression` | `BOOLEAN` | disable compression |
| `-a` | `preserveMetadata` | `BOOLEAN` | preserve file timestamp and mode |

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `remote` | `STRING` | `true` | remote path on device |
| `local` | `PATH` | `true` | local path to pull to |

### `push`
copy local files/directories to device

- **Subcommand argv path**: `push`
- **Risk Override**: `DEVICE_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-z` | `compression` | `STRING` | enable compression with specified algorithm |
| `-Z` | `disableCompression` | `BOOLEAN` | disable compression |
| `--sync` | `sync` | `BOOLEAN` | only push files that have different timestamps on the host than the device |

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `local` | `PATH` | `true` | local path to push |
| `remote` | `STRING` | `true` | remote path on device |

### `reboot`
reboot the device

- **Subcommand argv path**: `reboot`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `target` | `STRING` | `false` | reboot target: bootloader, recovery, fastboot |

### `reconnect`
reconnect transport

- **Subcommand argv path**: `reconnect`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `args` | `STRING` | `false` | reconnect arguments |

### `remount`
remount partitions

- **Subcommand argv path**: `remount`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `args` | `STRING` | `false` | remount arguments |

### `reverse`
reverse sockets

- **Subcommand argv path**: `reverse`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `args` | `STRING` | `false` | reverse arguments |

### `root`
restart adbd with root

- **Subcommand argv path**: `root`
- **Risk Override**: `DEVICE_WRITE`

### `shell`
run remote shell command

- **Subcommand argv path**: `shell`
- **Risk Override**: `DEVICE_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-x` | `disableExitSeparation` | `BOOLEAN` | disable remote exit codes and stdout/stderr separation |
| `-T` | `disablePty` | `BOOLEAN` | disable pty allocation |
| `-e` | `escapeChar` | `STRING` | choose escape character |
| `-t` | `forcePty` | `BOOLEAN` | allocate a pty if on a tty |
| `-n` | `noStdin` | `BOOLEAN` | do not read from stdin |

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `command` | `STRING` | `false` | shell command to run |

### `sideload`
sideload package

- **Subcommand argv path**: `sideload`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `packagePath` | `PATH` | `true` | path to zip/ota package |

### `startServer`
start adb server

- **Subcommand argv path**: `start-server`
- **Risk Override**: `HOST_WRITE`

### `sync`
sync a local build

- **Subcommand argv path**: `sync`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `args` | `STRING` | `false` | sync arguments |

### `tcpip`
restart adb over TCP

- **Subcommand argv path**: `tcpip`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `port` | `STRING` | `true` | TCP port |

### `uninstall`
remove an app

- **Subcommand argv path**: `uninstall`
- **Risk Override**: `DEVICE_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `args` | `STRING` | `false` | package arguments |

### `unroot`
restart adbd without root

- **Subcommand argv path**: `unroot`
- **Risk Override**: `DEVICE_WRITE`

### `usb`
restart adb over USB

- **Subcommand argv path**: `usb`
- **Risk Override**: `DEVICE_WRITE`

### `version`
show version

- **Subcommand argv path**: `version`
- **Risk Override**: `READ_ONLY`

### `waitFor`
wait for a transport state

- **Subcommand argv path**: `wait-for`
- **Risk Override**: `READ_ONLY`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `state` | `STRING` | `false` | transport state |
