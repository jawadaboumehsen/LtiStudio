package org.ide.lti.desktop.realhost

import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.data.tools.ToolErrorException
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.model.setup.ToolRef
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real-host test verifying reference resolution, annotated tag peeling,
 * branch movement resilience, catalog layout verification, credential rejection,
 * and network retry streaming (T061).
 */
class ToolStoreResolveRealTest {
    private fun hostPath(host: RealToolHost, sub: String = ""): String {
        val h = io.ltirom.tooling.client.WslPathTranslator().toWslPath(host.tempHome.path)
        return if (sub.isBlank()) h else "$h/$sub"
    }

    @Test
    fun annotatedTagIsPeeledAndBranchTagCommitResolvesTo40Hex() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "peel-resolve-repo")
            val script =
                """
                    mkdir -p '$repoPath'
                    cd '$repoPath'
                    git init -b main
                    git config user.name 'Test Runner'
                    git config user.email 'test@example.com'
                    echo 'hello' > README.md
                    git add README.md
                    git commit -m 'Initial commit'
                    git tag -a v1.0.0 -m 'Annotated tag release 1.0.0'
                    git checkout -b feat-test
                    echo 'feature' > feat.txt
                    git add feat.txt
                    git commit -m 'Feature commit'
                    git checkout main
                    echo "TAG_COMMIT=${'$'}(git rev-parse 'v1.0.0^{commit}')"
                    echo "TAG_OBJ=${'$'}(git rev-parse 'refs/tags/v1.0.0')"
                    echo "FEAT_COMMIT=${'$'}(git rev-parse 'refs/heads/feat-test')"
                """.trimIndent()
            val req =
                ToolExecutionRequest(toolId = "sh", arguments = listOf("-c", script), workingDirectory = "/tmp")
            val res = host.transport.execute(req)
            assertTrue(res.exitCode == 0, "Failed to setup repo: ${res.stderr}")

            val tagCommit = res.stdout.lines().first { it.startsWith("TAG_COMMIT=") }.substringAfter("=").trim()
            val tagObj = res.stdout.lines().first { it.startsWith("TAG_OBJ=") }.substringAfter("=").trim()
            val featCommit = res.stdout.lines().first { it.startsWith("FEAT_COMMIT=") }.substringAfter("=").trim()

            assertTrue(tagCommit.matches(Regex("^[a-fA-F0-9]{40}$")))
            assertTrue(tagObj.matches(Regex("^[a-fA-F0-9]{40}$")))
            assertTrue(featCommit.matches(Regex("^[a-fA-F0-9]{40}$")))
            assertTrue(tagCommit != tagObj, "Annotated tag object SHA must differ from commit SHA")

            val group =
                Fixtures.createTestToolGroup(
                    id = "peel-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = tagCommit,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                // 1. Resolve annotated tag -> must peel down to commit SHA
                val tagResolveRes = host.store.resolve(group.id, repoPath, ToolRef.Tag("v1.0.0"))
                assertTrue(tagResolveRes.isSuccess, "Tag resolve failed: ${tagResolveRes.exceptionOrNull()}")
                val tagCheck = tagResolveRes.getOrThrow()
                assertEquals(
                    tagCommit,
                    tagCheck.commit,
                    "Annotated tag must peel to commit SHA, not tag object SHA",
                )

                // 2. Resolve branch -> 40-hex commit
                val branchResolveRes = host.store.resolve(group.id, repoPath, ToolRef.Branch("feat-test"))
                assertTrue(
                    branchResolveRes.isSuccess,
                    "Branch resolve failed: ${branchResolveRes.exceptionOrNull()}",
                )
                assertEquals(featCommit, branchResolveRes.getOrThrow().commit)

                // 3. Resolve full commit -> 40-hex commit
                val commitResolveRes = host.store.resolve(group.id, repoPath, ToolRef.Commit(tagCommit))
                assertTrue(
                    commitResolveRes.isSuccess,
                    "Commit resolve failed: ${commitResolveRes.exceptionOrNull()}",
                )
                assertEquals(tagCommit, commitResolveRes.getOrThrow().commit)

                // 4. refs() -> tag listing contains peeled commit
                val refsRes = host.store.refs(group.id, repoPath)
                assertTrue(refsRes.isSuccess, "refs failed: ${refsRes.exceptionOrNull()}")
                val listing = refsRes.getOrThrow()
                val tagRef = listing.tags.firstOrNull { it.name == "v1.0.0" }
                assertNotNull(tagRef, "v1.0.0 tag must be in listing")
                assertEquals(tagCommit, tagRef.commit, "Tag in refs listing must hold peeled commit SHA")

                val branchRef = listing.branches.firstOrNull { it.name == "feat-test" }
                assertNotNull(branchRef, "feat-test branch must be in listing")
                assertEquals(featCommit, branchRef.commit)
            }
        }
    }

    @Test
    fun afterFixtureBranchMovesEarlierResolvedCommitIsBuilt() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "branch-move-repo")
            val setupRes =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "branch-move-tool",
                    probeExitCode = 0,
                    tag = "v1.0.0",
                )
            assertTrue(setupRes.isSuccess, "Fixture setup failed: ${setupRes.exceptionOrNull()?.message}")
            val initialCommit = setupRes.getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "branch-move-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Branch("main"),
                    commit = initialCommit,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                // Resolve branch initially -> initialCommit
                val resolveRes = host.store.resolve(group.id, repoPath, ToolRef.Branch("main"))
                assertTrue(resolveRes.isSuccess)
                assertEquals(initialCommit, resolveRes.getOrThrow().commit)

                // Move fixture branch forward with a second commit
                val advanceScript =
                    """
                        cd '$repoPath'
                        echo '# second commit comment' >> CMakeLists.txt
                        git commit -am 'Second commit on main'
                        git rev-parse HEAD
                    """.trimIndent()
                val advRes =
                    host.transport.execute(
                        ToolExecutionRequest(
                            toolId = "sh",
                            arguments = listOf("-c", advanceScript),
                            workingDirectory = "/tmp",
                        ),
                    )
                assertTrue(advRes.exitCode == 0, "Failed to advance fixture branch: ${advRes.stderr}")
                val secondCommit =
                    advRes.stdout.lines().map { it.trim() }.last {
                        it.matches(
                            Regex("^[a-fA-F0-9]{40}$"),
                        )
                    }
                assertTrue(secondCommit != initialCommit, "Fixture branch must have advanced to a new commit")

                // Build with earlier resolved commit
                val req =
                    InstallRequest(
                        opId = "op-branch-move-1",
                        group = group.id,
                        label = "main",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Branch("main"),
                            commit = initialCommit,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = true,
                        choice = ChoiceUpdate.Keep,
                    )

                val events = host.store.install(req).toList()
                val finished = events.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertTrue(finished?.result is OperationResult.Installed, "Install must succeed: $finished")

                // Verify the version.json recorded initialCommit, not secondCommit
                val vJsonRes =
                    host.transport.execute(
                        ToolExecutionRequest(
                            toolId = "cat",
                            arguments =
                            listOf(
                                "${hostPath(
                                    host,
                                    "LtiRomTools",
                                )}/bin/${group.id.value}/${(finished.result as OperationResult.Installed).versionId}/version.json",
                            ),
                            workingDirectory = "/tmp",
                        ),
                    )
                assertTrue(vJsonRes.exitCode == 0, "Must read version.json: ${vJsonRes.stderr}")
                assertTrue(
                    vJsonRes.stdout.contains(initialCommit),
                    "version.json must record earlier resolved commit: ${vJsonRes.stdout}",
                )
                assertFalse(
                    vJsonRes.stdout.contains(secondCommit),
                    "version.json must not record advanced commit: ${vJsonRes.stdout}",
                )
            }
        }
    }

    @Test
    fun catalogLayoutPathMissingFailsUnsupportedLayoutBeforeAnyBuild() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "layout-missing-repo")
            // Repo without CMakeLists.txt
            val setupRes =
                Fixtures.setupGitRepo(
                    transport = host.transport,
                    repoPath = repoPath,
                    branch = "main",
                    tag = "v1.0.0",
                )
            assertTrue(setupRes.isSuccess, "Git repo setup failed: ${setupRes.exceptionOrNull()?.message}")
            val revReq =
                ToolExecutionRequest(
                    toolId = "sh",
                    arguments = listOf("-c", "cd '$repoPath' && git rev-parse HEAD"),
                    workingDirectory = "/tmp",
                )
            val commit = host.transport.execute(revReq).stdout.trim()

            // Group requiring CMakeLists.txt in its layout
            val baseGroup =
                Fixtures.createTestToolGroup(
                    id = "layout-missing-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Branch("main"),
                    commit = commit,
                )
            val groupWithLayout = baseGroup.copy(layout = listOf("CMakeLists.txt"))

            ToolGroupCatalog.withIsolatedTestGroup(groupWithLayout) {
                // 1. resolve fails with UnsupportedLayout([CMakeLists.txt])
                val resolveRes = host.store.resolve(groupWithLayout.id, repoPath, ToolRef.Branch("main"))
                assertTrue(resolveRes.isFailure, "resolve must fail when required layout is missing")
                val ex = resolveRes.exceptionOrNull()
                assertIs<ToolErrorException>(ex)
                val err = ex.toolError
                assertIs<ToolError.UnsupportedLayout>(err)
                assertEquals(listOf("CMakeLists.txt"), err.missingPaths)

                // 2. install also refuses before building
                val req =
                    InstallRequest(
                        opId = "op-layout-missing-1",
                        group = groupWithLayout.id,
                        label = "main",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Branch("main"),
                            commit = commit,
                        ),
                        recipe = groupWithLayout.recipe,
                        outputs = groupWithLayout.outputs,
                    )
                val events = host.store.install(req).toList()
                val finished = events.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(finished)
                val failed = finished.result as? OperationResult.Failed
                assertNotNull(failed, "Install must fail: ${finished.result}")
                assertIs<ToolError.UnsupportedLayout>(failed.error)
                assertEquals(listOf("CMakeLists.txt"), (failed.error as ToolError.UnsupportedLayout).missingPaths)
            }
        }
    }

    @Test
    fun urlWithCredentialsIsRejectedBeforeCommandRunsAndTokenNeverLogged() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val secretToken = "super_secret_token_abcdef12345"
            val credUrl = "https://user:$secretToken@github.com/myorg/myrepo.git"

            val group =
                Fixtures.createTestToolGroup(
                    id = "cred-tool",
                    repoUrl = credUrl,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                // 1. refs rejects credential URL
                val refsRes = host.store.refs(group.id, credUrl)
                assertTrue(refsRes.isFailure, "refs must fail for credential URL")
                val refsEx = refsRes.exceptionOrNull()
                assertNotNull(refsEx)
                assertFalse(
                    refsEx.message.orEmpty().contains(secretToken),
                    "Token must not appear in exception message",
                )

                // 2. resolve rejects credential URL
                val resolveRes = host.store.resolve(group.id, credUrl, ToolRef.Branch("main"))
                assertTrue(resolveRes.isFailure, "resolve must fail for credential URL")
                val resolveEx = resolveRes.exceptionOrNull()
                assertNotNull(resolveEx)
                assertFalse(
                    resolveEx.message.orEmpty().contains(secretToken),
                    "Token must not appear in exception message",
                )

                // 3. install rejects credential URL
                val req =
                    InstallRequest(
                        opId = "op-cred-1",
                        group = group.id,
                        label = "v1.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = credUrl,
                            ref = ToolRef.Tag("v1.0.0"),
                            commit = "1111111111111111111111111111111111111111",
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                    )
                val events = host.store.install(req).toList()
                val finished = events.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(finished)
                val failed = finished.result as? OperationResult.Failed
                assertNotNull(failed, "Install must fail for credential URL: ${finished.result}")
                assertFalse(failed.message.contains(secretToken), "Token must not appear in failure message")
            }
        }
    }

    @Test
    fun refsRunsAsNetworkStreamRetryingClosedPortThreeTimes() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            // Bind ephemeral port and immediately close it
            val socket = ServerSocket(0)
            val closedPort = socket.localPort
            socket.close()

            val closedPortUrl = "http://127.0.0.1:$closedPort/repo.git"
            val group =
                Fixtures.createTestToolGroup(
                    id = "retry-tool",
                    repoUrl = closedPortUrl,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                val res = host.store.refs(group.id, closedPortUrl)
                assertTrue(res.isFailure, "refs against closed port must fail")
                val ex = res.exceptionOrNull()
                assertNotNull(ex)
                val err = (ex as? ToolErrorException)?.toolError
                assertIs<ToolError.Network>(err)
                assertTrue(
                    err.reason.contains("3") || err.reason.contains("Connection") || err.reason.contains("Failed") ||
                        err.reason.contains("connect"),
                    "Network error must report retry failure: ${err.reason}",
                )
            }
        }
    }

    @Test
    fun forkBranchAndTagResolveToForkCommitWhenMirrorAlreadyExists() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val upstreamPath = hostPath(host, "fork-upstream-repo")
            val forkPath = hostPath(host, "fork-child-repo")
            val setupScript =
                """
                    mkdir -p '$upstreamPath'
                    cd '$upstreamPath'
                    git init -b main
                    git config user.name 'Upstream Author'
                    git config user.email 'upstream@example.com'
                    echo 'upstream content' > file.txt
                    git add file.txt
                    git commit -m 'Upstream commit 1'
                    git tag v1.0.0
                    UPSTREAM_V1=${'$'}(git rev-parse 'v1.0.0^{commit}')

                    # Create fork cloned from upstream
                    git clone '$upstreamPath' '$forkPath'
                    cd '$forkPath'
                    git config user.name 'Fork Author'
                    git config user.email 'fork@example.com'
                    echo 'fork feature' > fork.txt
                    git add fork.txt
                    git commit -m 'Fork commit 1'
                    git checkout -b fork-branch
                    git tag v2.0.0-fork
                    FORK_TAG=${'$'}(git rev-parse 'v2.0.0-fork^{commit}')
                    FORK_BRANCH=${'$'}(git rev-parse 'refs/heads/fork-branch^{commit}')

                    echo "UPSTREAM_V1=${'$'}UPSTREAM_V1"
                    echo "FORK_TAG=${'$'}FORK_TAG"
                    echo "FORK_BRANCH=${'$'}FORK_BRANCH"
                """.trimIndent()
            val req =
                ToolExecutionRequest(
                    toolId = "sh",
                    arguments = listOf("-c", setupScript),
                    workingDirectory = "/tmp",
                )
            val res = host.transport.execute(req)
            assertTrue(res.exitCode == 0, "Failed to setup repos: ${res.stderr}")

            val upstreamV1 = res.stdout.lines().first { it.startsWith("UPSTREAM_V1=") }.substringAfter("=").trim()
            val forkTag = res.stdout.lines().first { it.startsWith("FORK_TAG=") }.substringAfter("=").trim()
            val forkBranch = res.stdout.lines().first { it.startsWith("FORK_BRANCH=") }.substringAfter("=").trim()

            val group =
                Fixtures.createTestToolGroup(
                    id = "fork-tool",
                    repoUrl = upstreamPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = upstreamV1,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                // 1. Resolve from upstream first -> initializes mirror for fork-tool
                val upstreamResolve = host.store.resolve(group.id, upstreamPath, ToolRef.Tag("v1.0.0"))
                assertTrue(
                    upstreamResolve.isSuccess,
                    "Upstream resolve should succeed: ${upstreamResolve.exceptionOrNull()}",
                )
                assertEquals(upstreamV1, upstreamResolve.getOrThrow().commit)

                // 2. Now resolve fork tag from forkPath -> must resolve to forkTag commit
                val forkTagResolve = host.store.resolve(group.id, forkPath, ToolRef.Tag("v2.0.0-fork"))
                assertTrue(
                    forkTagResolve.isSuccess,
                    "Fork tag resolve failed: ${forkTagResolve.exceptionOrNull()?.message}",
                )
                assertEquals(
                    forkTag,
                    forkTagResolve.getOrThrow().commit,
                    "Fork tag must resolve to fork commit, not upstream",
                )

                // 3. Resolve fork branch from forkPath -> must resolve to forkBranch commit
                val forkBranchResolve = host.store.resolve(group.id, forkPath, ToolRef.Branch("fork-branch"))
                assertTrue(
                    forkBranchResolve.isSuccess,
                    "Fork branch resolve failed: ${forkBranchResolve.exceptionOrNull()?.message}",
                )
                assertEquals(
                    forkBranch,
                    forkBranchResolve.getOrThrow().commit,
                    "Fork branch must resolve to fork commit",
                )
            }
        }
    }

    @Test
    fun resolveExtractsSubmoduleGitlinkShaAndNotRepoUrl(): Unit = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "submodule-repo")
            val subSha = "0123456789abcdef0123456789abcdef01234567"
            val setupScript =
                """
                                    mkdir -p '$repoPath'
                                    cd '$repoPath'
                                    git init -b main
                                    git config user.name 'Submodule Author'
                                    git config user.email 'sub@example.com'
                                    echo 'hello' > README.md
                                    cat << 'EOF' > .gitmodules
                    [submodule "libs/engine"]
                    	path = libs/engine
                    	url = https://github.com/example/engine.git
                    EOF
                                    git add README.md .gitmodules
                                    git update-index --add --cacheinfo 160000 $subSha libs/engine
                                    git commit -m 'Add submodule gitlink'
                                    echo "COMMIT=${'$'}(git rev-parse HEAD)"
                """.trimIndent()
            val req =
                ToolExecutionRequest(
                    toolId = "sh",
                    arguments = listOf("-c", setupScript),
                    workingDirectory = "/tmp",
                )
            val res = host.transport.execute(req)
            assertTrue(res.exitCode == 0, "Failed to setup repo: ${res.stderr}")
            val commit = res.stdout.lines().first { it.startsWith("COMMIT=") }.substringAfter("=").trim()

            val group =
                Fixtures.createTestToolGroup(
                    id = "submodule-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Branch("main"),
                    commit = commit,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                val resolved = host.store.resolve(group.id, repoPath, ToolRef.Branch("main"))
                assertTrue(resolved.isSuccess, "Resolve must succeed: ${resolved.exceptionOrNull()}")
                val check = resolved.getOrThrow()
                assertEquals(commit, check.commit)
                assertEquals(
                    mapOf("libs/engine" to subSha),
                    check.submodules,
                    "Submodules must map path to gitlink commit SHA, not URL",
                )
            }
        }
    }
}
