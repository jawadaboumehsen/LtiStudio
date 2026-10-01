# Mkfs.erofs Tool Reference

## Metadata
- **Executable**: `mkfs.erofs`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `HOST_WRITE`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `e553f48607298a9ac3cf4aea62d69302a6343cd3e6e36a757bbb4dc561010147`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-b` | `B` | `STRING` | set block size to # (# = page size by default) |
| `-C` | `C` | `STRING` | specify the size of compress physical cluster in bytes |
| `-d` | `D` | `STRING` | set output verbosity; 0=quiet, 9=verbose (default=2) |
| `-E` | `E` | `STRING` | [,...]             X=extended options |
| `-L` | `L` | `STRING` | set the volume label (maximum 15 bytes) |
| `-T` | `T` | `STRING` | specify a fixed UNIX timestamp # as build time |
| `-U` | `U` | `STRING` | use a given filesystem UUID |
| `-x` | `X` | `STRING` | set xattr tolerance to # (< 0, disable xattrs; default 2) |
| `-z` | `Z` | `STRING` | [,level=Y]         X=compressor (Y=compression level, Z=dictionary size, optional) |
| `--all-root` | `allRoot` | `BOOLEAN` | make all files owned by root |
| `--all-time` | `allTime` | `BOOLEAN` | the timestamp is also applied to all files (default) |
| `--aufs` | `aufs` | `BOOLEAN` | replace aufs special files with overlayfs metadata |
| `--blobdev` | `blobdev` | `STRING` | specify an extra device X to store chunked data |
| `--block-list-file` | `blockListFile` | `STRING` | X=block_list file |
| `--chunksize` | `chunksize` | `STRING` | generate chunk-based files with #-byte chunks |
| `--clean` | `clean` | `STRING` | run full clean build (default) or: |
| `--compress-hints` | `compressHints` | `STRING` | specify a file to configure per-file compression strategy |
| `--exclude-path` | `excludePath` | `STRING` | avoid including file X (X = exact literal path) |
| `--exclude-regex` | `excludeRegex` | `STRING` | avoid including files that match X (X = regular expression) |
| `--file-contexts` | `fileContexts` | `STRING` | specify a file contexts file to setup selinux labels |
| `--force-gid` | `forceGid` | `STRING` | set all file gids to # (# = GID) |
| `--force-uid` | `forceUid` | `STRING` | set all file uids to # (# = UID) |
| `--fs-config-file` | `fsConfigFile` | `STRING` | X=fs_config file |
| `--gid-offset` | `gidOffset` | `STRING` | add offset # to all file gids (# = id offset) |
| `--hard-dereference` | `hardDereference` | `BOOLEAN` | dereference hardlinks, add links as separate inodes |
| `--help, -h` | `help` | `BOOLEAN` | display this help and exit |
| `--ignore-mtime` | `ignoreMtime` | `BOOLEAN` | use build time instead of strict per-file modification time |
| `--incremental` | `incremental` | `STRING` | run incremental build |
| `--max-extent-bytes` | `maxExtentBytes` | `STRING` | set maximum decompressed extent size # in bytes |
| `--mkfs-time` | `mkfsTime` | `BOOLEAN` | the timestamp is applied as build time only |
| `--mount-point` | `mountPoint` | `STRING` | X=prefix of target fs path (default: /) |
| `--offset` | `offset` | `STRING` | skip # bytes at the beginning of IMAGE. |
| `--ovlfs-strip` | `ovlfsStrip` | `STRING` | strip overlayfs metadata in the target image (e.g. whiteouts) |
| `--preserve-mtime` | `preserveMtime` | `BOOLEAN` | keep per-file modification time strictly |
| `--product-out` | `productOut` | `STRING` | X=product_out directory |
| `--quiet` | `quiet` | `BOOLEAN` | quiet execution (do not write anything to standard output.) |
| `--root-xattr-isize` | `rootXattrIsize` | `STRING` | ensure the inline xattr size of the root directory is # bytes at least |
| `--sort` | `sort` | `PATH` | data sorting order for tarballs as input (default: path) |
| `--tar` | `tar` | `STRING` | generate a full or index-only image from a tarball(-ish) source |
| `--uid-offset` | `uidOffset` | `STRING` | add offset # to all file uids (# = id offset) |
| `--ungzip` | `ungzip` | `STRING` | try to filter the tarball stream through gzip |
| `--unxz` | `unxz` | `STRING` | try to filter the tarball stream through xz/lzma/lzip |
| `--version, -V` | `version` | `BOOLEAN` | print the version number of mkfs.erofs and exit |
| `--workers` | `workers` | `STRING` | set the number of worker threads to # (default: 16) |
| `--xattr-prefix` | `xattrPrefix` | `STRING` | X=extra xattr name prefix |
| `--zfeature-bits` | `zfeatureBits` | `STRING` | toggle filesystem compression features according to given bits # |

## Commands
### `execute`
Execute the tool with its options and positional operands

- **Subcommand argv path**: `_self`
- **Risk Override**: `HOST_WRITE`

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `positionalArgs` | `STRING` | `false` | Tool-specific positional operands |
