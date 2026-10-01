# Gh Tool Reference

## Metadata
- **Executable**: `gh`
- **Platform Support**: `linux-x86_64`
- **Default Risk**: `READ_ONLY`
- **Default Timeout**: `30s`
- **Source Revision**: `HEAD`
- **Binary SHA-256**: `141507c337e8b202ad398550c3b73d72f5af92e86f71665214538a81efd4c409`

## Global Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--version` | `version` | `BOOLEAN` | Show gh version |

## Commands
### `agentTask`
Working with agent tasks in the GitHub CLI is in preview and

- **Subcommand argv path**: `agent-task`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `agentTaskCreate`
Create an agent task (preview)

- **Subcommand argv path**: `agent-task create`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--base, -b` | `base` | `BOOLEAN` | string              Base branch for the pull request (use default branch if not provided) |
| `--custom-agent, -a` | `customAgent` | `BOOLEAN` | string      Use a custom agent for the task. e.g., use 'my-agent' for the 'my-agent.md' agent |
| `--follow` | `follow` | `BOOLEAN` | Follow agent session logs |
| `--from-file, -F` | `fromFile` | `BOOLEAN` | file           Read task description from file (use "-" to read from standard input) |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `agentTaskList`
List agent tasks (preview)

- **Subcommand argv path**: `agent-task list`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields       Output JSON with the specified fields |
| `--limit, -L` | `limit` | `BOOLEAN` | int         Maximum number of agent tasks to fetch (default 30) |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |
| `--web, -w` | `web` | `BOOLEAN` | Open agent tasks in the browser |

### `agentTaskView`
View an agent task session.

- **Subcommand argv path**: `agent-task view`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--follow` | `follow` | `BOOLEAN` | Follow agent session logs |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression            Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields              Output JSON with the specified fields |
| `--log` | `log` | `BOOLEAN` | Show agent session logs |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--template, -t` | `template` | `BOOLEAN` | string          Format JSON output using a Go template; see "gh help formatting" |
| `--web, -w` | `web` | `BOOLEAN` | Open agent task in the browser |

### `alias`
Aliases can be used to make shortcuts for gh commands or to compose multiple commands.

- **Subcommand argv path**: `alias`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `aliasDelete`
Delete set aliases

- **Subcommand argv path**: `alias delete`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--all` | `all` | `BOOLEAN` | Delete all aliases |
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `aliasImport`
Import aliases from the contents of a YAML file.

- **Subcommand argv path**: `alias import`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--clobber` | `clobber` | `BOOLEAN` | Overwrite existing aliases of the same name |
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `aliasList`
This command prints out all of the aliases gh is configured to use.

- **Subcommand argv path**: `alias list`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `aliasSet`
Define a word that will expand to a full gh command when invoked.

- **Subcommand argv path**: `alias set`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--clobber` | `clobber` | `BOOLEAN` | Overwrite existing aliases of the same name |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--shell, -s` | `shell` | `BOOLEAN` | Declare an alias to be passed through a shell interpreter |

### `api`
Makes an authenticated HTTP request to the GitHub API and prints the response.

- **Subcommand argv path**: `api`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--allow-escape-sequences` | `allowEscapeSequences` | `BOOLEAN` | Allow printing terminal escape sequences |
| `--cache` | `cache` | `BOOLEAN` | duration           Cache the response, e.g. "3600s", "60m", "1h" |
| `--field, -F` | `field` | `BOOLEAN` | key=value          Add a typed parameter in key=value format (use "@<path>" or "@-" to read value from file or stdin) |
| `--header, -H` | `header` | `BOOLEAN` | key:value         Add a HTTP request header in key:value format |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--hostname` | `hostname` | `BOOLEAN` | string          The GitHub hostname for the request (default "github.com") |
| `--include, -i` | `include` | `BOOLEAN` | Include HTTP response status line and headers in the output |
| `--input` | `input` | `BOOLEAN` | file               The file to use as body for the HTTP request (use "-" to read from standard input) |
| `--jq, -q` | `jq` | `BOOLEAN` | string                Query to select values from the response using jq syntax |
| `--method, -X` | `method` | `BOOLEAN` | string            The HTTP method for the request (default "GET") |
| `--paginate` | `paginate` | `BOOLEAN` | Make additional HTTP requests to fetch all pages of results |
| `--preview, -p` | `preview` | `BOOLEAN` | strings          Opt into GitHub API previews (names should omit '-preview') |
| `--raw-field, -f` | `rawField` | `BOOLEAN` | key=value      Add a string parameter in key=value format |
| `--silent` | `silent` | `BOOLEAN` | Do not print the response body |
| `--slurp` | `slurp` | `BOOLEAN` | Use with "--paginate" to return an array of all pages of either JSON arrays or objects |
| `--template, -t` | `template` | `BOOLEAN` | string          Format JSON output using a Go template; see "gh help formatting" |
| `--verbose` | `verbose` | `BOOLEAN` | Include full HTTP request and response in the output |

### `attestation`
Download and verify artifact attestations.

- **Subcommand argv path**: `attestation`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `attestationDownload`
### NOTE: This feature is currently in public preview, and subject to change.

- **Subcommand argv path**: `attestation download`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--digest-alg, -d` | `digestAlg` | `BOOLEAN` | string       The algorithm used to compute a digest of the artifact: {sha256|sha512} (default "sha256") |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--hostname` | `hostname` | `BOOLEAN` | string         Configure host to use |
| `--limit, -L` | `limit` | `BOOLEAN` | int               Maximum number of attestations to fetch (default 30) |
| `--owner, -o` | `owner` | `BOOLEAN` | string            GitHub organization to scope attestation lookup by |
| `--predicate-type` | `predicateType` | `BOOLEAN` | string   Filter attestations by provided predicate type |
| `--repo, -R` | `repo` | `BOOLEAN` | string             Repository name in the format <owner>/<repo> |

### `attestationTrustedRoot`
Output contents for a trusted_root.jsonl file, likely for offline verification.

- **Subcommand argv path**: `attestation trusted-root`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--hostname` | `hostname` | `BOOLEAN` | string   Configure host to use |
| `--tuf-root` | `tufRoot` | `BOOLEAN` | string   Path to the TUF root.json file on disk |
| `--tuf-url` | `tufUrl` | `BOOLEAN` | string    URL to the TUF repository mirror |
| `--verify-only` | `verifyOnly` | `BOOLEAN` | Don't output trusted_root.jsonl contents |

### `attestationVerify`
Verify the integrity and provenance of an artifact using its associated

- **Subcommand argv path**: `attestation verify`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--bundle, -b` | `bundle` | `BOOLEAN` | string                Path to bundle on disk, either a single bundle in a JSON file or a JSON lines file with multiple bundles |
| `--bundle-from-oci` | `bundleFromOci` | `BOOLEAN` | When verifying an OCI image, fetch the attestation bundle from the OCI registry instead of from GitHub |
| `--cert-identity` | `certIdentity` | `BOOLEAN` | string         Enforce that the certificate's SubjectAlternativeName matches the provided value exactly |
| `--cert-identity-regex, -i` | `certIdentityRegex` | `BOOLEAN` | string   Enforce that the certificate's SubjectAlternativeName matches the provided regex |
| `--cert-oidc-issuer` | `certOidcIssuer` | `BOOLEAN` | string      Enforce that the issuer of the OIDC token matches the provided value (default "https://token.actions.githubusercontent.com") |
| `--custom-trusted-root` | `customTrustedRoot` | `BOOLEAN` | string   Path to a trusted_root.jsonl file; likely for offline verification |
| `--deny-self-hosted-runners` | `denySelfHostedRunners` | `BOOLEAN` | Fail verification for attestations generated on self-hosted runners |
| `--digest-alg, -d` | `digestAlg` | `BOOLEAN` | string            The algorithm used to compute a digest of the artifact: {sha256|sha512} (default "sha256") |
| `--format` | `format` | `BOOLEAN` | string                Output format: {json} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--hostname` | `hostname` | `BOOLEAN` | string              Configure host to use |
| `--jq, -q` | `jq` | `BOOLEAN` | expression                Filter JSON output using a jq expression |
| `--limit, -L` | `limit` | `BOOLEAN` | int                    Maximum number of attestations to fetch (default 30) |
| `--no-public-good` | `noPublicGood` | `BOOLEAN` | Do not verify attestations signed with Sigstore public good instance |
| `--owner, -o` | `owner` | `BOOLEAN` | string                 GitHub organization to scope attestation lookup by |
| `--predicate-type` | `predicateType` | `BOOLEAN` | string        Enforce that verified attestations' predicate type matches the provided value (default "https://slsa.dev/provenance/v1") |
| `--repo, -R` | `repo` | `BOOLEAN` | string                  Repository name in the format <owner>/<repo> |
| `--signer-digest` | `signerDigest` | `BOOLEAN` | string         Enforce that the digest associated with the signer workflow matches the provided value |
| `--signer-repo` | `signerRepo` | `BOOLEAN` | string           Enforce that the workflow that signed the attestation's repository matches the provided value (<owner>/<repo>) |
| `--signer-workflow` | `signerWorkflow` | `BOOLEAN` | string       Enforce that the workflow that signed the attestation matches the provided value ([host/]<owner>/<repo>/<path>/<to>/<workflow>) |
| `--source-digest` | `sourceDigest` | `BOOLEAN` | string         Enforce that the digest associated with the source repository matches the provided value |
| `--source-ref` | `sourceRef` | `BOOLEAN` | string            Enforce that the git ref associated with the source repository matches the provided value |
| `--template, -t` | `template` | `BOOLEAN` | string              Format JSON output using a Go template; see "gh help formatting" |

### `auth`
Authenticate gh and git with GitHub

- **Subcommand argv path**: `auth`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `authLogin`
Authenticate with a GitHub host.

- **Subcommand argv path**: `auth login`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--clipboard, -c` | `clipboard` | `BOOLEAN` | Copy one-time OAuth device code to clipboard |
| `--git-protocol, -p` | `gitProtocol` | `BOOLEAN` | string   The protocol to use for git operations on this host: {ssh|https} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--hostname, -h` | `hostname` | `BOOLEAN` | string       The hostname of the GitHub instance to authenticate with |
| `--insecure-storage` | `insecureStorage` | `BOOLEAN` | Save authentication credentials in plain text instead of credential store |
| `--scopes, -s` | `scopes` | `BOOLEAN` | strings        Additional authentication scopes to request |
| `--skip-ssh-key` | `skipSshKey` | `BOOLEAN` | Skip generate/upload SSH key prompt |
| `--web, -w` | `web` | `BOOLEAN` | Open a browser to authenticate |
| `--with-token` | `withToken` | `BOOLEAN` | Read token from standard input |

### `authLogout`
Remove authentication for a GitHub account.

- **Subcommand argv path**: `auth logout`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--hostname, -h` | `hostname` | `BOOLEAN` | string   The hostname of the GitHub instance to log out of |
| `--user, -u` | `user` | `BOOLEAN` | string       The account to log out of |

### `authRefresh`
Expand or fix the permission scopes for stored credentials for active account.

- **Subcommand argv path**: `auth refresh`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--clipboard, -c` | `clipboard` | `BOOLEAN` | Copy one-time OAuth device code to clipboard |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--hostname, -h` | `hostname` | `BOOLEAN` | string         The GitHub host to use for authentication |
| `--insecure-storage` | `insecureStorage` | `BOOLEAN` | Save authentication credentials in plain text instead of credential store |
| `--remove-scopes, -r` | `removeScopes` | `BOOLEAN` | strings   Authentication scopes to remove from gh |
| `--reset-scopes` | `resetScopes` | `BOOLEAN` | Reset authentication scopes to the default minimum set of scopes |
| `--scopes, -s` | `scopes` | `BOOLEAN` | strings          Additional authentication scopes for gh to have |

### `authSetupGit`
This command configures `git` to use GitHub CLI as a credential helper.

- **Subcommand argv path**: `auth setup-git`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--force, -f` | `force` | `BOOLEAN` | --hostname   Force setup even if the host is not known. Must be used in conjunction with --hostname |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--hostname, -h` | `hostname` | `BOOLEAN` | string    The hostname to configure git for |

### `authStatus`
Display active account and authentication state on each known GitHub host.

- **Subcommand argv path**: `auth status`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--active, -a` | `active` | `BOOLEAN` | Display the active account only |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--hostname, -h` | `hostname` | `BOOLEAN` | string   Check only a specific hostname's auth status |
| `--jq` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields       Output JSON with the specified fields |
| `--show-token, -t` | `showToken` | `BOOLEAN` | Display the auth token |
| `--template` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |

### `authSwitch`
Switch the active account for a GitHub host.

- **Subcommand argv path**: `auth switch`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--hostname, -h` | `hostname` | `BOOLEAN` | string   The hostname of the GitHub instance to switch account for |
| `--user, -u` | `user` | `BOOLEAN` | string       The account to switch to |

### `authToken`
This command outputs the authentication token for an account on a given GitHub host.

- **Subcommand argv path**: `auth token`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--hostname, -h` | `hostname` | `BOOLEAN` | string   The hostname of the GitHub instance authenticated with |
| `--user, -u` | `user` | `BOOLEAN` | string       The account to output the token for |

### `browse`
Transition from the terminal to the web browser to view and interact with:

- **Subcommand argv path**: `browse`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--actions, -a` | `actions` | `BOOLEAN` | Open repository actions |
| `--blame` | `blame` | `BOOLEAN` | Open blame view for a file |
| `--branch, -b` | `branch` | `BOOLEAN` | string            Select another branch by passing in the branch name |
| `--commit, -c` | `commit` | `BOOLEAN` | string[="last"]   Select another commit by passing in the commit SHA, default is the last commit |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--no-browser, -n` | `noBrowser` | `BOOLEAN` | Print destination URL instead of opening the browser |
| `--projects, -p` | `projects` | `BOOLEAN` | Open repository projects |
| `--releases, -r` | `releases` | `BOOLEAN` | Open repository releases |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--settings, -s` | `settings` | `BOOLEAN` | Open repository settings |
| `--wiki, -w` | `wiki` | `BOOLEAN` | Open repository wiki |

### `cache`
Work with GitHub Actions caches.

- **Subcommand argv path**: `cache`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `cacheDelete`
Delete GitHub Actions caches.

- **Subcommand argv path**: `cache delete`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--all, -a` | `all` | `BOOLEAN` | Delete all caches, can be used with --ref to delete all caches for a specific ref |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--ref, -r` | `ref` | `BOOLEAN` | string                   Delete by cache key and ref, formatted as refs/heads/<branch name> or refs/pull/<number>/merge |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--succeed-on-no-caches` | `succeedOnNoCaches` | `BOOLEAN` | Return exit code 0 if no caches found. Must be used in conjunction with --all |

### `cacheList`
List GitHub Actions caches

- **Subcommand argv path**: `cache list`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields       Output JSON with the specified fields |
| `--key, -k` | `key` | `BOOLEAN` | string        Filter by cache key prefix |
| `--limit, -L` | `limit` | `BOOLEAN` | int         Maximum number of caches to fetch (default 30) |
| `--order, -O` | `order` | `BOOLEAN` | string      Order of caches returned: {asc|desc} (default "desc") |
| `--ref, -r` | `ref` | `BOOLEAN` | string        Filter by ref, formatted as refs/heads/<branch name> or refs/pull/<number>/merge |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--sort, -S` | `sort` | `BOOLEAN` | string       Sort fetched caches: {created_at|last_accessed_at|size_in_bytes} (default "last_accessed_at") |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |

### `codespace`
Connect to and manage codespaces

- **Subcommand argv path**: `codespace`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `codespaceCode`
Open a codespace in Visual Studio Code

- **Subcommand argv path**: `codespace code`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--codespace, -c` | `codespace` | `BOOLEAN` | string    Name of the codespace |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--insiders` | `insiders` | `BOOLEAN` | Use the insiders version of Visual Studio Code |
| `--repo, -R` | `repo` | `BOOLEAN` | string         Filter codespace selection by repository name (user/repo) |
| `--repo-owner` | `repoOwner` | `BOOLEAN` | string   Filter codespace selection by repository owner (username or org) |
| `--web, -w` | `web` | `BOOLEAN` | Use the web version of Visual Studio Code |

### `codespaceCp`
The `cp` command copies files between the local and remote file systems.

- **Subcommand argv path**: `codespace cp`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--codespace, -c` | `codespace` | `BOOLEAN` | string    Name of the codespace |
| `--expand, -e` | `expand` | `BOOLEAN` | Expand remote file names on remote shell |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--profile, -p` | `profile` | `BOOLEAN` | string      Name of the SSH profile to use |
| `--recursive, -r` | `recursive` | `BOOLEAN` | Recursively copy directories |
| `--repo, -R` | `repo` | `BOOLEAN` | string         Filter codespace selection by repository name (user/repo) |
| `--repo-owner` | `repoOwner` | `BOOLEAN` | string   Filter codespace selection by repository owner (username or org) |

### `codespaceCreate`
Create a codespace

- **Subcommand argv path**: `codespace create`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--branch, -b` | `branch` | `BOOLEAN` | string               Repository branch |
| `--default-permissions` | `defaultPermissions` | `BOOLEAN` | Do not prompt to accept additional permissions requested by the codespace |
| `--devcontainer-path` | `devcontainerPath` | `BOOLEAN` | string    Path to the devcontainer.json file to use when creating codespace |
| `--display-name, -d` | `displayName` | `BOOLEAN` | string         Display name for the codespace (48 characters or less) |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--idle-timeout` | `idleTimeout` | `BOOLEAN` | duration       Allowed inactivity before codespace is stopped, e.g. "10m", "1h" |
| `--location, -l` | `location` | `BOOLEAN` | string             Location: {EastUs|SouthEastAsia|WestEurope|WestUs2} (determined automatically if not provided) |
| `--machine, -m` | `machine` | `BOOLEAN` | string              Hardware specifications for the VM |
| `--repo, -R` | `repo` | `BOOLEAN` | string                 Repository name with owner: user/repo |
| `--retention-period` | `retentionPeriod` | `BOOLEAN` | duration   Allowed time after shutting down before the codespace is automatically deleted (maximum 30 days), e.g. "1h", "72h" |
| `--status, -s` | `status` | `BOOLEAN` | Show status of post-create command and dotfiles |
| `--web, -w` | `web` | `BOOLEAN` | Create codespace from browser, cannot be used with --display-name, --idle-timeout, or --retention-period |

### `codespaceDelete`
Delete codespaces based on selection criteria.

- **Subcommand argv path**: `codespace delete`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--all` | `all` | `BOOLEAN` | Delete all codespaces |
| `--codespace, -c` | `codespace` | `BOOLEAN` | string    Name of the codespace |
| `--days` | `days` | `STRING` | Delete codespaces older than N days |
| `--force, -f` | `force` | `BOOLEAN` | Skip confirmation for codespaces that contain unsaved changes |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--org, -o` | `org` | `BOOLEAN` | login           The login handle of the organization (admin-only) |
| `--repo, -R` | `repo` | `BOOLEAN` | string         Filter codespace selection by repository name (user/repo) |
| `--repo-owner` | `repoOwner` | `BOOLEAN` | string   Filter codespace selection by repository owner (username or org) |
| `--user, -u` | `user` | `BOOLEAN` | username       The username to delete codespaces for (used with --org) |

### `codespaceEdit`
Edit a codespace

- **Subcommand argv path**: `codespace edit`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--codespace, -c` | `codespace` | `BOOLEAN` | string      Name of the codespace |
| `--display-name, -d` | `displayName` | `BOOLEAN` | string   Set the display name |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--machine, -m` | `machine` | `BOOLEAN` | string        Set hardware specifications for the VM |
| `--repo, -R` | `repo` | `BOOLEAN` | string           Filter codespace selection by repository name (user/repo) |
| `--repo-owner` | `repoOwner` | `BOOLEAN` | string     Filter codespace selection by repository owner (username or org) |

### `codespaceJupyter`
Open a codespace in JupyterLab

- **Subcommand argv path**: `codespace jupyter`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--codespace, -c` | `codespace` | `BOOLEAN` | string    Name of the codespace |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | string         Filter codespace selection by repository name (user/repo) |
| `--repo-owner` | `repoOwner` | `BOOLEAN` | string   Filter codespace selection by repository owner (username or org) |

### `codespaceList`
List codespaces of the authenticated user.

- **Subcommand argv path**: `codespace list`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields       Output JSON with the specified fields |
| `--limit, -L` | `limit` | `BOOLEAN` | int         Maximum number of codespaces to list (default 30) |
| `--org, -o` | `org` | `BOOLEAN` | login         The login handle of the organization to list codespaces for (admin-only) |
| `--repo, -R` | `repo` | `BOOLEAN` | string       Repository name with owner: user/repo |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |
| `--user, -u` | `user` | `BOOLEAN` | username     The username to list codespaces for (used with --org) |
| `--web, -w` | `web` | `BOOLEAN` | List codespaces in the web browser, cannot be used with --user or --org |

### `codespaceLogs`
Access codespace logs

- **Subcommand argv path**: `codespace logs`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--codespace, -c` | `codespace` | `BOOLEAN` | string    Name of the codespace |
| `--follow, -f` | `follow` | `BOOLEAN` | Tail and follow the logs |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | string         Filter codespace selection by repository name (user/repo) |
| `--repo-owner` | `repoOwner` | `BOOLEAN` | string   Filter codespace selection by repository owner (username or org) |

### `codespacePorts`
List ports in a codespace

- **Subcommand argv path**: `codespace ports`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--codespace, -c` | `codespace` | `BOOLEAN` | string    Name of the codespace |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression       Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields         Output JSON with the specified fields |
| `--repo, -R` | `repo` | `BOOLEAN` | string         Filter codespace selection by repository name (user/repo) |
| `--repo-owner` | `repoOwner` | `BOOLEAN` | string   Filter codespace selection by repository owner (username or org) |
| `--template, -t` | `template` | `BOOLEAN` | string     Format JSON output using a Go template; see "gh help formatting" |

### `codespacePortsForward`
Forward ports

- **Subcommand argv path**: `codespace ports forward`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--codespace, -c` | `codespace` | `BOOLEAN` | string    Name of the codespace |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | string         Filter codespace selection by repository name (user/repo) |
| `--repo-owner` | `repoOwner` | `BOOLEAN` | string   Filter codespace selection by repository owner (username or org) |

### `codespacePortsVisibility`
Change the visibility of the forwarded port

- **Subcommand argv path**: `codespace ports visibility`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--codespace, -c` | `codespace` | `BOOLEAN` | string    Name of the codespace |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | string         Filter codespace selection by repository name (user/repo) |
| `--repo-owner` | `repoOwner` | `BOOLEAN` | string   Filter codespace selection by repository owner (username or org) |

### `codespaceRebuild`
Rebuilding recreates your codespace.

- **Subcommand argv path**: `codespace rebuild`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--codespace, -c` | `codespace` | `BOOLEAN` | string    Name of the codespace |
| `--full` | `full` | `BOOLEAN` | Perform a full rebuild |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | string         Filter codespace selection by repository name (user/repo) |
| `--repo-owner` | `repoOwner` | `BOOLEAN` | string   Filter codespace selection by repository owner (username or org) |

### `codespaceSsh`
The `ssh` command is used to SSH into a codespace. In its simplest form, you can

- **Subcommand argv path**: `codespace ssh`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--codespace, -c` | `codespace` | `BOOLEAN` | string    Name of the codespace |
| `--config` | `config` | `BOOLEAN` | Write OpenSSH configuration to stdout |
| `--debug, -d` | `debug` | `BOOLEAN` | Log debug data to a file |
| `--debug-file` | `debugFile` | `BOOLEAN` | string   Path of the file log to |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--profile` | `profile` | `BOOLEAN` | string      Name of the SSH profile to use |
| `--repo, -R` | `repo` | `BOOLEAN` | string         Filter codespace selection by repository name (user/repo) |
| `--repo-owner` | `repoOwner` | `BOOLEAN` | string   Filter codespace selection by repository owner (username or org) |
| `--server-port` | `serverPort` | `BOOLEAN` | int     SSH server port number (0 => pick unused) |

### `codespaceStop`
Stop a running codespace

- **Subcommand argv path**: `codespace stop`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--codespace, -c` | `codespace` | `BOOLEAN` | string    Name of the codespace |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--org, -o` | `org` | `BOOLEAN` | login           The login handle of the organization (admin-only) |
| `--repo, -R` | `repo` | `BOOLEAN` | string         Filter codespace selection by repository name (user/repo) |
| `--repo-owner` | `repoOwner` | `BOOLEAN` | string   Filter codespace selection by repository owner (username or org) |
| `--user, -u` | `user` | `BOOLEAN` | username       The username to stop codespace for (used with --org) |

### `codespaceView`
View details about a codespace

- **Subcommand argv path**: `codespace view`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--codespace, -c` | `codespace` | `BOOLEAN` | string    Name of the codespace |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression       Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields         Output JSON with the specified fields |
| `--repo, -R` | `repo` | `BOOLEAN` | string         Filter codespace selection by repository name (user/repo) |
| `--repo-owner` | `repoOwner` | `BOOLEAN` | string   Filter codespace selection by repository owner (username or org) |
| `--template, -t` | `template` | `BOOLEAN` | string     Format JSON output using a Go template; see "gh help formatting" |

### `completion`
Generate shell completion scripts for GitHub CLI commands.

- **Subcommand argv path**: `completion`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--shell, -s` | `shell` | `BOOLEAN` | string   Shell type: {bash|zsh|fish|powershell} |

### `config`
Display or change configuration settings for gh.

- **Subcommand argv path**: `config`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `configClearCache`
Clear the cli cache

- **Subcommand argv path**: `config clear-cache`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `configGet`
Print the value of a given configuration key

- **Subcommand argv path**: `config get`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--host, -h` | `host` | `BOOLEAN` | string   Get per-host setting |

### `configList`
Print a list of configuration keys and values

- **Subcommand argv path**: `config list`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--host, -h` | `host` | `BOOLEAN` | string   Get per-host configuration |

### `configSet`
Update configuration with a value for the given key

- **Subcommand argv path**: `config set`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--host, -h` | `host` | `BOOLEAN` | string   Set per-host setting |

### `copilot`
Runs the GitHub Copilot CLI.

- **Subcommand argv path**: `copilot`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--remove` | `remove` | `BOOLEAN` | Remove the downloaded Copilot CLI |

### `discussion`
Working with discussions in the GitHub CLI is in preview and subject to change without notice.

- **Subcommand argv path**: `discussion`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `extension`
GitHub CLI extensions are repositories that provide additional gh commands.

- **Subcommand argv path**: `extension`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `extensionBrowse`
This command will take over your terminal and run a fully interactive

- **Subcommand argv path**: `extension browse`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--debug` | `debug` | `BOOLEAN` | Log to /tmp/extBrowse-* |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--single-column, -s` | `singleColumn` | `BOOLEAN` | Render TUI with only one column of text |

### `extensionCreate`
Create a new extension

- **Subcommand argv path**: `extension create`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--precompiled` | `precompiled` | `BOOLEAN` | string   Create a precompiled extension. Possible values: go, other |

### `extensionExec`
extension "--help" not found

- **Subcommand argv path**: `extension exec`
- **Risk Override**: `READ_ONLY`

### `extensionInstall`
Install a GitHub CLI extension from a GitHub or local repository.

- **Subcommand argv path**: `extension install`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--force` | `force` | `BOOLEAN` | Force upgrade extension, or ignore if latest already installed |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--pin` | `pin` | `BOOLEAN` | string   Pin extension to a release tag or commit ref |

### `extensionList`
List installed extension commands

- **Subcommand argv path**: `extension list`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `extensionRemove`
Remove an installed extension

- **Subcommand argv path**: `extension remove`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `extensionSearch`
Search for gh extensions.

- **Subcommand argv path**: `extension search`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields       Output JSON with the specified fields |
| `--license` | `license` | `BOOLEAN` | strings   Filter based on license type |
| `--limit, -L` | `limit` | `BOOLEAN` | int         Maximum number of extensions to fetch (default 30) |
| `--order` | `order` | `BOOLEAN` | string      Order of repositories returned, ignored unless '--sort' flag is specified: {asc|desc} (default "desc") |
| `--owner` | `owner` | `BOOLEAN` | strings     Filter on owner |
| `--sort` | `sort` | `BOOLEAN` | string       Sort fetched repositories: {forks|help-wanted-issues|stars|updated} (default "best-match") |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |
| `--web, -w` | `web` | `BOOLEAN` | Open the search query in the web browser |

### `extensionUpgrade`
Upgrade installed extensions

- **Subcommand argv path**: `extension upgrade`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--all` | `all` | `BOOLEAN` | Upgrade all extensions |
| `--dry-run` | `dryRun` | `BOOLEAN` | Only display upgrades |
| `--force` | `force` | `BOOLEAN` | Force upgrade extension |
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `gist`
Work with GitHub gists.

- **Subcommand argv path**: `gist`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `gistClone`
Clone a GitHub gist locally.

- **Subcommand argv path**: `gist clone`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `gistCreate`
Create a new GitHub gist with given contents.

- **Subcommand argv path**: `gist create`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--desc, -d` | `desc` | `BOOLEAN` | string       A description for this gist |
| `--filename, -f` | `filename` | `BOOLEAN` | string   Provide a filename to be used when reading from standard input |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--public, -p` | `public` | `BOOLEAN` | List the gist publicly (default "secret") |
| `--web, -w` | `web` | `BOOLEAN` | Open the web browser with created gist |

### `gistDelete`
Delete a GitHub gist.

- **Subcommand argv path**: `gist delete`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--yes` | `yes` | `BOOLEAN` | Confirm deletion without prompting |

### `gistEdit`
Edit one of your gists

- **Subcommand argv path**: `gist edit`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--add, -a` | `add` | `BOOLEAN` | string        Add a new file to the gist |
| `--desc, -d` | `desc` | `BOOLEAN` | string       New description for the gist |
| `--filename, -f` | `filename` | `BOOLEAN` | string   Select a file to edit |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--remove, -r` | `remove` | `BOOLEAN` | string     Remove a file from the gist |

### `gistList`
List gists from your user account.

- **Subcommand argv path**: `gist list`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--filter` | `filter` | `BOOLEAN` | expression   Filter gists using a regular expression |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--include-content` | `includeContent` | `BOOLEAN` | Include gists' file content when filtering |
| `--limit, -L` | `limit` | `BOOLEAN` | int           Maximum number of gists to fetch (default 10) |
| `--public` | `public` | `BOOLEAN` | Show only public gists |
| `--secret` | `secret` | `BOOLEAN` | Show only secret gists |

### `gistRename`
Rename a file in the given gist ID / URL.

- **Subcommand argv path**: `gist rename`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `gistView`
View the given gist or select from recent gists.

- **Subcommand argv path**: `gist view`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--allow-escape-sequences` | `allowEscapeSequences` | `BOOLEAN` | Allow printing terminal escape sequences |
| `--filename, -f` | `filename` | `BOOLEAN` | string          Display a single file from the gist |
| `--files` | `files` | `BOOLEAN` | List file names from the gist |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--raw, -r` | `raw` | `BOOLEAN` | Print raw instead of rendered gist contents |
| `--web, -w` | `web` | `BOOLEAN` | Open gist in the browser |

### `gpgKey`
Manage GPG keys registered with your GitHub account.

- **Subcommand argv path**: `gpg-key`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `gpgKeyAdd`
Add a GPG key to your GitHub account

- **Subcommand argv path**: `gpg-key add`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--title, -t` | `title` | `BOOLEAN` | string   Title for the new key |

### `gpgKeyDelete`
Delete a GPG key from your GitHub account

- **Subcommand argv path**: `gpg-key delete`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--yes, -y` | `yes` | `BOOLEAN` | Skip the confirmation prompt |

### `gpgKeyList`
Lists GPG keys in your GitHub account

- **Subcommand argv path**: `gpg-key list`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `issue`
Work with GitHub issues.

- **Subcommand argv path**: `issue`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `label`
Work with GitHub labels.

- **Subcommand argv path**: `label`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `labelClone`
Clones labels from a source repository to a destination repository on GitHub.

- **Subcommand argv path**: `label clone`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--force, -f` | `force` | `BOOLEAN` | Overwrite labels in the destination repository |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `labelCreate`
Create a new label on GitHub, or update an existing one with `--force`.

- **Subcommand argv path**: `label create`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--color, -c` | `color` | `BOOLEAN` | string         Color of the label |
| `--description, -d` | `description` | `BOOLEAN` | string   Description of the label |
| `--force, -f` | `force` | `BOOLEAN` | Update the label color and description if label already exists |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `labelDelete`
Delete a label from a repository

- **Subcommand argv path**: `label delete`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--yes` | `yes` | `BOOLEAN` | Confirm deletion without prompting |

### `labelEdit`
Update a label on GitHub.

- **Subcommand argv path**: `label edit`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--color, -c` | `color` | `BOOLEAN` | string         Color of the label |
| `--description, -d` | `description` | `BOOLEAN` | string   Description of the label |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--name, -n` | `name` | `BOOLEAN` | string          New name of the label |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `labelList`
Display labels in a GitHub repository.

- **Subcommand argv path**: `label list`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields       Output JSON with the specified fields |
| `--limit, -L` | `limit` | `BOOLEAN` | int         Maximum number of labels to fetch (default 30) |
| `--order` | `order` | `BOOLEAN` | string      Order of labels returned: {asc|desc} (default "asc") |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--search, -S` | `search` | `BOOLEAN` | string     Search label names and descriptions |
| `--sort` | `sort` | `BOOLEAN` | string       Sort fetched labels: {created|name} (default "created") |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |
| `--web, -w` | `web` | `BOOLEAN` | List labels in the web browser |

### `licenses`
View license information for third-party libraries used in this build of the GitHub CLI.

- **Subcommand argv path**: `licenses`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `org`
Work with GitHub organizations.

- **Subcommand argv path**: `org`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `pr`
Work with GitHub pull requests.

- **Subcommand argv path**: `pr`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `preview`
Preview commands are for testing, demonstrative, and development purposes only.

- **Subcommand argv path**: `preview`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `previewPrompter`
Execute a test program to preview the prompter.

- **Subcommand argv path**: `preview prompter`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `project`
Work with GitHub Projects.

- **Subcommand argv path**: `project`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `projectClose`
Close a project

- **Subcommand argv path**: `project close`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--format` | `format` | `BOOLEAN` | string     Output format: {json} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--owner` | `owner` | `BOOLEAN` | string      Login of the owner. Use "@me" for the current user. |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |
| `--undo` | `undo` | `BOOLEAN` | Reopen a closed project |

### `projectCopy`
Copy a project

- **Subcommand argv path**: `project copy`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--drafts` | `drafts` | `BOOLEAN` | Include draft issues when copying |
| `--format` | `format` | `BOOLEAN` | string         Output format: {json} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression         Filter JSON output using a jq expression |
| `--source-owner` | `sourceOwner` | `BOOLEAN` | string   Login of the source owner. Use "@me" for the current user. |
| `--target-owner` | `targetOwner` | `BOOLEAN` | string   Login of the target owner. Use "@me" for the current user. |
| `--template, -t` | `template` | `BOOLEAN` | string       Format JSON output using a Go template; see "gh help formatting" |
| `--title` | `title` | `BOOLEAN` | string          Title for the new project |

### `projectCreate`
Create a project

- **Subcommand argv path**: `project create`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--format` | `format` | `BOOLEAN` | string     Output format: {json} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--owner` | `owner` | `BOOLEAN` | string      Login of the owner. Use "@me" for the current user. |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |
| `--title` | `title` | `BOOLEAN` | string      Title for the project |

### `projectDelete`
Delete a project

- **Subcommand argv path**: `project delete`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--format` | `format` | `BOOLEAN` | string     Output format: {json} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--owner` | `owner` | `BOOLEAN` | string      Login of the owner. Use "@me" for the current user. |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |

### `projectEdit`
Edit a project

- **Subcommand argv path**: `project edit`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--description, -d` | `description` | `BOOLEAN` | string   New description of the project |
| `--format` | `format` | `BOOLEAN` | string        Output format: {json} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression        Filter JSON output using a jq expression |
| `--owner` | `owner` | `BOOLEAN` | string         Login of the owner. Use "@me" for the current user. |
| `--readme` | `readme` | `BOOLEAN` | string        New readme for the project |
| `--template, -t` | `template` | `BOOLEAN` | string      Format JSON output using a Go template; see "gh help formatting" |
| `--title` | `title` | `BOOLEAN` | string         New title for the project |
| `--visibility` | `visibility` | `BOOLEAN` | string    Change project visibility: {PUBLIC|PRIVATE} |

### `projectFieldCreate`
Create a field in a project

- **Subcommand argv path**: `project field-create`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--data-type` | `dataType` | `BOOLEAN` | string                DataType of the new field.: {TEXT|SINGLE_SELECT|DATE|NUMBER} |
| `--format` | `format` | `BOOLEAN` | string                   Output format: {json} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression                   Filter JSON output using a jq expression |
| `--name` | `name` | `BOOLEAN` | string                     Name of the new field |
| `--owner` | `owner` | `BOOLEAN` | string                    Login of the owner. Use "@me" for the current user. |
| `--single-select-options` | `singleSelectOptions` | `BOOLEAN` | strings   Options for SINGLE_SELECT data type |
| `--template, -t` | `template` | `BOOLEAN` | string                 Format JSON output using a Go template; see "gh help formatting" |

### `projectFieldDelete`
Delete a field in a project

- **Subcommand argv path**: `project field-delete`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--format` | `format` | `BOOLEAN` | string     Output format: {json} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--id` | `id` | `BOOLEAN` | string         ID of the field to delete |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |

### `projectFieldList`
List the fields in a project

- **Subcommand argv path**: `project field-list`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--format` | `format` | `BOOLEAN` | string     Output format: {json} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--limit, -L` | `limit` | `BOOLEAN` | int         Maximum number of fields to fetch (default 30) |
| `--owner` | `owner` | `BOOLEAN` | string      Login of the owner. Use "@me" for the current user. |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |

### `projectItemAdd`
Add a pull request or an issue to a project

- **Subcommand argv path**: `project item-add`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--format` | `format` | `BOOLEAN` | string     Output format: {json} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--owner` | `owner` | `BOOLEAN` | string      Login of the owner. Use "@me" for the current user. |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |
| `--url` | `url` | `BOOLEAN` | string        URL of the issue or pull request to add to the project |

### `projectItemArchive`
Archive an item in a project

- **Subcommand argv path**: `project item-archive`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--format` | `format` | `BOOLEAN` | string     Output format: {json} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--id` | `id` | `BOOLEAN` | string         ID of the item to archive |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--owner` | `owner` | `BOOLEAN` | string      Login of the owner. Use "@me" for the current user. |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |
| `--undo` | `undo` | `BOOLEAN` | Unarchive an item |

### `projectItemCreate`
Create a draft issue item in a project

- **Subcommand argv path**: `project item-create`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--body` | `body` | `BOOLEAN` | string       Body for the draft issue |
| `--format` | `format` | `BOOLEAN` | string     Output format: {json} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--owner` | `owner` | `BOOLEAN` | string      Login of the owner. Use "@me" for the current user. |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |
| `--title` | `title` | `BOOLEAN` | string      Title for the draft issue |

### `projectItemDelete`
Delete an item from a project by ID

- **Subcommand argv path**: `project item-delete`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--format` | `format` | `BOOLEAN` | string     Output format: {json} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--id` | `id` | `BOOLEAN` | string         ID of the item to delete |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--owner` | `owner` | `BOOLEAN` | string      Login of the owner. Use "@me" for the current user. |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |

### `projectItemEdit`
Edit a draft issue or a project item.

- **Subcommand argv path**: `project item-edit`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--body` | `body` | `BOOLEAN` | string                      Body of the draft issue item |
| `--clear` | `clear` | `BOOLEAN` | Remove field value |
| `--date` | `date` | `BOOLEAN` | string                      Date value for the field (YYYY-MM-DD) |
| `--field` | `field` | `BOOLEAN` | string                     Name of the field to update |
| `--field-id` | `fieldId` | `BOOLEAN` | string                  ID of the field to update |
| `--format` | `format` | `BOOLEAN` | string                    Output format: {json} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--id` | `id` | `BOOLEAN` | string                        ID of the item to edit |
| `--iteration-id` | `iterationId` | `BOOLEAN` | string              ID of the iteration value to set on the field |
| `--jq, -q` | `jq` | `BOOLEAN` | expression                    Filter JSON output using a jq expression |
| `--number` | `number` | `BOOLEAN` | float                     Number value for the field |
| `--owner` | `owner` | `BOOLEAN` | string                     Login of the owner. Use "@me" for the current user. |
| `--project-id` | `projectId` | `BOOLEAN` | string                ID of the project to which the field belongs to |
| `--single-select-option-id` | `singleSelectOptionId` | `BOOLEAN` | string   ID of the single select option value to set on the field |
| `--template, -t` | `template` | `BOOLEAN` | string                  Format JSON output using a Go template; see "gh help formatting" |
| `--text` | `text` | `BOOLEAN` | string                      Text value for the field |
| `--title` | `title` | `BOOLEAN` | string                     Title of the draft issue item |
| `--url` | `url` | `BOOLEAN` | string                       URL of the issue or pull request whose project item to edit |
| `--value` | `value` | `BOOLEAN` | Value to set on the field named by --field |

### `projectItemList`
List the items in a project.

- **Subcommand argv path**: `project item-list`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--field` | `field` | `BOOLEAN` | stringArray      Name of a field to show as an extra column |
| `--field-id` | `fieldId` | `BOOLEAN` | stringArray   ID of a field to show as an extra column |
| `--format` | `format` | `BOOLEAN` | string          Output format: {json} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression          Filter JSON output using a jq expression |
| `--limit, -L` | `limit` | `BOOLEAN` | int              Maximum number of items to fetch (default 30) |
| `--owner` | `owner` | `BOOLEAN` | string           Login of the owner. Use "@me" for the current user |
| `--query` | `query` | `BOOLEAN` | string           Filter items using the Projects filter syntax, e.g. "assignee:octocat -status:Done" |
| `--template, -t` | `template` | `BOOLEAN` | string        Format JSON output using a Go template; see "gh help formatting" |

### `projectLink`
Link a project to a repository or a team

- **Subcommand argv path**: `project link`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--owner` | `owner` | `BOOLEAN` | string   Login of the owner. Use "@me" for the current user. |
| `--repo, -R` | `repo` | `BOOLEAN` | string    The repository to be linked to this project |
| `--team, -T` | `team` | `BOOLEAN` | string    The team to be linked to this project |

### `projectList`
List the projects for an owner

- **Subcommand argv path**: `project list`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--closed` | `closed` | `BOOLEAN` | Include closed projects |
| `--format` | `format` | `BOOLEAN` | string     Output format: {json} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--limit, -L` | `limit` | `BOOLEAN` | int         Maximum number of projects to fetch (default 30) |
| `--owner` | `owner` | `BOOLEAN` | string      Login of the owner |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |
| `--web, -w` | `web` | `BOOLEAN` | Open projects list in the browser |

### `projectMarkTemplate`
Mark a project as a template

- **Subcommand argv path**: `project mark-template`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--format` | `format` | `BOOLEAN` | string     Output format: {json} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--owner` | `owner` | `BOOLEAN` | string      Login of the org owner. |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |
| `--undo` | `undo` | `BOOLEAN` | Unmark the project as a template. |

### `projectUnlink`
Unlink a project from a repository or a team

- **Subcommand argv path**: `project unlink`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--owner` | `owner` | `BOOLEAN` | string   Login of the owner. Use "@me" for the current user. |
| `--repo, -R` | `repo` | `BOOLEAN` | string    The repository to be unlinked from this project |
| `--team, -T` | `team` | `BOOLEAN` | string    The team to be unlinked from this project |

### `projectView`
View a project

- **Subcommand argv path**: `project view`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--format` | `format` | `BOOLEAN` | string     Output format: {json} |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--owner` | `owner` | `BOOLEAN` | string      Login of the owner. Use "@me" for the current user. |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |
| `--web, -w` | `web` | `BOOLEAN` | Open a project in the browser |

### `release`
Manage releases

- **Subcommand argv path**: `release`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `repo`
Work with GitHub repositories.

- **Subcommand argv path**: `repo`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `ruleset`
Repository rulesets are a way to define a set of rules that apply to a repository.

- **Subcommand argv path**: `ruleset`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `rulesetCheck`
View information about GitHub rules that apply to a given branch.

- **Subcommand argv path**: `ruleset check`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--default` | `default` | `BOOLEAN` | Check rules on default branch |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--web, -w` | `web` | `BOOLEAN` | Open the branch rules page in a web browser |

### `rulesetList`
List GitHub rulesets for a repository or organization.

- **Subcommand argv path**: `ruleset list`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--limit, -L` | `limit` | `BOOLEAN` | int    Maximum number of rulesets to list (default 30) |
| `--org, -o` | `org` | `BOOLEAN` | string   List organization-wide rulesets for the provided organization |
| `--parents, -p` | `parents` | `BOOLEAN` | Whether to include rulesets configured at higher levels that also apply (default true) |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--web, -w` | `web` | `BOOLEAN` | Open the list of rulesets in the web browser |

### `rulesetView`
View information about a GitHub ruleset.

- **Subcommand argv path**: `ruleset view`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--org, -o` | `org` | `BOOLEAN` | string   Organization name if the provided ID is an organization-level ruleset |
| `--parents, -p` | `parents` | `BOOLEAN` | Whether to include rulesets configured at higher levels that also apply (default true) |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--web, -w` | `web` | `BOOLEAN` | Open the ruleset in the browser |

### `run`
List, view, and watch recent workflow runs from GitHub Actions.

- **Subcommand argv path**: `run`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `runCancel`
Cancel a workflow run

- **Subcommand argv path**: `run cancel`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--force` | `force` | `BOOLEAN` | Force cancel a workflow run |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `runDelete`
Delete a workflow run

- **Subcommand argv path**: `run delete`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `runDownload`
Download artifacts generated by a GitHub Actions workflow run.

- **Subcommand argv path**: `run download`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--dir, -D` | `dir` | `BOOLEAN` | string            The directory to download artifacts into (default ".") |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--name, -n` | `name` | `BOOLEAN` | stringArray      Download artifacts that match any of the given names |
| `--pattern, -p` | `pattern` | `BOOLEAN` | stringArray   Download artifacts that match a glob pattern |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `runList`
List recent workflow runs.

- **Subcommand argv path**: `run list`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--all, -a` | `all` | `BOOLEAN` | Include disabled workflows |
| `--branch, -b` | `branch` | `BOOLEAN` | string     Filter runs by branch |
| `--commit, -c` | `commit` | `STRING` | Filter runs by the SHA of the commit |
| `--created` | `created` | `BOOLEAN` | date      Filter runs by the date it was created |
| `--event, -e` | `event` | `BOOLEAN` | event       Filter runs by which event triggered the run |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields       Output JSON with the specified fields |
| `--limit, -L` | `limit` | `BOOLEAN` | int         Maximum number of runs to fetch (default 20) |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--status, -s` | `status` | `BOOLEAN` | string     Filter runs by status: {queued|completed|in_progress|requested|waiting|pending|action_required|cancelled|failure|neutral|skipped|stale|startup_failure|success|timed_out} |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |
| `--user, -u` | `user` | `BOOLEAN` | string       Filter runs by user who triggered the run |
| `--workflow, -w` | `workflow` | `BOOLEAN` | string   Filter runs by workflow |

### `runRerun`
Rerun an entire run, only failed jobs, or a specific job from a run.

- **Subcommand argv path**: `run rerun`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--debug, -d` | `debug` | `BOOLEAN` | Rerun with debug logging |
| `--failed` | `failed` | `BOOLEAN` | Rerun only failed jobs, including dependencies |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--job, -j` | `job` | `BOOLEAN` | string   Rerun a specific job ID from a run, including dependencies |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `runView`
View a summary of a workflow run.

- **Subcommand argv path**: `run view`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--attempt, -a` | `attempt` | `BOOLEAN` | uint      The attempt number of the workflow run |
| `--exit-status` | `exitStatus` | `BOOLEAN` | Exit with non-zero status if run failed |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--job, -j` | `job` | `BOOLEAN` | string        View a specific job ID from a run |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields       Output JSON with the specified fields |
| `--log` | `log` | `BOOLEAN` | View full log for either a run or specific job |
| `--log-failed` | `logFailed` | `BOOLEAN` | View the log for any failed steps in a run or specific job |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |
| `--verbose, -v` | `verbose` | `BOOLEAN` | Show job steps |
| `--web, -w` | `web` | `BOOLEAN` | Open run in the browser |

### `runWatch`
Watch a run until it completes, showing its progress.

- **Subcommand argv path**: `run watch`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--compact` | `compact` | `BOOLEAN` | Show only relevant/failed steps |
| `--exit-status` | `exitStatus` | `BOOLEAN` | Exit with non-zero status if run fails |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--interval, -i` | `interval` | `BOOLEAN` | int   Refresh interval in seconds (default 3) |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `search`
Search across all of GitHub.

- **Subcommand argv path**: `search`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `searchCode`
Search within code in GitHub repositories.

- **Subcommand argv path**: `search code`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--extension` | `extension` | `BOOLEAN` | string   Filter on file extension |
| `--filename` | `filename` | `BOOLEAN` | string    Filter on filename |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression      Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields        Output JSON with the specified fields |
| `--language` | `language` | `BOOLEAN` | string    Filter results by language |
| `--limit, -L` | `limit` | `BOOLEAN` | int          Maximum number of code results to fetch (default 30) |
| `--match` | `match` | `BOOLEAN` | strings      Restrict search to file contents or file path: {file|path} |
| `--owner` | `owner` | `BOOLEAN` | strings      Filter on owner |
| `--repo, -R` | `repo` | `BOOLEAN` | OWNER/REPO    Filter on repository, in OWNER/REPO format |
| `--size` | `size` | `BOOLEAN` | string        Filter on size range, in kilobytes |
| `--template, -t` | `template` | `BOOLEAN` | string    Format JSON output using a Go template; see "gh help formatting" |
| `--web, -w` | `web` | `BOOLEAN` | Open the search query in the web browser |

### `searchCommits`
Search for commits on GitHub.

- **Subcommand argv path**: `search commits`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--author` | `author` | `BOOLEAN` | string            Filter by author |
| `--author-date` | `authorDate` | `BOOLEAN` | date         Filter based on authored date |
| `--author-email` | `authorEmail` | `BOOLEAN` | string      Filter on author email |
| `--author-name` | `authorName` | `BOOLEAN` | string       Filter on author name |
| `--committer` | `committer` | `BOOLEAN` | string         Filter by committer |
| `--committer-date` | `committerDate` | `BOOLEAN` | date      Filter based on committed date |
| `--committer-email` | `committerEmail` | `BOOLEAN` | string   Filter on committer email |
| `--committer-name` | `committerName` | `BOOLEAN` | string    Filter on committer name |
| `--hash` | `hash` | `BOOLEAN` | string              Filter by commit hash |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression            Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields              Output JSON with the specified fields |
| `--limit, -L` | `limit` | `BOOLEAN` | int                Maximum number of commits to fetch (default 30) |
| `--merge` | `merge` | `BOOLEAN` | Filter on merge commits |
| `--order` | `order` | `BOOLEAN` | string             Order of commits returned, ignored unless '--sort' flag is specified: {asc|desc} (default "desc") |
| `--owner` | `owner` | `BOOLEAN` | strings            Filter on repository owner |
| `--parent` | `parent` | `BOOLEAN` | string            Filter by parent hash |
| `--repo, -R` | `repo` | `BOOLEAN` | OWNER/REPO          Filter on repository, in OWNER/REPO format |
| `--sort` | `sort` | `BOOLEAN` | string              Sort fetched commits: {author-date|committer-date} (default "best-match") |
| `--template, -t` | `template` | `BOOLEAN` | string          Format JSON output using a Go template; see "gh help formatting" |
| `--tree` | `tree` | `BOOLEAN` | string              Filter by tree hash |
| `--visibility` | `visibility` | `BOOLEAN` | strings       Filter based on repository visibility: {public|private|internal} |
| `--web, -w` | `web` | `BOOLEAN` | Open the search query in the web browser |

### `searchIssues`
Search for issues on GitHub.

- **Subcommand argv path**: `search issues`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--app` | `app` | `BOOLEAN` | string             Filter by GitHub App author |
| `--archived` | `archived` | `BOOLEAN` | Filter based on the repository archived state {true|false} |
| `--assignee` | `assignee` | `BOOLEAN` | string        Filter by assignee |
| `--author` | `author` | `BOOLEAN` | string          Filter by author (use --app to filter by a GitHub App) |
| `--closed` | `closed` | `BOOLEAN` | date            Filter on closed at date |
| `--commenter` | `commenter` | `BOOLEAN` | user         Filter based on comments by user |
| `--comments` | `comments` | `BOOLEAN` | number        Filter on number of comments |
| `--created` | `created` | `BOOLEAN` | date           Filter based on created at date |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--include-prs` | `includePrs` | `BOOLEAN` | Include pull requests in results |
| `--interactions` | `interactions` | `BOOLEAN` | number    Filter on number of reactions and comments |
| `--involves` | `involves` | `BOOLEAN` | user          Filter based on involvement of user |
| `--jq, -q` | `jq` | `BOOLEAN` | expression          Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields            Output JSON with the specified fields |
| `--label` | `label` | `BOOLEAN` | strings          Filter on label |
| `--language` | `language` | `BOOLEAN` | string        Filter based on the coding language |
| `--limit, -L` | `limit` | `BOOLEAN` | int              Maximum number of results to fetch (default 30) |
| `--locked` | `locked` | `BOOLEAN` | Filter on locked conversation status |
| `--match` | `match` | `BOOLEAN` | strings          Restrict search to specific field of issue: {title|body|comments} |
| `--mentions` | `mentions` | `BOOLEAN` | user          Filter based on user mentions |
| `--milestone` | `milestone` | `BOOLEAN` | title        Filter by milestone title |
| `--no-assignee` | `noAssignee` | `BOOLEAN` | Filter on missing assignee |
| `--no-label` | `noLabel` | `BOOLEAN` | Filter on missing label |
| `--no-milestone` | `noMilestone` | `BOOLEAN` | Filter on missing milestone |
| `--no-project` | `noProject` | `BOOLEAN` | Filter on missing project |
| `--order` | `order` | `BOOLEAN` | string           Order of results returned, ignored unless '--sort' flag is specified: {asc|desc} (default "desc") |
| `--owner` | `owner` | `BOOLEAN` | strings          Filter on repository owner |
| `--project` | `project` | `BOOLEAN` | owner/number   Filter on project board owner/number |
| `--reactions` | `reactions` | `BOOLEAN` | number       Filter on number of reactions |
| `--repo, -R` | `repo` | `BOOLEAN` | OWNER/REPO        Filter on repository, in OWNER/REPO format |
| `--sort` | `sort` | `BOOLEAN` | string            Sort fetched results: {comments|created|interactions|reactions|reactions-+1|reactions--1|reactions-heart|reactions-smile|reactions-tada|reactions-thinking_face|updated} (default "best-match") |
| `--state` | `state` | `BOOLEAN` | string           Filter based on state: {open|closed} |
| `--team-mentions` | `teamMentions` | `BOOLEAN` | string   Filter based on team mentions |
| `--template, -t` | `template` | `BOOLEAN` | string        Format JSON output using a Go template; see "gh help formatting" |
| `--updated` | `updated` | `BOOLEAN` | date           Filter on last updated at date |
| `--visibility` | `visibility` | `BOOLEAN` | strings     Filter based on repository visibility: {public|private|internal} |
| `--web, -w` | `web` | `BOOLEAN` | Open the search query in the web browser |

### `searchPrs`
Search for pull requests on GitHub.

- **Subcommand argv path**: `search prs`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--app` | `app` | `BOOLEAN` | string              Filter by GitHub App author |
| `--archived` | `archived` | `BOOLEAN` | Filter based on the repository archived state {true|false} |
| `--assignee` | `assignee` | `BOOLEAN` | string         Filter by assignee |
| `--author` | `author` | `BOOLEAN` | string           Filter by author (use --app to filter by a GitHub App) |
| `--base, -B` | `base` | `BOOLEAN` | string             Filter on base branch name |
| `--checks` | `checks` | `BOOLEAN` | string           Filter based on status of the checks: {pending|success|failure} |
| `--closed` | `closed` | `BOOLEAN` | date             Filter on closed at date |
| `--commenter` | `commenter` | `BOOLEAN` | user          Filter based on comments by user |
| `--comments` | `comments` | `BOOLEAN` | number         Filter on number of comments |
| `--created` | `created` | `BOOLEAN` | date            Filter based on created at date |
| `--draft` | `draft` | `BOOLEAN` | Filter based on draft state |
| `--head, -H` | `head` | `BOOLEAN` | string             Filter on head branch name |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--interactions` | `interactions` | `BOOLEAN` | number     Filter on number of reactions and comments |
| `--involves` | `involves` | `BOOLEAN` | user           Filter based on involvement of user |
| `--jq, -q` | `jq` | `BOOLEAN` | expression           Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields             Output JSON with the specified fields |
| `--label` | `label` | `BOOLEAN` | strings           Filter on label |
| `--language` | `language` | `BOOLEAN` | string         Filter based on the coding language |
| `--limit, -L` | `limit` | `BOOLEAN` | int               Maximum number of results to fetch (default 30) |
| `--locked` | `locked` | `BOOLEAN` | Filter on locked conversation status |
| `--match` | `match` | `BOOLEAN` | strings           Restrict search to specific field of issue: {title|body|comments} |
| `--mentions` | `mentions` | `BOOLEAN` | user           Filter based on user mentions |
| `--merged` | `merged` | `BOOLEAN` | Filter based on merged state |
| `--merged-at` | `mergedAt` | `BOOLEAN` | date          Filter on merged at date |
| `--milestone` | `milestone` | `BOOLEAN` | title         Filter by milestone title |
| `--no-assignee` | `noAssignee` | `BOOLEAN` | Filter on missing assignee |
| `--no-label` | `noLabel` | `BOOLEAN` | Filter on missing label |
| `--no-milestone` | `noMilestone` | `BOOLEAN` | Filter on missing milestone |
| `--no-project` | `noProject` | `BOOLEAN` | Filter on missing project |
| `--order` | `order` | `BOOLEAN` | string            Order of results returned, ignored unless '--sort' flag is specified: {asc|desc} (default "desc") |
| `--owner` | `owner` | `BOOLEAN` | strings           Filter on repository owner |
| `--project` | `project` | `BOOLEAN` | owner/number    Filter on project board owner/number |
| `--reactions` | `reactions` | `BOOLEAN` | number        Filter on number of reactions |
| `--repo, -R` | `repo` | `BOOLEAN` | OWNER/REPO         Filter on repository, in OWNER/REPO format |
| `--review` | `review` | `BOOLEAN` | string           Filter based on review status: {none|required|approved|changes_requested} |
| `--review-requested` | `reviewRequested` | `BOOLEAN` | user   Filter on user or team requested to review |
| `--reviewed-by` | `reviewedBy` | `BOOLEAN` | user        Filter on user who reviewed |
| `--sort` | `sort` | `BOOLEAN` | string             Sort fetched results: {comments|reactions|reactions-+1|reactions--1|reactions-smile|reactions-thinking_face|reactions-heart|reactions-tada|interactions|created|updated} (default "best-match") |
| `--state` | `state` | `BOOLEAN` | string            Filter based on state: {open|closed} |
| `--team-mentions` | `teamMentions` | `BOOLEAN` | string    Filter based on team mentions |
| `--template, -t` | `template` | `BOOLEAN` | string         Format JSON output using a Go template; see "gh help formatting" |
| `--updated` | `updated` | `BOOLEAN` | date            Filter on last updated at date |
| `--visibility` | `visibility` | `BOOLEAN` | strings      Filter based on repository visibility: {public|private|internal} |
| `--web, -w` | `web` | `BOOLEAN` | Open the search query in the web browser |

### `searchRepos`
Search for repositories on GitHub.

- **Subcommand argv path**: `search repos`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--archived` | `archived` | `BOOLEAN` | Filter based on the repository archived state {true|false} |
| `--created` | `created` | `BOOLEAN` | date                Filter based on created at date |
| `--followers` | `followers` | `BOOLEAN` | number            Filter based on number of followers |
| `--forks` | `forks` | `BOOLEAN` | number                Filter on number of forks |
| `--good-first-issues` | `goodFirstIssues` | `BOOLEAN` | number    Filter on number of issues with the 'good first issue' label |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--help-wanted-issues` | `helpWantedIssues` | `BOOLEAN` | number   Filter on number of issues with the 'help wanted' label |
| `--include-forks` | `includeForks` | `BOOLEAN` | string        Include forks in fetched repositories: {false|true|only} |
| `--jq, -q` | `jq` | `BOOLEAN` | expression               Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields                 Output JSON with the specified fields |
| `--language` | `language` | `BOOLEAN` | string             Filter based on the coding language |
| `--license` | `license` | `BOOLEAN` | strings             Filter based on license type |
| `--limit, -L` | `limit` | `BOOLEAN` | int                   Maximum number of repositories to fetch (default 30) |
| `--match` | `match` | `BOOLEAN` | strings               Restrict search to specific field of repository: {name|description|readme} |
| `--number-topics` | `numberTopics` | `BOOLEAN` | number        Filter on number of topics |
| `--order` | `order` | `BOOLEAN` | string                Order of repositories returned, ignored unless '--sort' flag is specified: {asc|desc} (default "desc") |
| `--owner` | `owner` | `BOOLEAN` | strings               Filter on owner |
| `--size` | `size` | `BOOLEAN` | string                 Filter on a size range, in kilobytes |
| `--sort` | `sort` | `BOOLEAN` | string                 Sort fetched repositories: {forks|help-wanted-issues|stars|updated} (default "best-match") |
| `--stars` | `stars` | `BOOLEAN` | number                Filter on number of stars |
| `--template, -t` | `template` | `BOOLEAN` | string             Format JSON output using a Go template; see "gh help formatting" |
| `--topic` | `topic` | `BOOLEAN` | strings               Filter on topic |
| `--updated` | `updated` | `BOOLEAN` | date                Filter on last updated at date |
| `--visibility` | `visibility` | `BOOLEAN` | strings          Filter based on visibility: {public|private|internal} |
| `--web, -w` | `web` | `BOOLEAN` | Open the search query in the web browser |

### `secret`
Secrets can be set at the repository, or organization level for use in

- **Subcommand argv path**: `secret`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `secretDelete`
Delete a secret on one of the following levels:

- **Subcommand argv path**: `secret delete`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--app, -a` | `app` | `BOOLEAN` | string   Delete a secret for a specific application: {actions|agents|codespaces|dependabot} |
| `--env, -e` | `env` | `BOOLEAN` | string   Delete a secret for an environment |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--org, -o` | `org` | `BOOLEAN` | string   Delete a secret for an organization |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--user, -u` | `user` | `BOOLEAN` | Delete a secret for your user |

### `secretList`
List secrets on one of the following levels:

- **Subcommand argv path**: `secret list`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--app, -a` | `app` | `BOOLEAN` | string        List secrets for a specific application: {actions|agents|codespaces|dependabot} |
| `--env, -e` | `env` | `BOOLEAN` | string        List secrets for an environment |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields       Output JSON with the specified fields |
| `--org, -o` | `org` | `BOOLEAN` | string        List secrets for an organization |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |
| `--user, -u` | `user` | `BOOLEAN` | List a secret for your user |

### `secretSet`
Set a value for a secret on one of the following levels:

- **Subcommand argv path**: `secret set`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--app, -a` | `app` | `BOOLEAN` | string           Set the application for a secret: {actions|agents|codespaces|dependabot} |
| `--body, -b` | `body` | `BOOLEAN` | string          The value for the secret (reads from standard input if not specified) |
| `--env, -e` | `env` | `BOOLEAN` | environment      Set deployment environment secret |
| `--env-file, -f` | `envFile` | `BOOLEAN` | file        Load secret names and values from a dotenv-formatted file |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--no-repos-selected` | `noReposSelected` | `BOOLEAN` | No repositories can access the organization secret |
| `--no-store` | `noStore` | `BOOLEAN` | Print the encrypted, base64-encoded value instead of storing it on GitHub |
| `--org, -o` | `org` | `BOOLEAN` | organization     Set organization secret |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--repos, -r` | `repos` | `BOOLEAN` | repositories   List of repositories that can access an organization or user secret |
| `--user, -u` | `user` | `BOOLEAN` | Set a secret for your user |
| `--visibility, -v` | `visibility` | `BOOLEAN` | string    Set visibility for an organization secret: {all|private|selected} (default "private") |

### `skill`
Install and manage agent skills from GitHub repositories.

- **Subcommand argv path**: `skill`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `skillInstall`
Install agent skills from a GitHub repository or local directory into

- **Subcommand argv path**: `skill install`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--agent` | `agent` | `BOOLEAN` | string        Target agent (see supported values above) |
| `--all` | `all` | `BOOLEAN` | Install all skills without prompting for skill selection |
| `--allow-hidden-dirs` | `allowHiddenDirs` | `BOOLEAN` | Include skills in hidden directories (e.g. .claude/skills/, .agents/skills/) |
| `--dir` | `dir` | `BOOLEAN` | string          Install to a custom directory (overrides --agent and --scope) |
| `--force, -f` | `force` | `BOOLEAN` | Overwrite existing skills without prompting |
| `--from-local` | `fromLocal` | `BOOLEAN` | Treat the argument as a local directory path instead of a repository |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--pin` | `pin` | `BOOLEAN` | string          Pin to a specific git tag or commit SHA |
| `--scope` | `scope` | `BOOLEAN` | string        Installation scope: {project|user} (default "project") |
| `--upstream` | `upstream` | `BOOLEAN` | Install from the upstream source when a re-published skill is detected |

### `skillList`
List installed agent skills across known agent host directories.

- **Subcommand argv path**: `skill list`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--agent` | `agent` | `BOOLEAN` | string      Filter by target agent: {github-copilot|claude-code|cursor|codex|gemini-cli|antigravity|antigravity-cli|antigravity2.0|adal|amp|augment|bob|cline|codebuddy|command-code|continue|cortex|crush|deepagents|devin|droid|firebender|goose|grok|iflow-cli|junie|kilo|kimi-cli|kiro-cli|kode|mcpjam|mistral-vibe|mux|neovate|openclaw|opencode|openhands|pi|pochi|qoder|qwen-code|replit|roo|trae|trae-cn|universal|warp|zencoder} |
| `--dir` | `dir` | `BOOLEAN` | string        Scan a custom directory for installed skills |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields       Output JSON with the specified fields |
| `--scope` | `scope` | `BOOLEAN` | string      Filter by installation scope: {project|user} |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |

### `skillPreview`
Render a skill's `SKILL.md` content in the terminal. This fetches the

- **Subcommand argv path**: `skill preview`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--allow-hidden-dirs` | `allowHiddenDirs` | `BOOLEAN` | Include skills in hidden directories (e.g. .claude/skills/, .agents/skills/) |
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `skillPublish`
Validate a local repository's skills against the Agent Skills specification

- **Subcommand argv path**: `skill publish`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--dry-run` | `dryRun` | `BOOLEAN` | Validate without publishing |
| `--fix` | `fix` | `BOOLEAN` | Auto-fix issues where possible without publishing (e.g. strip install metadata) |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--tag` | `tag` | `BOOLEAN` | string   Version tag for the release (e.g. v1.0.0) |

### `skillSearch`
Search across all public GitHub repositories for skills matching a keyword.

- **Subcommand argv path**: `skill search`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields       Output JSON with the specified fields |
| `--limit, -L` | `limit` | `BOOLEAN` | int         Maximum number of results per page (default 15) |
| `--owner` | `owner` | `BOOLEAN` | string      Filter results to a specific GitHub user or organization |
| `--page` | `page` | `BOOLEAN` | int          Page number of results to fetch (default 1) |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |

### `skillUpdate`
Checks installed skills for available updates by comparing the local

- **Subcommand argv path**: `skill update`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--all` | `all` | `BOOLEAN` | Update all skills without prompting |
| `--dir` | `dir` | `BOOLEAN` | string   Scan a custom directory for installed skills |
| `--dry-run` | `dryRun` | `BOOLEAN` | Report available updates without modifying files |
| `--force` | `force` | `BOOLEAN` | Re-download even if already up to date |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--unpin` | `unpin` | `BOOLEAN` | Clear pinned version and include pinned skills in update |

### `sshKey`
Manage SSH keys registered with your GitHub account.

- **Subcommand argv path**: `ssh-key`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `sshKeyAdd`
Add an SSH key to your GitHub account

- **Subcommand argv path**: `ssh-key add`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--title, -t` | `title` | `BOOLEAN` | string   Title for the new key |
| `--type` | `type` | `BOOLEAN` | string    Type of the ssh key: {authentication|signing} (default "authentication") |

### `sshKeyDelete`
Delete an SSH key from your GitHub account

- **Subcommand argv path**: `ssh-key delete`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--yes, -y` | `yes` | `BOOLEAN` | Skip the confirmation prompt |

### `sshKeyList`
Lists SSH keys in your GitHub account

- **Subcommand argv path**: `ssh-key list`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |

### `status`
The status command prints information about your work on GitHub across all the repositories you're subscribed to, including:

- **Subcommand argv path**: `status`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--exclude, -e` | `exclude` | `BOOLEAN` | strings   Comma separated list of repos to exclude in owner/name format |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--org, -o` | `org` | `BOOLEAN` | string        Report status within an organization |

### `variable`
Variables can be set at the repository, environment or organization level for use in

- **Subcommand argv path**: `variable`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `variableDelete`
Delete a variable on one of the following levels:

- **Subcommand argv path**: `variable delete`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--env, -e` | `env` | `BOOLEAN` | string   Delete a variable for an environment |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--org, -o` | `org` | `BOOLEAN` | string   Delete a variable for an organization |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `variableGet`
Get a variable on one of the following levels:

- **Subcommand argv path**: `variable get`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--env, -e` | `env` | `BOOLEAN` | string        Get a variable for an environment |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields       Output JSON with the specified fields |
| `--org, -o` | `org` | `BOOLEAN` | string        Get a variable for an organization |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |

### `variableList`
List variables on one of the following levels:

- **Subcommand argv path**: `variable list`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--env, -e` | `env` | `BOOLEAN` | string        List variables for an environment |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields       Output JSON with the specified fields |
| `--org, -o` | `org` | `BOOLEAN` | string        List variables for an organization |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |

### `variableSet`
Set a value for a variable on one of the following levels:

- **Subcommand argv path**: `variable set`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--body, -b` | `body` | `BOOLEAN` | string          The value for the variable (reads from standard input if not specified) |
| `--env, -e` | `env` | `BOOLEAN` | environment      Set deployment environment variable |
| `--env-file, -f` | `envFile` | `BOOLEAN` | file        Load variable names and values from a dotenv-formatted file |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--org, -o` | `org` | `BOOLEAN` | organization     Set organization variable |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--repos, -r` | `repos` | `BOOLEAN` | repositories   List of repositories that can access an organization variable |
| `--visibility, -v` | `visibility` | `BOOLEAN` | string    Set visibility for an organization variable: {all|private|selected} (default "private") |

### `workflow`
List, view, and run workflows in GitHub Actions.

- **Subcommand argv path**: `workflow`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `workflowDisable`
Disable a workflow, preventing it from running or showing up when listing workflows.

- **Subcommand argv path**: `workflow disable`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `workflowEnable`
Enable a workflow, allowing it to be run and show up when listing workflows.

- **Subcommand argv path**: `workflow enable`
- **Risk Override**: `HOST_WRITE`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `workflowList`
List workflow files, hiding disabled workflows by default.

- **Subcommand argv path**: `workflow list`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--all, -a` | `all` | `BOOLEAN` | Include disabled workflows |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--jq, -q` | `jq` | `BOOLEAN` | expression     Filter JSON output using a jq expression |
| `--json` | `json` | `BOOLEAN` | fields       Output JSON with the specified fields |
| `--limit, -L` | `limit` | `BOOLEAN` | int         Maximum number of workflows to fetch (default 50) |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--template, -t` | `template` | `BOOLEAN` | string   Format JSON output using a Go template; see "gh help formatting" |

### `workflowRun`
Create a `workflow_dispatch` event for a given workflow.

- **Subcommand argv path**: `workflow run`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--field, -F` | `field` | `BOOLEAN` | key=value       Add a string parameter in key=value format, respecting @ syntax (see "gh help api"). |
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--json` | `json` | `BOOLEAN` | Read workflow inputs as JSON via STDIN |
| `--raw-field, -f` | `rawField` | `BOOLEAN` | key=value   Add a string parameter in key=value format |
| `--ref, -r` | `ref` | `BOOLEAN` | string            Branch or tag name which contains the version of the workflow file you'd like to run |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |

### `workflowView`
View the summary of a workflow

- **Subcommand argv path**: `workflow view`
- **Risk Override**: `READ_ONLY`

#### Command Options
| Option | Property Name | Type | Description |
|---|---|---|---|
| `--help` | `help` | `BOOLEAN` | Show help for command |
| `--ref, -r` | `ref` | `BOOLEAN` | string   The branch or tag name which contains the version of the workflow file you'd like to view |
| `--repo, -R` | `repo` | `BOOLEAN` | [HOST/]OWNER/REPO   Select another repository using the [HOST/]OWNER/REPO format |
| `--web, -w` | `web` | `BOOLEAN` | Open workflow in the browser |
| `--yaml, -y` | `yaml` | `BOOLEAN` | View the workflow yaml file |
