package org.ide.lti.core.data.setup.source

import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.data.setup.install.GitCache
import org.ide.lti.core.data.setup.install.detectHostArch
import org.ide.lti.core.domain.setup.CompatibilityResult
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.setup.UpdateAvailability
import org.ide.lti.core.domain.setup.ports.RefListing
import org.ide.lti.core.domain.setup.ports.ResolveOutcome
import org.ide.lti.core.domain.setup.ports.SourceResolverPort
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.core.model.setup.ToolSelection
import java.io.File

public class GitSourceResolver(
    private val cli: WslCliExecutor,
    private val gitCache: GitCache,
    private val distroProvider: () -> String = { "Ubuntu" },
    private val catalog: ToolGroupCatalog = ToolGroupCatalog,
) : SourceResolverPort {
    public constructor(cli: WslCliExecutor, gitCache: GitCache, distro: String) :
        this(cli, gitCache, { distro }, ToolGroupCatalog)

    private val distro: String get() = distroProvider()

    override suspend fun listRefs(group: ToolGroupId, repoUrl: String?): RefListing {
        val releaseRecipe = catalog.group(group)?.recipe as? RecipeConfig.Release
        if (releaseRecipe != null) {
            val versions = releaseRecipe.versions.map { it.version }
            return RefListing(tags = versions, branches = emptyList())
        }
        return listGitRefs(group, repoUrl)
    }

    private suspend fun listGitRefs(group: ToolGroupId, repoUrl: String?): RefListing {
        val url = resolveRepoUrl(group, repoUrl)
        val result = if (url != null) cli.run(distro, listOf("git", "ls-remote", "--tags", "--heads", url)) else null
        if (result == null || result.exitCode != 0) return RefListing()

        val parsedRefs = result.output.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"), limit = 2)
                if (parts.size >= 2) parts[1] else null
            }

        val tags = parsedRefs
            .filter { it.startsWith("refs/tags/") }
            .map { it.removePrefix("refs/tags/").removeSuffix("^{}") }
            .distinct()
            .sorted()

        val branches = parsedRefs
            .filter { it.startsWith("refs/heads/") }
            .map { it.removePrefix("refs/heads/") }
            .distinct()
            .sorted()

        return RefListing(tags = tags, branches = branches)
    }

    override suspend fun resolve(selection: ToolSelection): ResolveOutcome {
        val group = ToolGroupId(selection.group)
        val url = resolveRepoUrl(group, selection.repoUrl)

        return when (val ref = selection.ref) {
            is ToolRef.ReleaseVersion -> resolveRelease(group, ref)
            is ToolRef.Tag -> {
                val relRecipe = catalog.group(group)?.recipe as? RecipeConfig.Release
                if (relRecipe != null) {
                    resolveRelease(group, ToolRef.ReleaseVersion(ref.name))
                } else if (url == null) {
                    ResolveOutcome.NotFound
                } else {
                    resolveTag(group, url, ref)
                }
            }
            is ToolRef.Branch -> {
                if (url == null) ResolveOutcome.NotFound else resolveBranch(group, url, ref)
            }
            is ToolRef.Commit -> {
                if (url == null) ResolveOutcome.NotFound else resolveCommit(group, url, ref)
            }
        }
    }

    override suspend fun checkLayout(input: ResolvedInput.Git): CompatibilityResult {
        val group = catalog.group(input.group)
        val url = input.repoUrl ?: resolveRepoUrl(input.group, null)
        return when {
            group == null -> CompatibilityResult.Unsupported(emptyList())
            url == null -> CompatibilityResult.Unreachable("No repo URL")
            else -> inspectLayoutTree(url, input.commit, group.layout)
        }
    }

    private suspend fun inspectLayoutTree(
        url: String,
        commit: String,
        expectedLayout: List<String>,
    ): CompatibilityResult {
        val mirrorPath = gitCache.getMirrorPath(distro, url)
        if (!isCommitPresent(mirrorPath, commit)) {
            val syncResult = gitCache.syncMirror(distro, url)
            val syncError = categorizeGitError(syncResult)
            if (syncError != null) return compatibilityFor(syncError, expectedLayout)
            if (!isCommitPresent(mirrorPath, commit)) {
                val lockPath = gitCache.getLockPath(distro, url)
                fetchCommit(lockPath, mirrorPath, listOf("--depth", "1", "origin", commit), commit)
                fetchCommit(lockPath, mirrorPath, listOf("origin", commit), commit)
            }
        }
        val res = cli.run(distro, listOf("git", "-C", mirrorPath, "ls-tree", "-r", "-t", "--name-only", commit))
        if (res.exitCode != 0) {
            return compatibilityFor(categorizeGitError(res), expectedLayout)
        }

        val treeFiles = res.output.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val missing = expectedLayout.filter { req ->
            req !in treeFiles && treeFiles.none { it == req || it.startsWith("$req/") }
        }
        return if (missing.isEmpty()) {
            CompatibilityResult.LayoutCompatible
        } else {
            CompatibilityResult.Unsupported(missing)
        }
    }

    override suspend fun updateAvailability(frozen: ResolvedInput.Git): UpdateAvailability {
        val branchRef = frozen.ref as? ToolRef.Branch
        val url = frozen.repoUrl ?: resolveRepoUrl(frozen.group, null)
        return if (branchRef != null && url != null) {
            queryBranchUpdate(url, branchRef.name, frozen.commit)
        } else {
            UpdateAvailability.UpToDate
        }
    }

    private suspend fun queryBranchUpdate(url: String, branchName: String, currentCommit: String): UpdateAvailability {
        val res = cli.run(distro, listOf("git", "ls-remote", "--heads", url, "refs/heads/$branchName"))
        val line = res.output.lines().firstOrNull { it.isNotBlank() }
        val newSha = line?.trim()?.split(Regex("\\s+"), limit = 2)?.firstOrNull().orEmpty()
        return if (res.exitCode == 0 && newSha.isNotBlank() && newSha != currentCommit) {
            UpdateAvailability.UpdateAvailable(newCommit = newSha)
        } else {
            UpdateAvailability.UpToDate
        }
    }

    private fun resolveRepoUrl(group: ToolGroupId, userUrl: String?): String? = userUrl?.trim()?.ifBlank { null }
        ?: (catalog.group(group)?.source as? ToolSource.Git)?.recommendedUrl

    private fun resolveRelease(group: ToolGroupId, ref: ToolRef.ReleaseVersion): ResolveOutcome {
        val recipe = catalog.group(group)?.recipe as? RecipeConfig.Release
        val matching = recipe?.versions?.firstOrNull { it.version == ref.version }
        return if (matching != null) {
            ResolveOutcome.Resolved(
                ResolvedInput.Release(
                    group = group,
                    version = matching.version,
                    url = matching.url,
                    archiveMember = matching.archiveMember,
                    sha256 = matching.sha256,
                    platform = "linux-${detectHostArch()}",
                ),
            )
        } else {
            ResolveOutcome.NotFound
        }
    }

    private suspend fun resolveTag(group: ToolGroupId, url: String, tag: ToolRef.Tag): ResolveOutcome {
        val tagRef = "refs/tags/${tag.name}"
        val peeledRef = "refs/tags/${tag.name}^{}"
        val result = cli.run(distro, listOf("git", "ls-remote", "--tags", url, tagRef, peeledRef))
        val errorOutcome = categorizeGitError(result)
        if (errorOutcome != null) return errorOutcome

        val lines = result.output.lines().map { it.trim() }.filter { it.isNotBlank() }
        val peeledSha = lines.firstOrNull { it.endsWith(peeledRef) }?.substringBefore('\t')?.substringBefore(' ')
        val tagSha = lines.firstOrNull { it.endsWith(tagRef) }?.substringBefore('\t')?.substringBefore(' ')
        val resolvedSha = (peeledSha ?: tagSha)?.takeIf { it.matches(HEX_40_REGEX) }

        return if (resolvedSha != null) {
            val cacheSync = gitCache.syncMirror(distro, url)
            categorizeGitError(cacheSync)?.let { return it }
            val submodules = extractSubmodules(url, resolvedSha)
            ResolveOutcome.Resolved(
                ResolvedInput.Git(
                    group = group,
                    repoUrl = url,
                    ref = tag,
                    commit = resolvedSha,
                    submoduleCommits = submodules,
                    resolvedAt = System.currentTimeMillis(),
                ),
            )
        } else {
            ResolveOutcome.NotFound
        }
    }

    private suspend fun resolveBranch(group: ToolGroupId, url: String, branch: ToolRef.Branch): ResolveOutcome {
        val result = cli.run(distro, listOf("git", "ls-remote", "--heads", url, "refs/heads/${branch.name}"))
        val errorOutcome = categorizeGitError(result)
        if (errorOutcome != null) return errorOutcome

        val sha = result.output.lines()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.substringBefore('\t')
            ?.substringBefore(' ')
            ?.takeIf { it.matches(HEX_40_REGEX) }

        return if (sha != null) {
            val cacheSync = gitCache.syncMirror(distro, url)
            categorizeGitError(cacheSync)?.let { return it }
            val submodules = extractSubmodules(url, sha)
            ResolveOutcome.Resolved(
                ResolvedInput.Git(
                    group = group,
                    repoUrl = url,
                    ref = branch,
                    commit = sha,
                    submoduleCommits = submodules,
                    resolvedAt = System.currentTimeMillis(),
                ),
            )
        } else {
            ResolveOutcome.NotFound
        }
    }

    private suspend fun resolveCommit(group: ToolGroupId, url: String, commitRef: ToolRef.Commit): ResolveOutcome {
        val sha = commitRef.sha.trim().lowercase()
        if (!sha.matches(HEX_40_REGEX)) return ResolveOutcome.NotFound
        val mirrorPath = gitCache.getMirrorPath(distro, url)
        val lockPath = gitCache.getLockPath(distro, url)

        val parentDir = File(mirrorPath).parent?.replace('\\', '/') ?: "."
        val mkdir = cli.run(distro, listOf("mkdir", "-p", parentDir))
        if (mkdir.exitCode != 0) return ResolveOutcome.Unreachable(mkdir.error.ifBlank { mkdir.output })

        val sync = gitCache.syncMirror(distro, url)
        categorizeGitError(sync)?.let { return it }

        val confirmed = isCommitPresent(mirrorPath, sha) ||
            fetchCommit(lockPath, mirrorPath, listOf("--depth", "1", "origin", sha), sha) ||
            fetchCommit(lockPath, mirrorPath, listOf("origin", sha), sha)

        return if (confirmed) {
            val submodules = extractSubmodules(url, sha)
            makeResolvedGitCommit(group, url, commitRef, sha, submodules)
        } else {
            ResolveOutcome.NotFound
        }
    }

    @Suppress("ReturnCount")
    private suspend fun extractSubmodules(url: String, sha: String): Map<String, String> {
        val mirrorPath = gitCache.getMirrorPath(distro, url)
        if (!isCommitPresent(mirrorPath, sha)) return emptyMap()
        val treeRes = cli.run(distro, listOf("git", "-C", mirrorPath, "ls-tree", "-r", sha))
        if (treeRes.exitCode != 0) return emptyMap()

        return treeRes.output.lines()
            .mapNotNull { parseSubmoduleGitlink(it.trim()) }
            .toMap()
    }

    private fun parseSubmoduleGitlink(trimmed: String): Pair<String, String>? {
        if (!trimmed.startsWith("160000 commit ")) return null
        val parts = trimmed.split(Regex("\\s+"), limit = 4)
        val subSha = parts.getOrNull(2)?.lowercase()
        val subPath = parts.getOrNull(3)
        return if (subSha != null && subSha.matches(HEX_40_REGEX) && !subPath.isNullOrBlank()) {
            subPath to subSha
        } else {
            null
        }
    }

    private suspend fun isCommitPresent(mirrorPath: String, sha: String): Boolean =
        cli.run(distro, listOf("git", "-C", mirrorPath, "cat-file", "-e", "$sha^{commit}")).exitCode == 0

    private suspend fun fetchCommit(
        lockPath: String,
        mirrorPath: String,
        fetchArgs: List<String>,
        sha: String,
    ): Boolean {
        val result = cli.run(distro, listOf("flock", "-x", lockPath, "git", "-C", mirrorPath, "fetch") + fetchArgs)
        return result.exitCode == 0 && isCommitPresent(mirrorPath, sha)
    }

    private fun compatibilityFor(
        error: ResolveOutcome?,
        expectedLayout: List<String>,
    ): CompatibilityResult = when (error) {
        is ResolveOutcome.AccessDenied -> CompatibilityResult.AccessDenied
        is ResolveOutcome.Unreachable -> CompatibilityResult.Unreachable(error.reason)
        else -> CompatibilityResult.Unsupported(expectedLayout)
    }

    private fun makeResolvedGitCommit(
        group: ToolGroupId,
        url: String,
        ref: ToolRef.Commit,
        sha: String,
        submodules: Map<String, String> = emptyMap(),
    ): ResolveOutcome.Resolved = ResolveOutcome.Resolved(
        ResolvedInput.Git(
            group = group,
            repoUrl = url,
            ref = ref,
            commit = sha,
            submoduleCommits = submodules,
            resolvedAt = System.currentTimeMillis(),
        ),
    )

    private fun categorizeGitError(result: CliExecutionResult): ResolveOutcome? {
        if (result.exitCode == 0) return null
        val combined = "${result.output}\n${result.error}".lowercase()
        return when {
            combined.contains("permission denied") ||
                combined.contains("authentication failed") ||
                combined.contains("could not read username") ||
                combined.contains("terminal prompts disabled") -> ResolveOutcome.AccessDenied
            combined.contains("could not resolve host") ||
                combined.contains("failed to connect") ||
                combined.contains("connection timed out") ||
                combined.contains("network is unreachable") ||
                combined.contains("unable to access") -> ResolveOutcome.Unreachable(
                result.error.ifBlank { result.output },
            )
            else -> null
        }
    }

    private companion object {
        private val HEX_40_REGEX = Regex("^[0-9a-f]{40}$")
    }
}
