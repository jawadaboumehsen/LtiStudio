# Aapt2 Tool Reference

## Metadata
- **Executable**: `aapt2`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `READ_ONLY`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `df312db814c018019b2b79a993b041b738d7b76f4a8f28da25bc874a026368cc`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Displays this help menu |

## Commands
### `apkinfo`
aapt2 apkinfo [options] -o arg files...

- **Subcommand argv path**: `apkinfo`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-o` | `O` | `BOOLEAN` | arg                                            Output path |
| `--help` | `help` | `BOOLEAN` | Displays this help menu |
| `--include-resource-table` | `includeResourceTable` | `BOOLEAN` | Include the resource table data into output. |
| `--include-xml` | `includeXml` | `BOOLEAN` | arg                                 Include an XML file content into output. Multiple XML files might be requested during single invocation. |

### `compile`
aapt2 compile [options] -o arg files...

- **Subcommand argv path**: `compile`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-o` | `O` | `BOOLEAN` | arg                                            Output path |
| `-v` | `V` | `BOOLEAN` | Enables verbose logging |
| `--dir` | `dir` | `BOOLEAN` | arg                                         Directory to scan for resources |
| `--feature-flags` | `featureFlags` | `BOOLEAN` | arg                               Specify the values of feature flags. The pairs in the argument |
| `--filter-product` | `filterProduct` | `BOOLEAN` | arg                              Leave only resources specific to the given product. All other resources (including defaults) are removed. |
| `--help` | `help` | `BOOLEAN` | Displays this help menu |
| `--legacy` | `legacy` | `BOOLEAN` | Treat errors that used to be valid in AAPT as warnings |
| `--no-crunch` | `noCrunch` | `BOOLEAN` | Disables PNG processing |
| `--output-text-symbols` | `outputTextSymbols` | `BOOLEAN` | arg                         Generates a text file containing the resource symbols in the |
| `--png-compression-level` | `pngCompressionLevel` | `BOOLEAN` | arg                       Set the zlib compression level for crunched PNG images, [0-9], 9 by default. |
| `--preserve-visibility-of-styleables` | `preserveVisibilityOfStyleables` | `BOOLEAN` | If specified, apply the same visibility rules for |
| `--pseudo-localize` | `pseudoLocalize` | `BOOLEAN` | Generate resources for pseudo-locales (en-XA and ar-XB) |
| `--pseudo-localize-gender-ratio` | `pseudoLocalizeGenderRatio` | `BOOLEAN` | arg                Sets the ratio of resources to generate grammatical gender strings for. The ratio has to be a float number between 0 and 1. |
| `--pseudo-localize-gender-values` | `pseudoLocalizeGenderValues` | `BOOLEAN` | arg               Sets the gender values to pick up for generating grammatical gender strings, gender values should be f, m, or n, which are shortcuts for feminine, masculine and neuter, and split with comma. |
| `--source-path` | `sourcePath` | `BOOLEAN` | arg                                 Sets the compiled resource file source file path to the given string. |
| `--trace-folder` | `traceFolder` | `BOOLEAN` | arg                                Generate systrace json trace fragment to specified folder. |
| `--visibility` | `visibility` | `BOOLEAN` | arg                                  Sets the visibility of the compiled resources to the specified |
| `--zip` | `zip` | `BOOLEAN` | arg                                         Zip file containing the res directory to scan for resources |

### `convert`
aapt2 convert [options] -o arg files...

- **Subcommand argv path**: `convert`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-o` | `O` | `BOOLEAN` | arg                                            Output path |
| `-v` | `V` | `BOOLEAN` | Enables verbose logging |
| `--collapse-resource-names` | `collapseResourceNames` | `BOOLEAN` | Collapses resource names to a single value in the key string pool. Resources can |
| `--deduplicate-entry-values` | `deduplicateEntryValues` | `BOOLEAN` | Whether to deduplicate pairs of resource entry and value for simple resources. |
| `--enable-compact-entries` | `enableCompactEntries` | `BOOLEAN` | This decreases APK size by using compact resource entries for simple data types. |
| `--enable-sparse-encoding` | `enableSparseEncoding` | `BOOLEAN` | Enables encoding sparse entries using a binary search tree. |
| `--force-sparse-encoding` | `forceSparseEncoding` | `BOOLEAN` | Enables encoding sparse entries using a binary search tree. |
| `--help` | `help` | `BOOLEAN` | Displays this help menu |
| `--keep-raw-values` | `keepRawValues` | `BOOLEAN` | Preserve raw attribute values in xml files when using the 'binary' output format |
| `--output-format` | `outputFormat` | `BOOLEAN` | arg                               Format of the output. Accepted values are 'proto' and 'binary'. When not set, defaults to 'binary'. |
| `--resources-config-path` | `resourcesConfigPath` | `BOOLEAN` | arg                       Path to the resources.cfg file containing the list of resources and |

### `daemon`
aapt2 daemon [options] files...

- **Subcommand argv path**: `daemon`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Displays this help menu |
| `--trace_folder` | `traceFolder` | `BOOLEAN` | arg                                Generate systrace json trace fragment to specified folder. |

### `diff`
aapt2 diff [options] files...

- **Subcommand argv path**: `diff`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Displays this help menu |
| `--ignore-id-shift` | `ignoreIdShift` | `BOOLEAN` | Match the resources when their IDs shift, e.g. because of the added |

### `dump`
aapt2 dump [subcommand] [options] files...

- **Subcommand argv path**: `dump`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Displays this help menu |

### `dumpApc`
dump apc [options] files...

- **Subcommand argv path**: `dump apc`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-v` | `V` | `BOOLEAN` | Enables verbose logging. |
| `--help` | `help` | `BOOLEAN` | Displays this help menu |
| `--no-values` | `noValues` | `BOOLEAN` | Suppresses output of values when displaying resource tables. |

### `dumpBadging`
dump badging [options] files...

- **Subcommand argv path**: `dump badging`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Displays this help menu |
| `--include-meta-data` | `includeMetaData` | `BOOLEAN` | Include meta-data information. |

#### Positional Arguments
| Argument | Type | Required | Description |
|---|---|---|---|
| `file` | `PATH` | `true` | APK file to dump |

### `dumpChunks`
dump chunks [options] files...

- **Subcommand argv path**: `dump chunks`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Displays this help menu |

### `dumpConfigurations`
dump configurations [options] files...

- **Subcommand argv path**: `dump configurations`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Displays this help menu |

### `dumpOverlayable`
dump overlayable [options] files...

- **Subcommand argv path**: `dump overlayable`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Displays this help menu |

### `dumpPackagename`
dump packagename [options] files...

- **Subcommand argv path**: `dump packagename`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Displays this help menu |

### `dumpPermissions`
dump permissions [options] files...

- **Subcommand argv path**: `dump permissions`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Displays this help menu |

### `dumpResources`
dump resources [options] files...

- **Subcommand argv path**: `dump resources`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-v` | `V` | `BOOLEAN` | Enables verbose logging. |
| `--help` | `help` | `BOOLEAN` | Displays this help menu |
| `--no-values` | `noValues` | `BOOLEAN` | Suppresses output of values when displaying resource tables. |

### `dumpStrings`
dump strings [options] files...

- **Subcommand argv path**: `dump strings`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Displays this help menu |

### `dumpStyleparents`
dump styleparents [options] --style arg files...

- **Subcommand argv path**: `dump styleparents`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Displays this help menu |
| `--style` | `style` | `BOOLEAN` | arg                                       The name of the style to print |

### `dumpXmlstrings`
dump xmlstrings [options] --file arg files...

- **Subcommand argv path**: `dump xmlstrings`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--file` | `file` | `BOOLEAN` | arg                                        A compiled xml file to print |
| `--help` | `help` | `BOOLEAN` | Displays this help menu |

### `dumpXmltree`
dump xmltree [options] --file arg files...

- **Subcommand argv path**: `dump xmltree`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--file` | `file` | `BOOLEAN` | arg                                        A compiled xml file to print |
| `--help` | `help` | `BOOLEAN` | Displays this help menu |

### `link`
aapt2 link [options] -o arg --manifest arg files...

- **Subcommand argv path**: `link`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-A` | `A` | `BOOLEAN` | arg                                            An assets directory to include in the APK. These are unprocessed. |
| `-c` | `C` | `BOOLEAN` | arg                                            Comma separated list of configurations to include. The default |
| `-e` | `E` | `BOOLEAN` | arg                                            File containing list of extensions not to compress. |
| `-I` | `I` | `BOOLEAN` | arg                                            Adds an Android APK to link against. |
| `-o` | `O` | `BOOLEAN` | arg                                            Output path. |
| `-R` | `R` | `BOOLEAN` | arg                                            Compilation unit to link, using `overlay` semantics. |
| `-v` | `V` | `BOOLEAN` | Enables verbose logging. |
| `-x` | `X` | `BOOLEAN` | Legacy flag that specifies to use the package identifier 0x01. |
| `-z` | `Z` | `BOOLEAN` | Require localization of strings marked 'suggested'. |
| `--add-javadoc-annotation` | `addJavadocAnnotation` | `BOOLEAN` | arg                      Adds a JavaDoc annotation to all generated Java classes. |
| `--allow-reserved-package-id` | `allowReservedPackageId` | `BOOLEAN` | Allows the use of a reserved package ID. This should on be used for |
| `--auto-add-overlay` | `autoAddOverlay` | `BOOLEAN` | Allows the addition of new resources in overlays without |
| `--compile-sdk-version-code` | `compileSdkVersionCode` | `BOOLEAN` | arg                    Version code (integer) to inject into the AndroidManifest.xml if none is |
| `--compile-sdk-version-name` | `compileSdkVersionName` | `BOOLEAN` | arg                    Version name to inject into the AndroidManifest.xml if none is present. |
| `--custom-package` | `customPackage` | `BOOLEAN` | arg                              Custom Java package under which to generate R.java. |
| `--debug-mode` | `debugMode` | `BOOLEAN` | Inserts android:debuggable="true" in to the application node of the |
| `--emit-ids` | `emitIds` | `BOOLEAN` | arg                                    Emit a file at the given path with a list of name to ID mappings, |
| `--enable-compact-entries` | `enableCompactEntries` | `BOOLEAN` | This decreases APK size by using compact resource entries for simple data types. |
| `--enable-sparse-encoding` | `enableSparseEncoding` | `BOOLEAN` | Enables encoding sparse entries using a binary search tree. |
| `--exclude-configs` | `excludeConfigs` | `BOOLEAN` | arg                             Excludes values of resources whose configs contain the specified qualifiers. |
| `--exclude-sources` | `excludeSources` | `BOOLEAN` | Do not serialize source file information when generating resources in |
| `--extra-packages` | `extraPackages` | `BOOLEAN` | arg                              Generate the same R.java but with different package names. |
| `--feature-flags` | `featureFlags` | `BOOLEAN` | arg                               Specify the values of feature flags. The pairs in the argument |
| `--fingerprint-prefix` | `fingerprintPrefix` | `BOOLEAN` | arg                          Fingerprint prefix to add to install constraints. |
| `--help` | `help` | `BOOLEAN` | Displays this help menu |
| `--java` | `java` | `BOOLEAN` | arg                                        Directory in which to generate R.java. |
| `--keep-raw-values` | `keepRawValues` | `BOOLEAN` | Preserve raw attribute values in xml files. |
| `--manifest` | `manifest` | `BOOLEAN` | arg                                    Path to the Android manifest to build. |
| `--merge-only` | `mergeOnly` | `BOOLEAN` | Only merge the resources, without verifying resource references. This flag |
| `--min-sdk-version` | `minSdkVersion` | `BOOLEAN` | arg                             Default minimum SDK version to use for AndroidManifest.xml. |
| `--no-auto-version` | `noAutoVersion` | `BOOLEAN` | Disables automatic style and layout SDK versioning. |
| `--no-compile-sdk-metadata` | `noCompileSdkMetadata` | `BOOLEAN` | Suppresses output of compile SDK-related attributes in AndroidManifest.xml, |
| `--no-compress` | `noCompress` | `BOOLEAN` | Do not compress any resources. |
| `--no-compress-fonts` | `noCompressFonts` | `BOOLEAN` | Do not compress files with common extensions for fonts. |
| `--no-compress-regex` | `noCompressRegex` | `BOOLEAN` | arg                           Do not compress extensions matching the regular expression. Remember to |
| `--no-proguard-location-reference` | `noProguardLocationReference` | `BOOLEAN` | Keep proguard rules files from having a reference to the source file |
| `--no-resource-deduping` | `noResourceDeduping` | `BOOLEAN` | Disables automatic deduping of resources with |
| `--no-resource-removal` | `noResourceRemoval` | `BOOLEAN` | Disables automatic removal of resources without |
| `--no-static-lib-packages` | `noStaticLibPackages` | `BOOLEAN` | Merge all library resources under the app's package. |
| `--no-version-transitions` | `noVersionTransitions` | `BOOLEAN` | Disables automatic versioning of transition resources. Use this only |
| `--no-version-vectors` | `noVersionVectors` | `BOOLEAN` | Disables automatic versioning of vector drawables. Use this only |
| `--no-xml-namespaces` | `noXmlNamespaces` | `BOOLEAN` | Removes XML namespace prefix and URI information |
| `--non-final-ids` | `nonFinalIds` | `BOOLEAN` | Generates R.java without the final modifier. This is implied when |
| `--non-updatable-system` | `nonUpdatableSystem` | `BOOLEAN` | Mark the app as a non-updatable system app. This inserts |
| `--output-text-symbols` | `outputTextSymbols` | `BOOLEAN` | arg                         Generates a text file containing the resource symbols of the R class in |
| `--output-to-dir` | `outputToDir` | `BOOLEAN` | Outputs the APK contents to a directory specified by -o. |
| `--override-styles-instead-of-overlaying` | `overrideStylesInsteadOfOverlaying` | `BOOLEAN` | Causes styles defined in -R resources to replace previous definitions |
| `--package-id` | `packageId` | `BOOLEAN` | arg                                  Specify the package ID to use for this app. Must be greater or equal to |
| `--preferred-density` | `preferredDensity` | `BOOLEAN` | arg                           Selects the closest matching density and strips out all others. |
| `--private-symbols` | `privateSymbols` | `BOOLEAN` | arg                             Package name to use when generating R.java for private symbols. |
| `--product` | `product` | `BOOLEAN` | arg                                     Comma separated list of product names to keep |
| `--proguard` | `proguard` | `BOOLEAN` | arg                                    Output file for generated Proguard rules. |
| `--proguard-conditional-keep-rules` | `proguardConditionalKeepRules` | `BOOLEAN` | Generate conditional Proguard keep rules. |
| `--proguard-main-dex` | `proguardMainDex` | `BOOLEAN` | arg                           Output file for generated Proguard rules for the main dex. |
| `--proguard-minimal-keep-rules` | `proguardMinimalKeepRules` | `BOOLEAN` | Generate a minimal set of Proguard keep rules. |
| `--proto-format` | `protoFormat` | `BOOLEAN` | Generates compiled resources in Protobuf format. |
| `--rename-instrumentation-target-package` | `renameInstrumentationTargetPackage` | `BOOLEAN` | arg       Changes the name of the target package for instrumentation. Most useful |
| `--rename-manifest-package` | `renameManifestPackage` | `BOOLEAN` | arg                     Renames the package in AndroidManifest.xml. |
| `--rename-overlay-category` | `renameOverlayCategory` | `BOOLEAN` | arg                     Changes the category for the overlay. |
| `--rename-overlay-target-package` | `renameOverlayTargetPackage` | `BOOLEAN` | arg               Changes the name of the target package for overlay. Most useful |
| `--rename-resources-package` | `renameResourcesPackage` | `BOOLEAN` | arg                    Renames the package in resources table |
| `--replace-version` | `replaceVersion` | `BOOLEAN` | If --version-code, --version-name, and/or --revision-code are specified, these |
| `--revision-code` | `revisionCode` | `BOOLEAN` | arg                               Revision code (integer) to inject into the AndroidManifest.xml if none is |
| `--shared-lib` | `sharedLib` | `BOOLEAN` | Generates a shared Android runtime library. |
| `--split` | `split` | `BOOLEAN` | arg                                       Split resources matching a set of configs out to a Split APK. |
| `--stable-ids` | `stableIds` | `BOOLEAN` | arg                                  File containing a list of name to ID mapping. |
| `--static-lib` | `staticLib` | `BOOLEAN` | Generate a static Android library. |
| `--strict-visibility` | `strictVisibility` | `BOOLEAN` | Do not allow overlays with different visibility levels. |
| `--target-sdk-version` | `targetSdkVersion` | `BOOLEAN` | arg                          Default target SDK version to use for AndroidManifest.xml. |
| `--trace-folder` | `traceFolder` | `BOOLEAN` | arg                                Generate systrace json trace fragment to specified folder. |
| `-0` | `value0` | `BOOLEAN` | arg                                            File suffix not to compress. |
| `--version-code` | `versionCode` | `BOOLEAN` | arg                                Version code (integer) to inject into the AndroidManifest.xml if none is |
| `--version-code-major` | `versionCodeMajor` | `BOOLEAN` | arg                          Version code major (integer) to inject into the AndroidManifest.xml if none is |
| `--version-name` | `versionName` | `BOOLEAN` | arg                                Version name to inject into the AndroidManifest.xml if none is present. |
| `--warn-manifest-validation` | `warnManifestValidation` | `BOOLEAN` | Treat manifest validation errors as warnings. |

### `optimize`
aapt2 optimize [options] files...

- **Subcommand argv path**: `optimize`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `-c` | `C` | `BOOLEAN` | arg                                            Comma separated list of configurations to include. The default |
| `-d` | `D` | `BOOLEAN` | arg                                            Path to the output directory (for splits). |
| `-o` | `O` | `BOOLEAN` | arg                                            Path to the output APK. |
| `-p` | `P` | `BOOLEAN` | Print the multi APK artifacts and exit. |
| `-v` | `V` | `BOOLEAN` | Enables verbose logging |
| `-x` | `X` | `BOOLEAN` | arg                                            Path to XML configuration file. |
| `--collapse-resource-names` | `collapseResourceNames` | `BOOLEAN` | Collapses resource names to a single value in the key string pool. Resources can |
| `--deduplicate-entry-values` | `deduplicateEntryValues` | `BOOLEAN` | Whether to deduplicate pairs of resource entry and value for simple resources. |
| `--enable-compact-entries` | `enableCompactEntries` | `BOOLEAN` | This decreases APK size by using compact resource entries for simple data types. |
| `--enable-sparse-encoding` | `enableSparseEncoding` | `BOOLEAN` | Enables encoding sparse entries using a binary search tree. |
| `--force-sparse-encoding` | `forceSparseEncoding` | `BOOLEAN` | Enables encoding sparse entries using a binary search tree. |
| `--help` | `help` | `BOOLEAN` | Displays this help menu |
| `--keep-artifacts` | `keepArtifacts` | `BOOLEAN` | arg                              Comma separated list of artifacts to keep. If none are specified, |
| `--resource-path-shortening-map` | `resourcePathShorteningMap` | `BOOLEAN` | arg                [Deprecated]Path to output the map of old resource paths to shortened paths. |
| `--resources-config-path` | `resourcesConfigPath` | `BOOLEAN` | arg                       Path to the resources.cfg file containing the list of resources and |
| `--save-obfuscation-map` | `saveObfuscationMap` | `BOOLEAN` | arg                        Path to output the map of original paths/names to obfuscated paths/names. |
| `--shorten-resource-paths` | `shortenResourcePaths` | `BOOLEAN` | Shortens the paths of resources inside the APK. Resources can be exempted using the |
| `--split` | `split` | `BOOLEAN` | arg                                       Split resources matching a set of configs out to a Split APK. |
| `--target-densities` | `targetDensities` | `BOOLEAN` | arg                            Comma separated list of the screen densities that the APK will be optimized for. |

### `version`
aapt2 version [options] files...

- **Subcommand argv path**: `version`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Displays this help menu |
