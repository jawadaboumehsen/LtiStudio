# Apktool Tool Reference

## Metadata
- **Executable**: `apktool`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `HOST_WRITE`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `17de374e5e34f90d4e6ac333b970814131b74750de1a1468c23ede19b7308f07`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--aapt` | `aapt` | `PATH` | Use aapt2 binary located in <file>. |
| `--all` | `all` | `BOOLEAN` | Include all framework files regardless of tag. |
| `--all-src, -a` | `allSrc` | `BOOLEAN` | Decode all sources in the apk (includes unknown dex files). |
| `--copy-original` | `copyOriginal` | `BOOLEAN` | Copy original AndroidManifest.xml and META-INF. See project page for more info. |
| `--debuggable` | `debuggable` | `BOOLEAN` | Set android:debuggable to "true" in AndroidManifest.xml for the built apk. |
| `--force, -f` | `force` | `BOOLEAN` | Force delete destination directory. |
| `--frame-path, -p` | `framePath` | `PATH` | Use framework files located in <dir>. |
| `--frame-tag, -t` | `frameTag` | `STRING` | Use framework files tagged with <tag>. |
| `--ignore-raw-values` | `ignoreRawValues` | `BOOLEAN` | Ignore raw attribute values in XML resource files. |
| `--jobs, -j` | `jobs` | `INT` | Set the number of jobs to execute in parallel to <num>. |
| `--keep-broken-res` | `keepBrokenRes` | `BOOLEAN` | Use if there was an error and some resources were dropped, e.g. "Invalid resource config detected. Dropping resources", but you want to decode them anyway, even with errors. You will have to fix them manually before building. |
| `--lib, -l` | `lib` | `STRING` | Use shared library <package> located in <file>. Can be specified multiple times. |
| `--match-original` | `matchOriginal` | `BOOLEAN` | Keep files closest to original as possible (prevents rebuild). |
| `--net-sec-conf` | `netSecConf` | `BOOLEAN` | Add a generic network security configuration file to the built apk. |
| `--no-apk` | `noApk` | `BOOLEAN` | Disable repacking of the built files into a new apk. |
| `--no-assets` | `noAssets` | `BOOLEAN` | Do not decode assets. |
| `--no-crunch` | `noCrunch` | `BOOLEAN` | Disable crunching of resource files during the build step. |
| `--no-debug-info` | `noDebugInfo` | `BOOLEAN` | Do not include debug info in sources (.local, .param, .line, etc.) |
| `--no-res, -r` | `noRes` | `BOOLEAN` | Do not decode resources. |
| `--no-src, -s` | `noSrc` | `BOOLEAN` | Do not decode sources. |
| `--only-manifest` | `onlyManifest` | `BOOLEAN` | Only decode AndroidManifest.xml without resources. |
| `--output, -o` | `output` | `PATH` | Output decoded files to <dir>. (default: apk.out) |
| `--quiet, -q` | `quiet` | `BOOLEAN` | Suppress normal output. |
| `--res-resolve-mode` | `resResolveMode` | `STRING` | Set the resolve mode for resources to <mode>. Possible values: 'default', 'greedy' or 'lazy'. |
| `--verbose, -v` | `verbose` | `BOOLEAN` | Increase output verbosity. |

## Commands
### `b`
Unrecognized option: -h

- **Subcommand argv path**: `b`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--aapt` | `aapt` | `PATH` | Use aapt2 binary located in <file>. |
| `--copy-original` | `copyOriginal` | `BOOLEAN` | Copy original AndroidManifest.xml and META-INF. See project page for more info. |
| `--debuggable` | `debuggable` | `BOOLEAN` | Set android:debuggable to "true" in AndroidManifest.xml for the built apk. |
| `--force, -f` | `force` | `BOOLEAN` | Skip changes detection and build all files. |
| `--frame-path, -p` | `framePath` | `PATH` | Use framework files located in <dir>. |
| `--jobs, -j` | `jobs` | `INT` | Set the number of jobs to execute in parallel to <num>. |
| `--lib, -l` | `lib` | `STRING` | Use shared library <package> located in <file>. Can be specified multiple times. |
| `--net-sec-conf` | `netSecConf` | `BOOLEAN` | Add a generic network security configuration file to the built apk. |
| `--no-apk` | `noApk` | `BOOLEAN` | Disable repacking of the built files into a new apk. |
| `--no-crunch` | `noCrunch` | `BOOLEAN` | Disable crunching of resource files during the build step. |
| `--output, -o` | `output` | `PATH` | Output the built apk to <file>. (default: dist/name.apk) |
| `--quiet, -q` | `quiet` | `BOOLEAN` | Suppress normal output. |
| `--verbose, -v` | `verbose` | `BOOLEAN` | Increase output verbosity. |

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `apkDir` | `PATH` | `true` | Positional apk-dir accepted by the tool |

### `build`
Unrecognized option: -h

- **Subcommand argv path**: `build`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--aapt` | `aapt` | `PATH` | Use aapt2 binary located in <file>. |
| `--copy-original` | `copyOriginal` | `BOOLEAN` | Copy original AndroidManifest.xml and META-INF. See project page for more info. |
| `--debuggable` | `debuggable` | `BOOLEAN` | Set android:debuggable to "true" in AndroidManifest.xml for the built apk. |
| `--force, -f` | `force` | `BOOLEAN` | Skip changes detection and build all files. |
| `--frame-path, -p` | `framePath` | `PATH` | Use framework files located in <dir>. |
| `--jobs, -j` | `jobs` | `INT` | Set the number of jobs to execute in parallel to <num>. |
| `--lib, -l` | `lib` | `STRING` | Use shared library <package> located in <file>. Can be specified multiple times. |
| `--net-sec-conf` | `netSecConf` | `BOOLEAN` | Add a generic network security configuration file to the built apk. |
| `--no-apk` | `noApk` | `BOOLEAN` | Disable repacking of the built files into a new apk. |
| `--no-crunch` | `noCrunch` | `BOOLEAN` | Disable crunching of resource files during the build step. |
| `--output, -o` | `output` | `PATH` | Output the built apk to <file>. (default: dist/name.apk) |
| `--quiet, -q` | `quiet` | `BOOLEAN` | Suppress normal output. |
| `--verbose, -v` | `verbose` | `BOOLEAN` | Increase output verbosity. |

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `apkDir` | `PATH` | `true` | Positional apk-dir accepted by the tool |

### `cf`
Unrecognized option: -h

- **Subcommand argv path**: `cf`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--all, -a` | `all` | `BOOLEAN` | Include all framework files regardless of tag. |
| `--frame-path, -p` | `framePath` | `PATH` | Set the path for framework files to <dir>. |
| `--frame-tag, -t` | `frameTag` | `STRING` | Suffix framework files with <tag>. |
| `--quiet, -q` | `quiet` | `BOOLEAN` | Suppress normal output. |
| `--verbose, -v` | `verbose` | `BOOLEAN` | Increase output verbosity. |

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `brutAlllGmailCom` | `STRING` | `true` | Positional brut.alll@gmail.com accepted by the tool |

### `cleanFrameworks`
Unrecognized option: -h

- **Subcommand argv path**: `clean-frameworks`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--all, -a` | `all` | `BOOLEAN` | Include all framework files regardless of tag. |
| `--frame-path, -p` | `framePath` | `PATH` | Set the path for framework files to <dir>. |
| `--frame-tag, -t` | `frameTag` | `STRING` | Suffix framework files with <tag>. |
| `--quiet, -q` | `quiet` | `BOOLEAN` | Suppress normal output. |
| `--verbose, -v` | `verbose` | `BOOLEAN` | Increase output verbosity. |

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `brutAlllGmailCom` | `STRING` | `true` | Positional brut.alll@gmail.com accepted by the tool |

### `d`
Unrecognized option: -h

- **Subcommand argv path**: `d`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--all-src, -a` | `allSrc` | `BOOLEAN` | Decode all sources in the apk (includes unknown dex files). |
| `--force, -f` | `force` | `BOOLEAN` | Force delete destination directory. |
| `--frame-path, -p` | `framePath` | `PATH` | Use framework files located in <dir>. |
| `--frame-tag, -t` | `frameTag` | `STRING` | Use framework files tagged with <tag>. |
| `--ignore-raw-values` | `ignoreRawValues` | `BOOLEAN` | Ignore raw attribute values in XML resource files. |
| `--jobs, -j` | `jobs` | `INT` | Set the number of jobs to execute in parallel to <num>. |
| `--keep-broken-res` | `keepBrokenRes` | `BOOLEAN` | Use if there was an error and some resources were dropped, e.g. "Invalid resource config detected. Dropping resources", but you want to decode them anyway, even with errors. You will have to fix them manually before building. |
| `--lib, -l` | `lib` | `STRING` | Use shared library <package> located in <file>. Can be specified multiple times. |
| `--match-original` | `matchOriginal` | `BOOLEAN` | Keep files closest to original as possible (prevents rebuild). |
| `--no-assets` | `noAssets` | `BOOLEAN` | Do not decode assets. |
| `--no-debug-info` | `noDebugInfo` | `BOOLEAN` | Do not include debug info in sources (.local, .param, .line, etc.) |
| `--no-res, -r` | `noRes` | `BOOLEAN` | Do not decode resources. |
| `--no-src, -s` | `noSrc` | `BOOLEAN` | Do not decode sources. |
| `--only-manifest` | `onlyManifest` | `BOOLEAN` | Only decode AndroidManifest.xml without resources. |
| `--output, -o` | `output` | `PATH` | Output decoded files to <dir>. (default: apk.out) |
| `--quiet, -q` | `quiet` | `BOOLEAN` | Suppress normal output. |
| `--res-resolve-mode` | `resResolveMode` | `STRING` | Set the resolve mode for resources to <mode>. Possible values: 'default', 'greedy' or 'lazy'. |
| `--verbose, -v` | `verbose` | `BOOLEAN` | Increase output verbosity. |

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `apkFile` | `PATH` | `true` | Positional apk-file accepted by the tool |

### `decode`
Unrecognized option: -h

- **Subcommand argv path**: `decode`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--all-src, -a` | `allSrc` | `BOOLEAN` | Decode all sources in the apk (includes unknown dex files). |
| `--force, -f` | `force` | `BOOLEAN` | Force delete destination directory. |
| `--frame-path, -p` | `framePath` | `PATH` | Use framework files located in <dir>. |
| `--frame-tag, -t` | `frameTag` | `STRING` | Use framework files tagged with <tag>. |
| `--ignore-raw-values` | `ignoreRawValues` | `BOOLEAN` | Ignore raw attribute values in XML resource files. |
| `--jobs, -j` | `jobs` | `INT` | Set the number of jobs to execute in parallel to <num>. |
| `--keep-broken-res` | `keepBrokenRes` | `BOOLEAN` | Use if there was an error and some resources were dropped, e.g. "Invalid resource config detected. Dropping resources", but you want to decode them anyway, even with errors. You will have to fix them manually before building. |
| `--lib, -l` | `lib` | `STRING` | Use shared library <package> located in <file>. Can be specified multiple times. |
| `--match-original` | `matchOriginal` | `BOOLEAN` | Keep files closest to original as possible (prevents rebuild). |
| `--no-assets` | `noAssets` | `BOOLEAN` | Do not decode assets. |
| `--no-debug-info` | `noDebugInfo` | `BOOLEAN` | Do not include debug info in sources (.local, .param, .line, etc.) |
| `--no-res, -r` | `noRes` | `BOOLEAN` | Do not decode resources. |
| `--no-src, -s` | `noSrc` | `BOOLEAN` | Do not decode sources. |
| `--only-manifest` | `onlyManifest` | `BOOLEAN` | Only decode AndroidManifest.xml without resources. |
| `--output, -o` | `output` | `PATH` | Output decoded files to <dir>. (default: apk.out) |
| `--quiet, -q` | `quiet` | `BOOLEAN` | Suppress normal output. |
| `--res-resolve-mode` | `resResolveMode` | `STRING` | Set the resolve mode for resources to <mode>. Possible values: 'default', 'greedy' or 'lazy'. |
| `--verbose, -v` | `verbose` | `BOOLEAN` | Increase output verbosity. |

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `apkFile` | `PATH` | `true` | Positional apk-file accepted by the tool |

### `help`
Show help

- **Subcommand argv path**: `h`
- **Risk Override**: `READ_ONLY`

### `ifCommand`
Unrecognized option: -h

- **Subcommand argv path**: `if`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--frame-path, -p` | `framePath` | `PATH` | Set the path for framework files to <dir>. |
| `--frame-tag, -t` | `frameTag` | `STRING` | Suffix framework files with <tag>. |
| `--quiet, -q` | `quiet` | `BOOLEAN` | Suppress normal output. |
| `--verbose, -v` | `verbose` | `BOOLEAN` | Increase output verbosity. |

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `apkFile` | `PATH` | `true` | Positional apk-file accepted by the tool |

### `installFramework`
Unrecognized option: -h

- **Subcommand argv path**: `install-framework`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--frame-path, -p` | `framePath` | `PATH` | Set the path for framework files to <dir>. |
| `--frame-tag, -t` | `frameTag` | `STRING` | Suffix framework files with <tag>. |
| `--quiet, -q` | `quiet` | `BOOLEAN` | Suppress normal output. |
| `--verbose, -v` | `verbose` | `BOOLEAN` | Increase output verbosity. |

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `apkFile` | `PATH` | `true` | Positional apk-file accepted by the tool |

### `lf`
Unrecognized option: -h

- **Subcommand argv path**: `lf`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--all, -a` | `all` | `BOOLEAN` | Include all framework files regardless of tag. |
| `--frame-path, -p` | `framePath` | `PATH` | Set the path for framework files to <dir>. |
| `--frame-tag, -t` | `frameTag` | `STRING` | Suffix framework files with <tag>. |
| `--quiet, -q` | `quiet` | `BOOLEAN` | Suppress normal output. |
| `--verbose, -v` | `verbose` | `BOOLEAN` | Increase output verbosity. |

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `brutAlllGmailCom` | `STRING` | `true` | Positional brut.alll@gmail.com accepted by the tool |

### `listFrameworks`
Unrecognized option: -h

- **Subcommand argv path**: `list-frameworks`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--all, -a` | `all` | `BOOLEAN` | Include all framework files regardless of tag. |
| `--frame-path, -p` | `framePath` | `PATH` | Set the path for framework files to <dir>. |
| `--frame-tag, -t` | `frameTag` | `STRING` | Suffix framework files with <tag>. |
| `--quiet, -q` | `quiet` | `BOOLEAN` | Suppress normal output. |
| `--verbose, -v` | `verbose` | `BOOLEAN` | Increase output verbosity. |

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `brutAlllGmailCom` | `STRING` | `true` | Positional brut.alll@gmail.com accepted by the tool |

### `pr`
Unrecognized option: -h

- **Subcommand argv path**: `pr`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--quiet, -q` | `quiet` | `BOOLEAN` | Suppress normal output. |
| `--verbose, -v` | `verbose` | `BOOLEAN` | Increase output verbosity. |

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `arscFile` | `PATH` | `true` | Positional arsc-file accepted by the tool |

### `publicizeResources`
Unrecognized option: -h

- **Subcommand argv path**: `publicize-resources`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--quiet, -q` | `quiet` | `BOOLEAN` | Suppress normal output. |
| `--verbose, -v` | `verbose` | `BOOLEAN` | Increase output verbosity. |

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `arscFile` | `PATH` | `true` | Positional arsc-file accepted by the tool |

### `version`
Show version

- **Subcommand argv path**: `v`
- **Risk Override**: `READ_ONLY`
