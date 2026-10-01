# Avbtool Tool Reference

## Metadata
- **Executable**: `avbtool`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `HOST_WRITE`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `14dc8d6ec533f551ec05ffd9a986ced0fd1290a201f62c91dad329dd51dfe3ed`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |

## Commands
### `addHashFooter`
usage: avbtool add_hash_footer [-h] [--image IMAGE]

- **Subcommand argv path**: `add_hash_footer`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--algorithm` | `algorithm` | `STRING` | Algorithm to use (default: NONE) |
| `--append_to_release_string` | `appendToReleaseString` | `STRING` | Text to append to release string |
| `--calc_max_image_size` | `calcMaxImageSize` | `BOOLEAN` | Don't store the footer - instead calculate the maximum |
| `--chain_partition` | `chainPartition` | `BOOLEAN` | PART_NAME:ROLLBACK_SLOT:KEY_PATH |
| `--chain_partition_do_not_use_ab` | `chainPartitionDoNotUseAb` | `BOOLEAN` | PART_NAME:ROLLBACK_SLOT:KEY_PATH |
| `--do_not_append_vbmeta_image` | `doNotAppendVbmetaImage` | `BOOLEAN` | Do not append vbmeta struct or footer to the image |
| `--do_not_use_ab` | `doNotUseAb` | `BOOLEAN` | The partition does not use A/B even when an A/B suffix |
| `--dynamic_partition_size` | `dynamicPartitionSize` | `BOOLEAN` | Calculate partition size based on image size |
| `--flags` | `flags` | `STRING` | VBMeta flags |
| `--hash_algorithm` | `hashAlgorithm` | `STRING` | Hash algorithm to use (default: sha256) |
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--image` | `image` | `STRING` | Image to add hashes to |
| `--include_descriptors_from_image` | `includeDescriptorsFromImage` | `STRING` | Include descriptors from image |
| `--kernel_cmdline` | `kernelCmdline` | `STRING` | Add kernel cmdline |
| `--key` | `key` | `STRING` | Path to RSA private key file |
| `--output_vbmeta_image` | `outputVbmetaImage` | `STRING` | Also write vbmeta struct to file |
| `--partition_name` | `partitionName` | `STRING` | Partition name |
| `--partition_size` | `partitionSize` | `STRING` | Partition size |
| `--print_required_libavb_version` | `printRequiredLibavbVersion` | `BOOLEAN` | Don't store the footer - instead calculate the |
| `--prop` | `prop` | `BOOLEAN` | KEY:VALUE      Add property |
| `--prop_from_file` | `propFromFile` | `BOOLEAN` | KEY:PATH |
| `--public_key_metadata` | `publicKeyMetadata` | `STRING` | Path to public key metadata file |
| `--rollback_index` | `rollbackIndex` | `STRING` | Rollback Index |
| `--rollback_index_location` | `rollbackIndexLocation` | `STRING` | Location of main vbmeta Rollback Index |
| `--salt` | `salt` | `STRING` | Salt in hex (default: /dev/urandom) |
| `--set_hashtree_disabled_flag` | `setHashtreeDisabledFlag` | `BOOLEAN` | Set the HASHTREE_DISABLED flag |
| `--set_verification_disabled_flag` | `setVerificationDisabledFlag` | `BOOLEAN` | Set the VERIFICATION_DISABLED flag |
| `--setup_rootfs_from_kernel` | `setupRootfsFromKernel` | `BOOLEAN` | IMAGE, --generate_dm_verity_cmdline_from_hashtree IMAGE |
| `--signing_helper` | `signingHelper` | `STRING` | Path to helper used for signing |
| `--signing_helper_with_files` | `signingHelperWithFiles` | `STRING` | Path to helper used for signing using files |
| `--use_persistent_digest` | `usePersistentDigest` | `BOOLEAN` | Use a persistent digest on device instead of storing |

### `addHashtreeFooter`
usage: avbtool add_hashtree_footer [-h] [--image IMAGE]

- **Subcommand argv path**: `add_hashtree_footer`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--algorithm` | `algorithm` | `STRING` | Algorithm to use (default: NONE) |
| `--append_to_release_string` | `appendToReleaseString` | `STRING` | Text to append to release string |
| `--block_size` | `blockSize` | `STRING` | Block size (default: 4096) |
| `--calc_max_image_size` | `calcMaxImageSize` | `BOOLEAN` | Don't store the hashtree or footer - instead calculate |
| `--chain_partition` | `chainPartition` | `BOOLEAN` | PART_NAME:ROLLBACK_SLOT:KEY_PATH |
| `--chain_partition_do_not_use_ab` | `chainPartitionDoNotUseAb` | `BOOLEAN` | PART_NAME:ROLLBACK_SLOT:KEY_PATH |
| `--check_at_most_once` | `checkAtMostOnce` | `BOOLEAN` | Set to verify data block only once |
| `--do_not_append_vbmeta_image` | `doNotAppendVbmetaImage` | `BOOLEAN` | Do not append vbmeta struct or footer to the image |
| `--do_not_generate_fec` | `doNotGenerateFec` | `BOOLEAN` | Do not generate forward-error-correction codes |
| `--do_not_use_ab` | `doNotUseAb` | `BOOLEAN` | The partition does not use A/B even when an A/B suffix |
| `--fec_num_roots` | `fecNumRoots` | `STRING` | Number of roots for FEC (default: 2) |
| `--flags` | `flags` | `STRING` | VBMeta flags |
| `--hash_algorithm` | `hashAlgorithm` | `STRING` | Hash algorithm to use (default: sha1) |
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--image` | `image` | `STRING` | Image to add hashtree to |
| `--include_descriptors_from_image` | `includeDescriptorsFromImage` | `STRING` | Include descriptors from image |
| `--kernel_cmdline` | `kernelCmdline` | `STRING` | Add kernel cmdline |
| `--key` | `key` | `STRING` | Path to RSA private key file |
| `--no_hashtree` | `noHashtree` | `BOOLEAN` | Do not append hashtree |
| `--output_vbmeta_image` | `outputVbmetaImage` | `STRING` | Also write vbmeta struct to file |
| `--partition_name` | `partitionName` | `STRING` | Partition name |
| `--partition_size` | `partitionSize` | `STRING` | Partition size |
| `--print_required_libavb_version` | `printRequiredLibavbVersion` | `BOOLEAN` | Don't store the footer - instead calculate the |
| `--prop` | `prop` | `BOOLEAN` | KEY:VALUE      Add property |
| `--prop_from_file` | `propFromFile` | `BOOLEAN` | KEY:PATH |
| `--public_key_metadata` | `publicKeyMetadata` | `STRING` | Path to public key metadata file |
| `--rollback_index` | `rollbackIndex` | `STRING` | Rollback Index |
| `--rollback_index_location` | `rollbackIndexLocation` | `STRING` | Location of main vbmeta Rollback Index |
| `--salt` | `salt` | `STRING` | Salt in hex (default: /dev/urandom) |
| `--set_hashtree_disabled_flag` | `setHashtreeDisabledFlag` | `BOOLEAN` | Set the HASHTREE_DISABLED flag |
| `--set_verification_disabled_flag` | `setVerificationDisabledFlag` | `BOOLEAN` | Set the VERIFICATION_DISABLED flag |
| `--setup_as_rootfs_from_kernel` | `setupAsRootfsFromKernel` | `BOOLEAN` | Adds kernel cmdline for setting up rootfs |
| `--setup_rootfs_from_kernel` | `setupRootfsFromKernel` | `BOOLEAN` | IMAGE, --generate_dm_verity_cmdline_from_hashtree IMAGE |
| `--signing_helper` | `signingHelper` | `STRING` | Path to helper used for signing |
| `--signing_helper_with_files` | `signingHelperWithFiles` | `STRING` | Path to helper used for signing using files |
| `--use_persistent_digest` | `usePersistentDigest` | `BOOLEAN` | Use a persistent digest on device instead of storing |

### `appendVbmetaImage`
usage: avbtool append_vbmeta_image [-h] [--image IMAGE] --partition_size

- **Subcommand argv path**: `append_vbmeta_image`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--image` | `image` | `STRING` | Image to append vbmeta blob to |
| `--partition_size` | `partitionSize` | `STRING` | Partition size |
| `--vbmeta_image` | `vbmetaImage` | `STRING` | Image with vbmeta blob to append |

### `calculateKernelCmdline`
usage: avbtool calculate_kernel_cmdline [-h] --image IMAGE

- **Subcommand argv path**: `calculate_kernel_cmdline`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--hashtree_disabled` | `hashtreeDisabled` | `BOOLEAN` | Return the cmdline for hashtree disabled |
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--image` | `image` | `STRING` | Image to calculate kernel cmdline for |
| `--output` | `output` | `STRING` | Write cmdline to file (default: stdout) |

### `calculateVbmetaDigest`
usage: avbtool calculate_vbmeta_digest [-h] --image IMAGE

- **Subcommand argv path**: `calculate_vbmeta_digest`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--format` | `format` | `BOOLEAN` | {hex,raw}    Output format (default: hex) |
| `--hash_algorithm` | `hashAlgorithm` | `STRING` | Hash algorithm to use (default: sha256) |
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--image` | `image` | `STRING` | Image to calculate digest for |
| `--output` | `output` | `STRING` | Write digest to file (default: stdout) |

### `eraseFooter`
usage: avbtool erase_footer [-h] --image IMAGE [--keep_hashtree]

- **Subcommand argv path**: `erase_footer`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--image` | `image` | `STRING` | Image with a footer |
| `--keep_hashtree` | `keepHashtree` | `BOOLEAN` | Keep the hashtree and FEC in the image |

### `extractPublicKey`
usage: avbtool extract_public_key [-h] --key KEY --output OUTPUT

- **Subcommand argv path**: `extract_public_key`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--key` | `key` | `STRING` | Path to RSA private key file |
| `--output` | `output` | `STRING` | Output file name |

### `extractPublicKeyDigest`
usage: avbtool extract_public_key_digest [-h] --key KEY --output OUTPUT

- **Subcommand argv path**: `extract_public_key_digest`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--key` | `key` | `STRING` | Path to RSA private key file |
| `--output` | `output` | `STRING` | Output file name |

### `extractVbmetaImage`
usage: avbtool extract_vbmeta_image [-h] --image IMAGE [--output OUTPUT]

- **Subcommand argv path**: `extract_vbmeta_image`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--image` | `image` | `STRING` | Image with footer |
| `--output` | `output` | `STRING` | Output file name |
| `--padding_size` | `paddingSize` | `STRING` | If non-zero, pads output with NUL bytes so its size is |

### `generateTestImage`
usage: avbtool generate_test_image [-h] --image_size IMAGE_SIZE

- **Subcommand argv path**: `generate_test_image`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--image_size` | `imageSize` | `STRING` | Size of image to generate. |
| `--output` | `output` | `STRING` | Output file name. |
| `--start_byte` | `startByte` | `STRING` | Integer for the start byte of the pattern. |

### `infoImage`
usage: avbtool info_image [-h] --image IMAGE [--output OUTPUT] [--cert]

- **Subcommand argv path**: `info_image`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--cert` | `cert` | `BOOLEAN` | Show information about the avb_cert extension |
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--image` | `image` | `STRING` | Image to show information about |
| `--output` | `output` | `STRING` | Write info to file |
| `--output_pubkey` | `outputPubkey` | `STRING` | Write public key to file |

### `makeAtxCertificate`
usage: avbtool make_certificate [-h] [--output OUTPUT] --subject SUBJECT

- **Subcommand argv path**: `make_atx_certificate`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--authority_key` | `authorityKey` | `STRING` | Path to authority RSA private key file |
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--output` | `output` | `STRING` | Write certificate to file |
| `--signing_helper` | `signingHelper` | `STRING` | Path to helper used for signing |
| `--signing_helper_with_files` | `signingHelperWithFiles` | `STRING` | Path to helper used for signing using files |
| `--subject` | `subject` | `STRING` | Path to subject file |
| `--subject_is_intermediate_authority` | `subjectIsIntermediateAuthority` | `BOOLEAN` | Override usage with the value used for an intermediate |
| `--subject_key` | `subjectKey` | `STRING` | [--subject_key_version SUBJECT_KEY_VERSION] |
| `--subject_key_version` | `subjectKeyVersion` | `STRING` | Version of the subject key |
| `--usage` | `usage` | `STRING` | Override usage with a hash of the provided string |
| `--usage_for_unlock` | `usageForUnlock` | `BOOLEAN` | Override usage with the value used for authenticated |

### `makeAtxMetadata`
usage: avbtool make_cert_metadata [-h] [--output OUTPUT]

- **Subcommand argv path**: `make_atx_metadata`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--intermediate_key_certificate` | `intermediateKeyCertificate` | `BOOLEAN` | INTERMEDIATE_KEY_CERTIFICATE |
| `--output` | `output` | `STRING` | Write metadata to file |
| `--product_key_certificate` | `productKeyCertificate` | `BOOLEAN` | PRODUCT_KEY_CERTIFICATE |

### `makeAtxPermanentAttributes`
usage: avbtool make_cert_permanent_attributes [-h] [--output OUTPUT]

- **Subcommand argv path**: `make_atx_permanent_attributes`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--output` | `output` | `STRING` | Write attributes to file |
| `--product_id` | `productId` | `STRING` | Path to Product ID file |
| `--root_authority_key` | `rootAuthorityKey` | `BOOLEAN` | ROOT_AUTHORITY_KEY --product_id |

### `makeAtxUnlockCredential`
usage: avbtool make_cert_unlock_credential [-h] [--output OUTPUT]

- **Subcommand argv path**: `make_atx_unlock_credential`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--challenge` | `challenge` | `STRING` | Path to the challenge to sign (optional). If this is |
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--intermediate_key_certificate` | `intermediateKeyCertificate` | `BOOLEAN` | INTERMEDIATE_KEY_CERTIFICATE |
| `--output` | `output` | `STRING` | Write credential to file |
| `--signing_helper` | `signingHelper` | `STRING` | Path to helper used for signing |
| `--signing_helper_with_files` | `signingHelperWithFiles` | `STRING` | Path to helper used for signing using files |
| `--unlock_key` | `unlockKey` | `STRING` | Path to unlock key (optional). Must be provided if |
| `--unlock_key_certificate` | `unlockKeyCertificate` | `BOOLEAN` | UNLOCK_KEY_CERTIFICATE |

### `makeCertMetadata`
usage: avbtool make_cert_metadata [-h] [--output OUTPUT]

- **Subcommand argv path**: `make_cert_metadata`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--intermediate_key_certificate` | `intermediateKeyCertificate` | `BOOLEAN` | INTERMEDIATE_KEY_CERTIFICATE |
| `--output` | `output` | `STRING` | Write metadata to file |
| `--product_key_certificate` | `productKeyCertificate` | `BOOLEAN` | PRODUCT_KEY_CERTIFICATE |

### `makeCertPermanentAttributes`
usage: avbtool make_cert_permanent_attributes [-h] [--output OUTPUT]

- **Subcommand argv path**: `make_cert_permanent_attributes`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--output` | `output` | `STRING` | Write attributes to file |
| `--product_id` | `productId` | `STRING` | Path to Product ID file |
| `--root_authority_key` | `rootAuthorityKey` | `BOOLEAN` | ROOT_AUTHORITY_KEY --product_id |

### `makeCertUnlockCredential`
usage: avbtool make_cert_unlock_credential [-h] [--output OUTPUT]

- **Subcommand argv path**: `make_cert_unlock_credential`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--challenge` | `challenge` | `STRING` | Path to the challenge to sign (optional). If this is |
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--intermediate_key_certificate` | `intermediateKeyCertificate` | `BOOLEAN` | INTERMEDIATE_KEY_CERTIFICATE |
| `--output` | `output` | `STRING` | Write credential to file |
| `--signing_helper` | `signingHelper` | `STRING` | Path to helper used for signing |
| `--signing_helper_with_files` | `signingHelperWithFiles` | `STRING` | Path to helper used for signing using files |
| `--unlock_key` | `unlockKey` | `STRING` | Path to unlock key (optional). Must be provided if |
| `--unlock_key_certificate` | `unlockKeyCertificate` | `BOOLEAN` | UNLOCK_KEY_CERTIFICATE |

### `makeCertificate`
usage: avbtool make_certificate [-h] [--output OUTPUT] --subject SUBJECT

- **Subcommand argv path**: `make_certificate`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--authority_key` | `authorityKey` | `STRING` | Path to authority RSA private key file |
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--output` | `output` | `STRING` | Write certificate to file |
| `--signing_helper` | `signingHelper` | `STRING` | Path to helper used for signing |
| `--signing_helper_with_files` | `signingHelperWithFiles` | `STRING` | Path to helper used for signing using files |
| `--subject` | `subject` | `STRING` | Path to subject file |
| `--subject_is_intermediate_authority` | `subjectIsIntermediateAuthority` | `BOOLEAN` | Override usage with the value used for an intermediate |
| `--subject_key` | `subjectKey` | `STRING` | [--subject_key_version SUBJECT_KEY_VERSION] |
| `--subject_key_version` | `subjectKeyVersion` | `STRING` | Version of the subject key |
| `--usage` | `usage` | `STRING` | Override usage with a hash of the provided string |
| `--usage_for_unlock` | `usageForUnlock` | `BOOLEAN` | Override usage with the value used for authenticated |

### `makeVbmetaImage`
usage: avbtool make_vbmeta_image [-h] [--output OUTPUT]

- **Subcommand argv path**: `make_vbmeta_image`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--algorithm` | `algorithm` | `STRING` | Algorithm to use (default: NONE) |
| `--append_to_release_string` | `appendToReleaseString` | `STRING` | Text to append to release string |
| `--chain_partition` | `chainPartition` | `BOOLEAN` | PART_NAME:ROLLBACK_SLOT:KEY_PATH |
| `--chain_partition_do_not_use_ab` | `chainPartitionDoNotUseAb` | `BOOLEAN` | PART_NAME:ROLLBACK_SLOT:KEY_PATH |
| `--flags` | `flags` | `STRING` | VBMeta flags |
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--include_descriptors_from_image` | `includeDescriptorsFromImage` | `STRING` | Include descriptors from image |
| `--kernel_cmdline` | `kernelCmdline` | `STRING` | Add kernel cmdline |
| `--key` | `key` | `STRING` | Path to RSA private key file |
| `--output` | `output` | `STRING` | Output file name |
| `--padding_size` | `paddingSize` | `STRING` | If non-zero, pads output with NUL bytes so its size is |
| `--print_required_libavb_version` | `printRequiredLibavbVersion` | `BOOLEAN` | Don't store the footer - instead calculate the |
| `--prop` | `prop` | `BOOLEAN` | KEY:VALUE      Add property |
| `--prop_from_file` | `propFromFile` | `BOOLEAN` | KEY:PATH |
| `--public_key_metadata` | `publicKeyMetadata` | `STRING` | Path to public key metadata file |
| `--rollback_index` | `rollbackIndex` | `STRING` | Rollback Index |
| `--rollback_index_location` | `rollbackIndexLocation` | `STRING` | Location of main vbmeta Rollback Index |
| `--set_hashtree_disabled_flag` | `setHashtreeDisabledFlag` | `BOOLEAN` | Set the HASHTREE_DISABLED flag |
| `--set_verification_disabled_flag` | `setVerificationDisabledFlag` | `BOOLEAN` | Set the VERIFICATION_DISABLED flag |
| `--setup_rootfs_from_kernel` | `setupRootfsFromKernel` | `BOOLEAN` | IMAGE, --generate_dm_verity_cmdline_from_hashtree IMAGE |
| `--signing_helper` | `signingHelper` | `STRING` | Path to helper used for signing |
| `--signing_helper_with_files` | `signingHelperWithFiles` | `STRING` | Path to helper used for signing using files |

### `printPartitionDigests`
usage: avbtool print_partition_digests [-h] --image IMAGE [--output OUTPUT]

- **Subcommand argv path**: `print_partition_digests`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--image` | `image` | `STRING` | Image to print partition digests from |
| `--json` | `json` | `BOOLEAN` | Print output as JSON |
| `--output` | `output` | `STRING` | Write info to file |

### `resignImage`
usage: avbtool resign_image [-h] --image IMAGE --key KEY --algorithm ALGORITHM

- **Subcommand argv path**: `resign_image`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--algorithm` | `algorithm` | `STRING` | Algorithm to use |
| `--auto_resize` | `autoResize` | `BOOLEAN` | Automatically resize the image if the new key is |
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--image` | `image` | `STRING` | Image to resign |
| `--key` | `key` | `STRING` | Path to RSA private key file |
| `--signing_helper` | `signingHelper` | `STRING` | Program that signs a hash and returns a signature. |
| `--signing_helper_with_files` | `signingHelperWithFiles` | `PATH` | Same as signing_helper but uses files for |

### `resizeImage`
usage: avbtool resize_image [-h] --image IMAGE

- **Subcommand argv path**: `resize_image`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--image` | `image` | `STRING` | Image with a footer |
| `--partition_size` | `partitionSize` | `STRING` | New partition size |

### `setAbMetadata`
usage: avbtool set_ab_metadata [-h] --misc_image MISC_IMAGE

- **Subcommand argv path**: `set_ab_metadata`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--misc_image` | `miscImage` | `STRING` | The misc image to modify. If the image does not exist, |
| `--slot_data` | `slotData` | `STRING` | Slot data of the form "priority", "tries_remaining", |

### `testing`
usage: avbtool [-h]

- **Subcommand argv path**: `testing`
- **Risk Override**: `HOST_WRITE`

### `updatePartitionDescriptor`
usage: avbtool update_partition_descriptor [-h] --image IMAGE

- **Subcommand argv path**: `update_partition_descriptor`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--algorithm` | `algorithm` | `STRING` | Algorithm to use (default: NONE) |
| `--append_to_release_string` | `appendToReleaseString` | `STRING` | Text to append to release string |
| `--chain_partition` | `chainPartition` | `BOOLEAN` | PART_NAME:ROLLBACK_SLOT:KEY_PATH |
| `--chain_partition_do_not_use_ab` | `chainPartitionDoNotUseAb` | `BOOLEAN` | PART_NAME:ROLLBACK_SLOT:KEY_PATH |
| `--flags` | `flags` | `STRING` | VBMeta flags |
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--image` | `image` | `STRING` | The VBMeta image to update. |
| `--include_descriptors_from_image` | `includeDescriptorsFromImage` | `STRING` | Include descriptors from image |
| `--kernel_cmdline` | `kernelCmdline` | `STRING` | Add kernel cmdline |
| `--key` | `key` | `STRING` | Path to RSA private key file |
| `--output` | `output` | `STRING` | [--algorithm ALGORITHM] [--key KEY] |
| `--partition_image` | `partitionImage` | `STRING` |  |
| `--print_required_libavb_version` | `printRequiredLibavbVersion` | `BOOLEAN` | Don't store the footer - instead calculate the |
| `--prop` | `prop` | `BOOLEAN` | KEY:VALUE      Add property |
| `--prop_from_file` | `propFromFile` | `BOOLEAN` | KEY:PATH |
| `--public_key_metadata` | `publicKeyMetadata` | `STRING` | Path to public key metadata file |
| `--rollback_index` | `rollbackIndex` | `STRING` | Rollback Index |
| `--rollback_index_location` | `rollbackIndexLocation` | `STRING` | Location of main vbmeta Rollback Index |
| `--set_hashtree_disabled_flag` | `setHashtreeDisabledFlag` | `BOOLEAN` | Set the HASHTREE_DISABLED flag |
| `--set_verification_disabled_flag` | `setVerificationDisabledFlag` | `BOOLEAN` | Set the VERIFICATION_DISABLED flag |
| `--setup_rootfs_from_kernel` | `setupRootfsFromKernel` | `BOOLEAN` | IMAGE, --generate_dm_verity_cmdline_from_hashtree IMAGE |
| `--signing_helper` | `signingHelper` | `STRING` | Path to helper used for signing |
| `--signing_helper_with_files` | `signingHelperWithFiles` | `STRING` | Path to helper used for signing using files |

### `verifyImage`
usage: avbtool verify_image [-h] --image IMAGE [--key KEY]

- **Subcommand argv path**: `verify_image`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--accept_zeroed_hashtree` | `acceptZeroedHashtree` | `BOOLEAN` | Accept images where the hashtree or FEC data is zeroed |
| `--expected_chain_partition` | `expectedChainPartition` | `BOOLEAN` | PART_NAME:ROLLBACK_SLOT:KEY_PATH |
| `--follow_chain_partitions` | `followChainPartitions` | `BOOLEAN` | Follows chain partitions even when not specified with |
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--image` | `image` | `STRING` | Image to verify |
| `--key` | `key` | `STRING` | Check embedded public key matches KEY |

### `version`
usage: avbtool version [-h]

- **Subcommand argv path**: `version`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |

### `zeroHashtree`
usage: avbtool zero_hashtree [-h] --image IMAGE

- **Subcommand argv path**: `zero_hashtree`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help, -h` | `help` | `BOOLEAN` | show this help message and exit |
| `--image` | `image` | `STRING` | Image with a footer |
