/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.tools

import io.ltirom.tooling.client.resilience.DaemonHeartbeatMonitor
import io.ltirom.tooling.core.RemoteCancellationUnconfirmedException
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.RunPurpose
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.ide.lti.core.data.tools.KotlinStoreLayout.matches
import org.ide.lti.core.data.tools.KotlinStoreLayout.shellQuote
import org.ide.lti.core.data.tools.KotlinStoreLayout.toData
import org.ide.lti.core.data.tools.KotlinStoreLayout.toDomain
import org.ide.lti.core.data.tools.KotlinStoreLayout.typeName
import org.ide.lti.core.data.tools.KotlinStoreLayout.validateGroupId
import org.ide.lti.core.data.tools.KotlinStoreLayout.validateOpId
import org.ide.lti.core.data.tools.KotlinStoreLayout.validateRelativePath
import org.ide.lti.core.data.tools.KotlinStoreLayout.validateVersionId
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolOutputKind
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.tools.BranchRefInfo
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.DurableOperationResult
import org.ide.lti.core.domain.tools.GroupLinks
import org.ide.lti.core.domain.tools.GroupState
import org.ide.lti.core.domain.tools.InitInfo
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.InterruptedOp
import org.ide.lti.core.domain.tools.OperationPhase
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.PinnedTools
import org.ide.lti.core.domain.tools.RefListing
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ResolvedSourceCheck
import org.ide.lti.core.domain.tools.Selection
import org.ide.lti.core.domain.tools.TagRefInfo
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolInventory
import org.ide.lti.core.domain.tools.ToolStorePort
import org.ide.lti.core.model.setup.ToolRef
import java.util.concurrent.ConcurrentHashMap

public typealias ToolErrorException = org.ide.lti.core.domain.tools.ToolErrorException

@Suppress("LargeClass")
public class KotlinToolStoreAdapter(
    private val transport: RemoteTransportPort,
    private val heartbeatMonitor: DaemonHeartbeatMonitor? = null,
    private val serverDirProvider: suspend () -> String,
    private val catalog: ToolGroupCatalog = ToolGroupCatalog,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        },
    private val freeSpaceThresholdKbProvider: suspend (ToolGroupId) -> Long = { group ->
        if (group.value == "android-tools") 8L * 1024 * 1024 else 2L * 1024 * 1024
    },
    private val commandRunner: RemoteCommandRunner = RemoteCommandRunner(transport),
) : ToolStorePort {
    private val recipeRunner = KotlinRecipeRunner(transport, commandRunner)
    private val activeOperations = ConcurrentHashMap<ToolGroupId, String>()
    private val activeJobs = ConcurrentHashMap<ToolGroupId, Job>()
    private val unconfirmedCancellations = UnconfirmedCancellations(transport)
    private val cancelledGroups = ConcurrentHashMap.newKeySet<ToolGroupId>()

    /**
     * Held while a group's folders and `current` link change (publish, switch, remove): their filesystem checks
     * and the changes that follow must not interleave, or a removal could delete the version just switched to.
     */
    private val groupLock = GroupLocks()

    /** Cancel can arrive between two commands; every phase boundary asks before it goes on. */
    private fun ensureNotCancelled(group: ToolGroupId) {
        if (group in cancelledGroups) throw CancellationException("Installation of ${group.value} was cancelled")
    }

    private suspend fun homeDir(): String {
        val serverDir = serverDirProvider()
        return serverDir.substringBeforeLast("/.ltirom/", missingDelimiterValue = serverDir)
    }

    private suspend fun toolsBaseDir(): String = "${homeDir()}/LtiRomTools"

    private suspend fun hasCommandAdmission(): Boolean = heartbeatMonitor?.let {
        val acknowledged = it.awaitInitialHeartbeat()
        if (acknowledged) it.start()
        acknowledged
    } ?: true

    private suspend fun exec(cmd: List<String>, workDir: String? = null, timeoutSeconds: Long = 30L): Result<String> {
        if (!hasCommandAdmission()) {
            val admissionErr = ToolError.AdmissionRequired("Build service did not acknowledge client heartbeat")
            return Result.failure(ToolErrorException(admissionErr))
        }
        val wd = workDir ?: homeDir()
        val res =
            transport.execute(
                ToolExecutionRequest(
                    toolId = cmd.first(),
                    arguments = cmd.drop(1),
                    workingDirectory = wd,
                    timeoutMs = timeoutSeconds * 1_000L,
                    purpose = RunPurpose.SETUP,
                ),
            )
        return if (res.exitCode == 0) {
            Result.success(res.stdout)
        } else {
            val cmdStr = cmd.take(2).joinToString(" ")
            val errOutput = res.stderr.ifBlank { res.stdout }
            val failErr = ToolError.BuildFailed("Command '$cmdStr' failed with exit ${res.exitCode}: $errOutput")
            Result.failure(ToolErrorException(failErr))
        }
    }

    override suspend fun init(): Result<InitInfo> = try {
        val base = toolsBaseDir()
        val qBase = shellQuote(base)
        val script =
            buildString {
                appendLine("set -e")
                appendLine("mkdir -p $qBase/src $qBase/work $qBase/results")
                appendLine("for old in $qBase/bin.old-*; do")
                appendLine("  if [ -e \"\$old\" ] || [ -L \"\$old\" ]; then")
                appendLine("    rm -rf \"\$old\"")
                appendLine("    echo \"DELETED=\$old\"")
                appendLine("  fi")
                appendLine("done")
                appendLine("mkdir -p $qBase/bin")
                // Mirrors used to be shared per group, whatever the repository: a fork then resolved against its
                // upstream's tags and commits. They are rebuilt per repository on demand, so the old ones go.
                for (groupId in ToolGroupCatalog.allGroups.map { it.id.value }) {
                    appendLine(
                        "rm -rf $qBase/src/${shellQuote(groupId)} $qBase/src/${shellQuote("$groupId-modules")}",
                    )
                }
                val legacyOutputs =
                    ToolGroupCatalog.allOutputs
                        .flatMap { listOf(it.file, it.toolId) + it.aliases.keys }
                        .toSet()
                for (legacyName in legacyOutputs.sorted()) {
                    val qName = shellQuote(legacyName)
                    appendLine("if [ -f $qBase/bin/$qName ] || [ -L $qBase/bin/$qName ]; then")
                    appendLine("  rm -f $qBase/bin/$qName")
                    appendLine("  echo \"DELETED=$base/bin/$legacyName\"")
                    appendLine("fi")
                }
            }
        val stdout = exec(listOf("sh", "-c", script)).getOrThrow()
        val deleted =
            stdout.lines()
                .filter { it.startsWith("DELETED=") }
                .map { it.substringAfter("DELETED=").trim() }
                .filter { it.isNotBlank() }
        val moved =
            stdout.lines().firstOrNull { it.startsWith("LEGACY_MOVED=") }
                ?.substringAfter("LEGACY_MOVED=")
                ?.trim()
                ?.ifBlank { null }
        Result.success(InitInfo(legacyBinMovedTo = moved, deletedPaths = deleted))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.failure(e)
    }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    override suspend fun status(catalog: ToolGroupCatalog): Result<ToolInventory> = try {
        val base = toolsBaseDir()
        val sFile = ToolSettingsFile(commandRunner, base)
        val settingsRes = sFile.readSettings()
        if (settingsRes.isFailure) {
            val ex = settingsRes.exceptionOrNull()
            return Result.failure(ex ?: ToolErrorException(ToolError.Internal("$base/settings.json can't be read")))
        }
        val selections = settingsRes.getOrThrow().picks
        val unreadablePicks = settingsRes.getOrThrow().unreadable
        val groupStates = mutableMapOf<ToolGroupId, GroupState>()

        val interruptedScript =
            buildString {
                appendLine("if [ -d " + shellQuote("$base/work") + " ]; then")
                appendLine("  for d in " + shellQuote("$base/work") + "/*; do")
                appendLine("    if [ -d \"\$d\" ] && [ -f \"\$d/op.json\" ]; then")
                appendLine("      opId=\$(basename \"\$d\")")
                appendLine("      if [ ! -f " + shellQuote("$base/results") + "/\"\$opId.json\" ]; then")
                appendLine("        echo \"INTERRUPTED_START=\$opId\"")
                appendLine("        cat \"\$d/op.json\"")
                appendLine("        echo 'INTERRUPTED_END'")
                appendLine("      fi")
                appendLine("    fi")
                appendLine("  done")
                appendLine("fi")
            }
        val interruptedRes = exec(listOf("sh", "-c", interruptedScript)).getOrNull() ?: ""
        val interruptedByGroup = parseInterruptedOps(interruptedRes)

        for (group in catalog.allGroups) {
            val groupDir = "$base/bin/${group.id.value}"
            val qGroupDir = shellQuote(groupDir)
            val inspectScript = KotlinStoreLayout.buildInspectScript(qGroupDir)
            val res = exec(listOf("sh", "-c", inspectScript)).getOrThrow()
            val groupSelection = selections[group.id]?.let { Selection(it.first, it.second) }
            val interruptedOp = if (!activeOperations.containsKey(group.id)) interruptedByGroup[group.id] else null
            if (res.trim() == "ABSENT") {
                groupStates[group.id] =
                    GroupState(
                        group = group.id,
                        installed = emptyList(),
                        current = null,
                        selection = groupSelection,
                        interrupted = interruptedOp,
                    )
                continue
            }

            val lines = res.lines()
            val rawCurrent =
                lines.firstOrNull { it.startsWith("CURRENT=") }
                    ?.substringAfter("CURRENT=")
                    ?.trim()
                    ?.ifBlank { null }

            val installedList =
                KotlinStoreLayout.parseGroupInstalledVersions(
                    groupDir = groupDir,
                    lines = lines,
                    groupId = group.id,
                    catalog = catalog,
                    json = json,
                    exec = { exec(it) },
                    probeVersion = rawCurrent,
                )
            val currentVer = installedList.firstOrNull { it.id == rawCurrent }
            val isKnownSelection = groupSelection != null && groupSelection.id == rawCurrent
            val isKnownPinned =
                when (val src = group.source) {
                    is ToolSource.Git -> {
                        val resolved = ResolvedSource.Git(src.recommendedUrl, src.pinnedRef, src.pinnedCommit)
                        KotlinStoreLayout.computeVersionId(resolved) == rawCurrent
                    }
                    is ToolSource.Release -> {
                        (group.recipe as? RecipeConfig.Release)?.versions?.any { it.version == rawCurrent } == true
                    }
                }
            val defaultGroup = ToolGroupCatalog.DEFAULT_GROUPS.firstOrNull { it.id == group.id }
            val isDefaultPinned =
                when (val src = defaultGroup?.source) {
                    is ToolSource.Git -> {
                        val resolved = ResolvedSource.Git(src.recommendedUrl, src.pinnedRef, src.pinnedCommit)
                        KotlinStoreLayout.computeVersionId(resolved) == rawCurrent
                    }
                    is ToolSource.Release -> {
                        (defaultGroup.recipe as? RecipeConfig.Release)?.versions?.any {
                            it.version == rawCurrent
                        } == true
                    }
                    null -> false
                }
            // A damaged pick is reported like an unknown selection: this group needs an explicit choice, the rest work.
            val currentUnknown =
                group.id in unreadablePicks ||
                    (
                        rawCurrent != null && currentVer == null && !isKnownSelection &&
                            !isKnownPinned && !isDefaultPinned
                        )
            groupStates[group.id] =
                GroupState(
                    group = group.id,
                    installed = installedList,
                    current = currentVer,
                    selection = groupSelection,
                    currentUnknown = currentUnknown,
                    interrupted = interruptedOp,
                )
        }
        Result.success(ToolInventory(groups = groupStates))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.failure(e)
    }

    private fun parseInterruptedOps(output: String): Map<ToolGroupId, InterruptedOp> {
        val result = mutableMapOf<ToolGroupId, MutableList<InterruptedOp>>()
        val lines = output.lines()
        var idx = 0
        while (idx < lines.size) {
            val line = lines[idx]
            if (line.startsWith("INTERRUPTED_START=")) {
                val opId = line.substringAfter("INTERRUPTED_START=").trim()
                idx++
                val sb = StringBuilder()
                while (idx < lines.size && lines[idx] != "INTERRUPTED_END") {
                    sb.appendLine(lines[idx])
                    idx++
                }
                val jsonStr = sb.toString().trim()
                val parsed =
                    runCatching {
                        json.parseToJsonElement(jsonStr).jsonObject
                    }.getOrNull()
                if (parsed != null) {
                    val grp = parsed["group"]?.jsonPrimitive?.contentOrNull
                    val ver = parsed["versionId"]?.jsonPrimitive?.contentOrNull
                    val startedAt = parsed["startedAt"]?.jsonPrimitive?.contentOrNull ?: ""
                    if (!grp.isNullOrBlank() && !ver.isNullOrBlank()) {
                        val op = InterruptedOp(opId = opId, versionId = ver, startedAt = startedAt)
                        result.getOrPut(ToolGroupId(grp)) { mutableListOf() }.add(op)
                    }
                }
            }
            idx++
        }
        return result.mapValues { (_, ops) ->
            ops.maxByOrNull { it.startedAt }!!
        }
    }

    private fun validateRepoUrl(url: String) {
        if (url.isBlank()) {
            throw IllegalArgumentException("Repository URL must not be blank")
        }
        val hasCredentials =
            try {
                val uri = java.net.URI(url)
                uri.userInfo != null
            } catch (_: Exception) {
                false
            } || Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://[^/\s@]+:[^/\s@]+@""").containsMatchIn(url) ||
                Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://[^/\s@]+@""").containsMatchIn(url)

        if (hasCredentials) {
            val err = ToolError.UnsupportedLayout(listOf("Repository URL must not contain credentials"))
            throw ToolErrorException(err)
        }
    }

    @Suppress("NestedBlockDepth")
    override suspend fun refs(group: ToolGroupId, repoUrl: String): Result<RefListing> = try {
        validateGroupId(group)
        validateRepoUrl(repoUrl)
        val qRepo = shellQuote(repoUrl)
        val script = "git ls-remote --tags --heads $qRepo"
        val out =
            commandRunner.streamCollect(
                script = script,
                category = CommandCategory.NETWORK,
                timeoutSeconds = CommandCategory.NETWORK.defaultTimeoutSeconds,
                cwd = homeDir(),
            ).getOrThrow()

        val tagsMap = mutableMapOf<String, String>()
        val branches = mutableListOf<BranchRefInfo>()
        for (line in out.lines()) {
            val parts = line.split(Regex("""\s+"""), limit = 2)
            if (parts.size == 2) {
                val commit = parts[0].trim()
                val ref = parts[1].trim()
                if (ref.startsWith("refs/tags/")) {
                    val isPeeled = ref.endsWith("^{}")
                    val name = ref.removePrefix("refs/tags/").removeSuffix("^{}")
                    if (isPeeled || !tagsMap.containsKey(name)) {
                        tagsMap[name] = commit
                    }
                } else if (ref.startsWith("refs/heads/")) {
                    val name = ref.removePrefix("refs/heads/")
                    branches.add(BranchRefInfo(name, commit))
                }
            }
        }
        val tags = tagsMap.map { (name, commit) -> TagRefInfo(name, commit) }
        Result.success(RefListing(tags = tags, branches = branches.distinctBy { it.name }))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.failure(e)
    }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    override suspend fun resolve(group: ToolGroupId, repoUrl: String, ref: ToolRef): Result<ResolvedSourceCheck> = try {
        validateGroupId(group)
        validateRepoUrl(repoUrl)
        val base = toolsBaseDir()
        val mirrorDir = KotlinStoreLayout.mirrorPath(base, group.value, repoUrl)
        val qMirror = shellQuote(mirrorDir)
        val qRepo = shellQuote(repoUrl)
        val targetRef =
            when (ref) {
                is ToolRef.Tag -> "refs/tags/${ref.name}^{commit}"
                is ToolRef.Branch -> "refs/heads/${ref.name}^{commit}"
                is ToolRef.Commit -> "${ref.sha}^{commit}"
                is ToolRef.ReleaseVersion -> "refs/tags/${ref.version}^{commit}"
            }
        val prepScript = KotlinStoreLayout.buildMirrorScript(base, group.value, repoUrl, lenientFetch = false)
        val prepRes =
            commandRunner.streamCollect(
                script = prepScript,
                category = CommandCategory.NETWORK,
                timeoutSeconds = CommandCategory.NETWORK.defaultTimeoutSeconds,
                cwd = homeDir(),
                maxOutputChars = RemoteCommandRunner.MAX_DIAGNOSTIC_CHARS,
            )
        if (prepRes.isFailure) {
            return Result.failure(prepRes.exceptionOrNull()!!)
        }

        val revParseRes = exec(listOf("git", "--git-dir=$mirrorDir", "rev-parse", targetRef))
        if (revParseRes.isFailure) {
            throw ToolErrorException(ToolError.RefNotFound("Could not resolve ref $ref in $repoUrl"))
        }
        val revOut = revParseRes.getOrThrow()
        val commit =
            revOut.lines().map { it.trim() }.lastOrNull { it.matches(Regex("^[a-fA-F0-9]{40}$")) }
                ?: throw ToolErrorException(ToolError.RefNotFound("Could not resolve ref $ref in $repoUrl"))

        // Layout check
        val groupDef = catalog.group(group)
        val layoutPaths = groupDef?.layout ?: emptyList()
        val sourceSubdir =
            when (val r = groupDef?.recipe) {
                is RecipeConfig.CMake -> r.sourceSubdir?.trim('/')?.ifBlank { null }
                else -> null
            }
        val missingPaths = mutableListOf<String>()
        for (path in layoutPaths) {
            val candidatePaths =
                if (sourceSubdir != null && !path.startsWith(sourceSubdir)) {
                    listOf(path, "$sourceSubdir/$path")
                } else {
                    listOf(path)
                }
            val found =
                candidatePaths.any { cp ->
                    val checkScript =
                        "test -n \"\$(git --git-dir=$qMirror ls-tree " + shellQuote(commit) + " -- " +
                            shellQuote(cp) + ")\""
                    exec(listOf("sh", "-c", checkScript)).isSuccess
                }
            if (!found) {
                missingPaths.add(path)
            }
        }
        if (missingPaths.isNotEmpty()) {
            throw ToolErrorException(ToolError.UnsupportedLayout(missingPaths))
        }

        // Submodules: resolve gitlink commit SHAs (mode 160000) from git tree
        val submodules = mutableMapOf<String, String>()
        val lsTreeScript = "git --git-dir=$qMirror ls-tree -r " + shellQuote(commit)
        // Not "no submodules": if the tree cannot be read, the reviewed input was not captured, so the
        // resolution fails instead of reporting an empty (and wrong) submodule set.
        val lsTreeOut =
            exec(listOf("sh", "-c", lsTreeScript)).getOrElse {
                throw ToolErrorException(
                    ToolError.Network("Could not read the submodules of $commit in $repoUrl: ${it.message}"),
                )
            }
        run {
            for (line in lsTreeOut.lines()) {
                val tabParts = line.split('\t', limit = 2)
                if (tabParts.size == 2) {
                    val metaParts = tabParts[0].trim().split(Regex("\\s+"))
                    if (metaParts.size >= 3 && metaParts[0] == "160000" && metaParts[1] == "commit") {
                        val subSha = metaParts[2]
                        val subPath = tabParts[1].trim()
                        submodules[subPath] = subSha
                    }
                }
            }
        }

        Result.success(ResolvedSourceCheck(commit = commit, submodules = submodules, layoutMissing = emptyList()))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.failure(e)
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override fun install(request: InstallRequest): Flow<InstallEvent> = flow {
        validateGroupId(request.group)
        validateOpId(request.opId)
        for (out in request.outputs) {
            validateRelativePath(out.file)
        }
        for (supp in request.supportFiles) {
            validateRelativePath(supp)
        }

        val base = toolsBaseDir()
        var versionId = KotlinStoreLayout.computeVersionId(request.source, request.label)
        var targetVerDir = "$base/bin/${request.group.value}/$versionId"
        val opId = request.opId
        var isRebuild = false

        when (val blocked = unconfirmedCancellations.recheck(request.group)) {
            is Recheck.Blocked -> throw RemoteCancellationUnconfirmedException(blocked.executionId)
            // The block came from an operation that was left registered; its process is confirmed gone now.
            Recheck.Lifted -> {
                activeOperations.remove(request.group)
                activeJobs.remove(request.group)
            }
            Recheck.Clear -> Unit
        }
        check(activeOperations.putIfAbsent(request.group, opId) == null) {
            "An operation is already active for ${request.group.value}"
        }
        currentCoroutineContext()[Job]?.let { activeJobs[request.group] = it }
        try {
            emit(InstallEvent.Started(opId))
            emit(InstallEvent.Phase(OperationPhase.Fetching))
            val reqSource = request.source
            if (reqSource is ResolvedSource.Git) {
                validateRepoUrl(reqSource.repoUrl)
            }

            // Whether the folder last looked at holds this same code (built by another recipe revision, say).
            var sameSourceFound = false

            @Suppress("ReturnCount")
            suspend fun checkIntactAndReuse(dir: String, vid: String): Boolean {
                sameSourceFound = false
                val cat = exec(listOf("cat", "$dir/version.json")).getOrNull() ?: return false
                if (cat.isBlank()) return false
                val existing = runCatching { json.decodeFromString<VersionData>(cat) }.getOrNull() ?: return false
                val sameSource = existing.source.matches(request.source.toData())
                val sameRecipe =
                    existing.recipe.type == request.recipe.typeName() &&
                        existing.recipe.revision == request.recipe.revision
                if (sameSource && sameRecipe && !request.forceNew) {
                    val check = KotlinStoreLayout.versionCheck(dir, request.outputs, request.supportFiles, probe = true)
                    val usable = exec(listOf("sh", "-c", check)).isSuccess
                    if (usable) {
                        if (request.select) {
                            groupLock(request.group).withLock {
                                ensureNotCancelled(request.group)
                                useInternal(request.group, vid, request.choice).getOrThrow()
                            }
                        }
                        emit(InstallEvent.Finished(OperationResult.Reused(vid)))
                        return true
                    }
                }
                if (sameSource && sameRecipe) {
                    isRebuild = true
                }
                sameSourceFound = sameSource
                return false
            }

            // Check if existing and intact
            val catJson = exec(listOf("cat", "$targetVerDir/version.json")).getOrNull()
            if (catJson != null && catJson.isNotBlank()) {
                val existing = runCatching { json.decodeFromString<VersionData>(catJson) }.getOrNull()
                if (existing != null) {
                    if (checkIntactAndReuse(targetVerDir, versionId)) {
                        return@flow
                    }
                    if (!isRebuild) {
                        // Disambiguate if the existing folder holds other code (commit, repository or submodules)
                        // under the same name; the whole source identity goes into the name, so two forks at one
                        // commit get two folders.
                        val suffix = KotlinStoreLayout.disambiguator(request.source)
                        if (!versionId.endsWith("-$suffix")) {
                            versionId = "$versionId-$suffix"
                            targetVerDir = "$base/bin/${request.group.value}/$versionId"
                            if (checkIntactAndReuse(targetVerDir, versionId)) {
                                return@flow
                            }
                            // This commit's own folder from an older recipe: nothing else can be named after it, so
                            // it is rebuilt in place (under the group lock when published) instead of colliding.
                            if (sameSourceFound) isRebuild = true
                        }
                    }
                }
            }
            if (request.forceNew) {
                isRebuild = true
            }

            val requiredKb = freeSpaceThresholdKbProvider(request.group)
            if (requiredKb > 0) {
                val home = homeDir()
                val dfRes = exec(listOf("df", "-Pk", home))
                if (dfRes.isSuccess) {
                    val lines = dfRes.getOrNull()?.lines() ?: emptyList()
                    val availKb =
                        lines.drop(1).firstOrNull()
                            ?.trim()
                            ?.split(Regex("""\s+"""))
                            ?.getOrNull(3)
                            ?.toLongOrNull()
                    if (availKb != null && availKb < requiredKb) {
                        val msg = "Required $requiredKb KB, but only $availKb KB available on $home"
                        val err = ToolError.DiskFull(msg)
                        emit(InstallEvent.Finished(OperationResult.Failed(err, err.message)))
                        return@flow
                    }
                }
            }

            if (request.packages.isNotEmpty()) {
                val missing = mutableListOf<String>()
                for (pkg in request.packages) {
                    val checkRes = exec(listOf("sh", "-c", KotlinStoreLayout.buildPackageCheck(pkg)))
                    if (checkRes.isFailure) {
                        missing += pkg
                    }
                }
                if (missing.isNotEmpty()) {
                    val err = ToolError.MissingPackages(missing)
                    emit(InstallEvent.Finished(OperationResult.Failed(err, err.message)))
                    return@flow
                }
            }

            val workDir = "$base/work/$opId"
            val srcDir = "$workDir/src"
            val outDir = "$workDir/out"

            emit(InstallEvent.Output("Preparing build workspace at $workDir"))
            val opJsonPayload =
                buildJsonObject {
                    put("group", request.group.value)
                    put("versionId", versionId)
                    put("startedAt", java.time.Instant.now().toString())
                }.toString()
            val prepWorkspaceScript =
                KotlinStoreLayout.buildPrepWorkspaceScript(
                    baseDir = base,
                    group = request.group.value,
                    opId = opId,
                    workDir = workDir,
                    srcDir = srcDir,
                    outDir = outDir,
                    opJsonPayload = opJsonPayload,
                )
            exec(listOf("sh", "-c", prepWorkspaceScript)).getOrThrow()

            ensureNotCancelled(request.group)
            // Source preparation
            when (val src = request.source) {
                is ResolvedSource.Git -> {
                    emit(InstallEvent.Output("Extracting source commit ${src.commit.take(7)}..."))
                    val gitPrepScript =
                        KotlinStoreLayout.buildGitPrepScript(
                            base = base,
                            group = request.group.value,
                            repoUrl = src.repoUrl,
                            commit = src.commit,
                            srcDir = srcDir,
                            submodules = src.submodules,
                        )
                    val streamEnv = mapOf("LTI_TOOL_GROUP" to request.group.value)
                    // Retried: the mirror and the submodule cache keep what each attempt fetched, so a retry
                    // continues instead of starting over. Exit 5 (submodule at the wrong commit) is final.
                    var attempt = 1
                    while (true) {
                        var exitCode = 0
                        var summary: String? = null
                        commandRunner.stream(
                            script = gitPrepScript,
                            category = CommandCategory.NETWORK,
                            // ponytail: wall-clock limit per attempt; an idle-output limit suits slow links better.
                            timeoutSeconds = CommandCategory.BUILD.defaultTimeoutSeconds,
                            cwd = homeDir(),
                            env = streamEnv,
                            onFinished = { exitCode = it },
                            onSummary = { summary = it },
                        ).collect { line ->
                            emit(InstallEvent.Output(line))
                        }
                        if (exitCode == 0) break
                        if (exitCode == SUBMODULE_MISMATCH_EXIT || attempt >= GIT_PREP_ATTEMPTS) {
                            val error =
                                if (RemoteCommandRunner.isTimeout(summary)) {
                                    ToolError.Timeout("Git preparation: ${summary.orEmpty()}")
                                } else {
                                    ToolError.Network("Git preparation failed with exit code $exitCode")
                                }
                            throw ToolErrorException(error)
                        }
                        attempt++
                        emit(
                            InstallEvent.Output(
                                "Git preparation failed (exit $exitCode); attempt $attempt of $GIT_PREP_ATTEMPTS...",
                            ),
                        )
                        delay(GIT_RETRY_DELAY_MS)
                    }
                }
                is ResolvedSource.Release -> {
                    emit(InstallEvent.Output("Preparing release download..."))
                }
            }

            // Layout validation before build
            val groupDef = catalog.group(request.group)
            val layoutPaths = groupDef?.layout ?: emptyList()
            val sourceSubdir =
                when (val r = groupDef?.recipe) {
                    is RecipeConfig.CMake -> r.sourceSubdir?.trim('/')?.ifBlank { null }
                    else -> null
                }
            val missingLayout = mutableListOf<String>()
            for (lp in layoutPaths) {
                val candidatePaths =
                    if (sourceSubdir != null && !lp.startsWith(sourceSubdir)) {
                        listOf(lp, "$sourceSubdir/$lp")
                    } else {
                        listOf(lp)
                    }
                val found =
                    candidatePaths.any { cp ->
                        val checkPathScript = "[ -e " + shellQuote("$srcDir/$cp") + " ]"
                        exec(listOf("sh", "-c", checkPathScript)).isSuccess
                    }
                if (!found) {
                    missingLayout.add(lp)
                }
            }
            if (missingLayout.isNotEmpty()) {
                val err = ToolError.UnsupportedLayout(missingLayout)
                runCatching {
                    writeResultFile(
                        opId,
                        request.group.value,
                        null,
                        reused = false,
                        ok = false,
                        error = err.message,
                    )
                    exec(listOf("rm", "-rf", "--", workDir))
                }
                emit(InstallEvent.Finished(OperationResult.Failed(err, err.message)))
                return@flow
            }

            ensureNotCancelled(request.group)
            // Recipe execution
            emit(InstallEvent.Phase(OperationPhase.Building))
            emit(InstallEvent.Output("Running build recipe: ${request.recipe::class.simpleName}..."))
            recipeRunner.executeRecipe(
                srcDir = srcDir,
                outDir = outDir,
                source = request.source,
                recipe = request.recipe,
                outputs = request.outputs,
                supportFiles = request.supportFiles,
                onLog = { emit(InstallEvent.Output(it)) },
                groupId = request.group.value,
            ).getOrThrow()

            ensureNotCancelled(request.group)
            // Verification of outputs & probes
            emit(InstallEvent.Phase(OperationPhase.Verifying))
            emit(InstallEvent.Output("Verifying build outputs..."))
            for (out in request.outputs) {
                emit(InstallEvent.Output("Running probe for ${out.toolId}: ${out.probe.args.joinToString(" ")}"))
                val ok =
                    exec(
                        listOf(
                            "sh",
                            "-c",
                            KotlinStoreLayout.versionCheck(outDir, listOf(out), request.supportFiles, probe = true),
                        ),
                    ).isSuccess
                if (!ok) throw ToolErrorException(ToolError.VerifyFailed(out.toolId))
            }

            // Write version.json
            val verData =
                VersionData(
                    schema = 1,
                    group = request.group.value,
                    id = versionId,
                    label = request.label,
                    source = request.source.toData(),
                    recipe = RecipeData(type = request.recipe.typeName(), revision = request.recipe.revision),
                    outputs =
                    request.outputs.map {
                        val mode = if (it.kind == ToolOutputKind.JAR) "read" else "exec"
                        OutputData(it.file, 0L, mode)
                    },
                    builtAt = System.currentTimeMillis().toString(),
                    intact = true,
                )
            val verJson = json.encodeToString(VersionData.serializer(), verData)
            val writeVerScript =
                buildString {
                    appendLine("set -e")
                    appendLine("cat << 'EOF' > " + shellQuote("$outDir/version.json.tmp"))
                    appendLine(verJson)
                    appendLine("EOF")
                    appendLine(
                        "mv " + shellQuote("$outDir/version.json.tmp") + " " + shellQuote("$outDir/version.json"),
                    )
                }
            exec(listOf("sh", "-c", writeVerScript)).getOrThrow()

            // Publish out/ to bin/<group>/<versionId>
            emit(InstallEvent.Phase(OperationPhase.Publishing))
            emit(InstallEvent.Output("Publishing version $versionId..."))
            val publishScript =
                KotlinStoreLayout.buildPublishScript(
                    groupDir = "$base/bin/${request.group.value}",
                    targetDir = targetVerDir,
                    outDir = outDir,
                    workDir = if (isRebuild) workDir else null,
                )
            groupLock(request.group).withLock {
                // The last point to stop: past it the version is published and (if asked) active.
                ensureNotCancelled(request.group)
                exec(listOf("sh", "-c", publishScript)).getOrThrow()
                if (request.select) {
                    ensureNotCancelled(request.group)
                    emit(InstallEvent.Phase(OperationPhase.Switching))
                    useInternal(request.group, versionId, request.choice).getOrThrow()
                }
            }

            // The version is published (and active if asked): from here on nothing may turn this into a failure.
            // Bookkeeping that doesn't succeed is a warning. A workspace left behind stays in work/ (nothing sweeps it;
            // with a result file it is not reported as interrupted either), which costs disk, not correctness.
            try {
                writeResultFile(opId, request.group.value, versionId, reused = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(InstallEvent.Output("Warning: $versionId is installed, its result file was not written"))
            }
            if (exec(listOf("rm", "-rf", "--", workDir)).isFailure) {
                emit(InstallEvent.Output("Warning: $versionId is installed; workspace $workDir was not removed"))
            }

            emit(InstallEvent.Finished(OperationResult.Installed(versionId)))
        } catch (e: Exception) {
            val wasCancelled = cancelledGroups.remove(request.group)
            if (e is RemoteCancellationUnconfirmedException) {
                unconfirmedCancellations.record(request.group, e.executionId)
                throw e
            }
            if (wasCancelled) {
                runCatching {
                    writeResultFile(
                        opId = opId,
                        group = request.group.value,
                        versionId = null,
                        reused = false,
                        ok = false,
                        error = "Operation cancelled",
                    )
                }
                emit(InstallEvent.Finished(OperationResult.Cancelled))
                return@flow
            }
            if (e is CancellationException) throw e
            val isAdmissionRequired = e is io.ltirom.tooling.client.resilience.CommandAdmissionException
            val isConnLost =
                e is io.ltirom.tooling.core.ToolingException ||
                    e is java.net.ConnectException ||
                    e is java.nio.channels.ClosedChannelException ||
                    e is java.io.IOException ||
                    e.message?.contains("Failed to execute command on remote daemon", ignoreCase = true) == true ||
                    e.message?.contains("Stream closed without ExecutionFinished", ignoreCase = true) == true ||
                    e.message?.contains("Stream error on remote daemon", ignoreCase = true) == true ||
                    e.message?.contains("Connection refused", ignoreCase = true) == true ||
                    e.message?.contains("Connection lost", ignoreCase = true) == true ||
                    e.message?.contains("Broken pipe", ignoreCase = true) == true
            val err =
                when {
                    e is ToolErrorException -> {
                        val msg = e.toolError.message
                        val innerConnLost =
                            isConnLost || (
                                msg != null && (
                                    msg.contains("Failed to execute command on remote daemon", ignoreCase = true) ||
                                        msg.contains("Stream closed without ExecutionFinished", ignoreCase = true) ||
                                        msg.contains("Stream error on remote daemon", ignoreCase = true) ||
                                        msg.contains("Connection refused", ignoreCase = true) ||
                                        msg.contains("Connection lost", ignoreCase = true) ||
                                        msg.contains("Broken pipe", ignoreCase = true)
                                    )
                                )
                        if (innerConnLost && e.toolError !is ToolError.ConnectionLost) {
                            ToolError.ConnectionLost(msg ?: "Connection lost")
                        } else {
                            e.toolError
                        }
                    }
                    isAdmissionRequired -> ToolError.AdmissionRequired(e.message ?: "Command admission is closed")
                    isConnLost -> ToolError.ConnectionLost(e.message ?: "Connection lost")
                    else -> ToolError.BuildFailed(e.message ?: "Build failed")
                }
            runCatching {
                writeResultFile(opId, request.group.value, null, reused = false, ok = false, error = err.message)
            }
            emit(InstallEvent.Finished(OperationResult.Failed(err, err.message)))
        } finally {
            cancelledGroups.remove(request.group)
            if (!unconfirmedCancellations.isBlocking(request.group)) {
                activeOperations.remove(request.group)
                activeJobs.remove(request.group)
            }
        }
    }

    private suspend fun writeResultFile(
        opId: String,
        group: String,
        versionId: String?,
        reused: Boolean,
        ok: Boolean = true,
        error: String? = null,
        budgetMs: Long? = null,
    ) = withContext(NonCancellable) {
        val base = toolsBaseDir()
        val jsonPayload =
            buildString {
                append("""{"schema":1,"ok":$ok,"opId":"$opId","group":"$group",""")
                if (versionId != null) append(""""versionId":"$versionId",""")
                if (error != null) append(""""message":${json.encodeToString(String.serializer(), error)},""")
                append(""""reused":$reused}""")
            }
        val resScript = KotlinStoreLayout.buildResultScript(base, opId, jsonPayload)
        if (budgetMs == null) {
            exec(listOf("sh", "-c", resScript)).getOrThrow()
        } else {
            withTimeoutOrNull(budgetMs) { exec(listOf("sh", "-c", resScript)).getOrThrow() }
        }
    }

    override suspend fun result(opId: String): Result<DurableOperationResult?> = try {
        validateOpId(opId)
        val base = toolsBaseDir()
        val res = exec(listOf("cat", "$base/results/$opId.json"))
        if (res.isFailure) {
            val checkScript = "[ -f " + shellQuote("$base/results/$opId.json") + " ]"
            val exists = exec(listOf("sh", "-c", checkScript)).isSuccess
            if (!exists) {
                Result.success(null)
            } else {
                Result.failure(
                    res.exceptionOrNull() ?: ToolErrorException(
                        ToolError.Internal("Failed to read result file for $opId"),
                    ),
                )
            }
        } else {
            val stdout = res.getOrThrow()
            if (stdout.isNotBlank()) {
                val parsed = json.decodeFromString<HelperInstallResult>(stdout)
                if (parsed.ok) {
                    Result.success(DurableOperationResult(parsed.versionId, parsed.opId, parsed.reused))
                } else {
                    val errMsg = parsed.message ?: "Operation $opId failed"
                    Result.failure(ToolErrorException(ToolError.BuildFailed(errMsg)))
                }
            } else {
                Result.failure(ToolErrorException(ToolError.Internal("Result file for $opId is empty")))
            }
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.failure(e)
    }

    override suspend fun cancel(group: ToolGroupId, budgetMs: Long): Result<Unit> = withContext(NonCancellable) {
        validateGroupId(group)
        val opId = activeOperations[group]
        val job = activeJobs[group]
        if (opId == null && job == null) {
            return@withContext Result.success(Unit)
        }
        // One deadline for everything below: this runs uninterruptibly (a cancel must finish its bookkeeping), so
        // each wait is cut to what is left of the budget instead of adding its own full wait.
        val end = System.nanoTime() + budgetMs * NANOS_PER_MS

        fun leftMs() = ((end - System.nanoTime()) / NANOS_PER_MS).coerceAtLeast(0L)
        cancelledGroups.add(group)
        withTimeoutOrNull(leftMs()) { commandRunner.cancelActive(group.value) }
        if (job != null) {
            val joined =
                withTimeoutOrNull(leftMs() * JOIN_SHARE_PERCENT / PERCENT) {
                    job.join()
                    true
                } ?: false
            if (!joined) {
                job.cancel()
                val forceJoined =
                    withTimeoutOrNull(leftMs()) {
                        job.join()
                        true
                    } ?: false
                if (!forceJoined) {
                    return@withContext Result.failure(
                        IllegalStateException("Cancellation has not finished; workspace retained"),
                    )
                }
            }
        }
        (unconfirmedCancellations.recheck(group) as? Recheck.Blocked)?.let { blocked ->
            return@withContext Result.failure(RemoteCancellationUnconfirmedException(blocked.executionId))
        }
        if (opId != null) {
            runCatching {
                writeResultFile(
                    opId,
                    group.value,
                    null,
                    reused = false,
                    ok = false,
                    error = "Operation cancelled",
                    budgetMs = leftMs().coerceAtMost(RESULT_WRITE_BUDGET_MS),
                )
            }
            activeOperations.remove(group, opId)
        }
        if (job != null) activeJobs.remove(group, job)
        Result.success(Unit)
    }

    override suspend fun cancelAll(budgetMs: Long): List<Result<Unit>> = coroutineScope {
        // In parallel: groups are independent, and one overall budget has to cover all of them.
        activeOperations.keys().toList().map { async { cancel(it, budgetMs) } }.awaitAll()
    }

    override suspend fun use(group: ToolGroupId, versionId: String, choice: ChoiceUpdate): Result<GroupLinks> {
        if (activeOperations.containsKey(group)) {
            val err = ToolError.Busy("An operation is already active for ${group.value}")
            return Result.failure(ToolErrorException(err))
        }
        return groupLock(group).withLock { useInternal(group, versionId, choice) }
    }

    private suspend fun useInternal(group: ToolGroupId, versionId: String, choice: ChoiceUpdate): Result<GroupLinks> =
        try {
            validateGroupId(group)
            validateVersionId(versionId)
            val base = toolsBaseDir()
            val groupDir = "$base/bin/${group.value}"
            val targetVerDir = "$groupDir/$versionId"
            val groupDef = catalog.group(group)
            val qTargetVerDir = shellQuote(targetVerDir)
            val qVersionId = shellQuote(versionId)

            val outputsToCheck = groupDef?.outputs ?: emptyList()
            val supportToCheck = groupDef?.allSupportFiles().orEmpty()
            val precheckScript =
                KotlinStoreLayout.buildContainmentScript(qTargetVerDir, shellQuote(groupDir)) + "\n" +
                    KotlinStoreLayout.versionCheck(targetVerDir, outputsToCheck, supportToCheck, probe = true)
            val precheck = exec(listOf("sh", "-c", precheckScript))
            if (precheck.isFailure) {
                val msg = "Target version does not exist, has broken outputs, or escapes tree: $versionId"
                val err = ToolError.UnsupportedLayout(listOf(msg))
                return Result.failure(ToolErrorException(err))
            }

            val sFile = ToolSettingsFile(commandRunner, base)
            // The pick is written before the link moves (a crash in between is reconciled by the next setup,
            // which finds the picked version and switches to it); a failed switch puts the old pick back.
            val previousPick =
                if (choice == ChoiceUpdate.Keep) null else sFile.readSettings().getOrNull()?.picks?.get(group)
            val recordedSource =
                if (choice == ChoiceUpdate.Record) {
                    val vJson = exec(listOf("cat", "$groupDir/$versionId/version.json")).getOrNull()
                    val vData = vJson?.let { runCatching { json.decodeFromString<VersionData>(it) }.getOrNull() }
                    vData?.toDomain(group, groupDef)?.source
                } else {
                    null
                }
            sFile.applyChoice(choice, group, versionId, recordedSource).onFailure { return Result.failure(it) }

            val toolsDDir = shellQuote("${homeDir()}/.ltirom/tools.d")
            val filesCheck =
                KotlinStoreLayout.versionCheck(targetVerDir, outputsToCheck, supportToCheck, probe = false)
            val switchScript =
                KotlinStoreLayout.buildSwitchScript(
                    qTargetVerDir = qTargetVerDir,
                    groupDir = groupDir,
                    qVersionId = qVersionId,
                    checkOuts = filesCheck,
                    groupDef = groupDef,
                    toolsDDir = toolsDDir,
                )
            val res = exec(listOf("sh", "-c", switchScript))
            if (res.isFailure) {
                val restored =
                    if (choice != ChoiceUpdate.Keep) sFile.restore(group, previousPick) else Result.success(Unit)
                val msg =
                    if (restored.isSuccess) {
                        "Failed to switch current link to $versionId"
                    } else {
                        // The next setup would activate the pick that this failed switch was supposed to undo.
                        "Failed to switch current link to $versionId, and the saved pick for ${group.value} could " +
                            "not be put back (${restored.exceptionOrNull()?.message}): settings.json still names " +
                            "$versionId while ${group.value} keeps its previous version. Choose a version again."
                    }
                val err = ToolError.UnsupportedLayout(listOf(msg))
                Result.failure(ToolErrorException(err))
            } else {
                transport.registrationOutcome(groupDef, versionId)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }

    override suspend fun remove(group: ToolGroupId, versionId: String): Result<Unit> = groupLock(group).withLock {
        try {
            validateGroupId(group)
            validateVersionId(versionId)
            if (activeOperations.containsKey(group)) {
                val err = ToolError.Busy("An operation is already active for ${group.value}")
                return Result.failure(ToolErrorException(err))
            }
            val base = toolsBaseDir()
            val sFile = ToolSettingsFile(commandRunner, base)
            // Lenient: another group's damaged pick must not stop this group's removal; only this group's pick matters.
            val settingsRes = sFile.readSettings()
            if (settingsRes.isFailure) {
                return Result.failure(settingsRes.exceptionOrNull()!!)
            }
            val selections = settingsRes.getOrThrow().picks
            if (selections[group]?.first == versionId || group in settingsRes.getOrThrow().unreadable) {
                val err = ToolError.InUse("Cannot remove picked version $versionId")
                return Result.failure(ToolErrorException(err))
            }

            val groupDir = "$base/bin/${group.value}"
            val targetVerDir = "$groupDir/$versionId"
            val qTarget = shellQuote(targetVerDir)
            val qGroup = shellQuote(groupDir)
            val qVersion = shellQuote(versionId)

            val removeScript = KotlinStoreLayout.buildRemoveScript(qGroup, qTarget, qVersion)
            val res = exec(listOf("sh", "-c", removeScript))
            if (res.isFailure) {
                val err = ToolError.InUse("Cannot remove active version or invalid target $versionId")
                Result.failure(ToolErrorException(err))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun paths(catalog: ToolGroupCatalog): Result<PinnedTools> = try {
        val base = toolsBaseDir()
        val pathMap = mutableMapOf<String, String>()
        val verMap = mutableMapOf<String, String>()
        for (group in catalog.allGroups) {
            val groupDir = "$base/bin/${group.id.value}"
            val readlinkRes = exec(listOf("readlink", "$groupDir/current"))
            val currVer = readlinkRes.getOrNull()?.trim().orEmpty()
            if (currVer.isBlank()) {
                throw ToolErrorException(ToolError.NotFound("No selected version for ${group.id.value}"))
            }
            if (currVer.isNotBlank()) {
                validateVersionId(currVer)
                val targetDir = "$groupDir/$currVer"
                val qTarget = shellQuote(targetDir)
                val qGroup = shellQuote(groupDir)
                val containmentCheck = KotlinStoreLayout.buildContainmentScript(qTarget, qGroup)
                val contRes = exec(listOf("sh", "-c", containmentCheck))
                if (contRes.isFailure) {
                    val msg = "Target version directory invalid or escapes layout: $targetDir"
                    throw ToolErrorException(ToolError.UnsupportedLayout(listOf(msg)))
                }
                // The same validator as activation and the setup check: outputs, support files and the probes, so a
                // version damaged after setup passed (a deleted support directory) is not pinned into a new job.
                val intact =
                    KotlinStoreLayout.versionCheck(targetDir, group.outputs, group.allSupportFiles(), probe = true)
                if (exec(listOf("sh", "-c", intact)).isFailure) {
                    val msg = "Installed ${group.id.value} $currVer is damaged (outputs, support files or probes)"
                    throw ToolErrorException(ToolError.UnsupportedLayout(listOf(msg)))
                }
                for (out in group.outputs) {
                    pathMap[out.toolId] = "$targetDir/${out.file}"
                    verMap[out.toolId] = currVer
                }
            }
        }
        Result.success(PinnedTools(paths = pathMap, versions = verMap))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.failure(e)
    }

    private companion object {
        private const val NANOS_PER_MS = 1_000_000L
        private const val JOIN_SHARE_PERCENT = 80L
        private const val PERCENT = 100L
        private const val RESULT_WRITE_BUDGET_MS = 2_000L
        private const val GIT_PREP_ATTEMPTS = 3
        private const val SUBMODULE_MISMATCH_EXIT = 5
        private const val GIT_RETRY_DELAY_MS = 5_000L
    }
}
