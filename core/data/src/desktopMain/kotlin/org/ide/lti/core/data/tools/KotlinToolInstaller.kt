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

import io.ltirom.tooling.client.resilience.CommandAdmissionException
import io.ltirom.tooling.core.RemoteCancellationUnconfirmedException
import io.ltirom.tooling.core.ToolingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.data.tools.KotlinStoreLayout.matches
import org.ide.lti.core.data.tools.KotlinStoreLayout.shellQuote
import org.ide.lti.core.data.tools.KotlinStoreLayout.toData
import org.ide.lti.core.data.tools.KotlinStoreLayout.typeName
import org.ide.lti.core.data.tools.KotlinStoreLayout.validateGroupId
import org.ide.lti.core.data.tools.KotlinStoreLayout.validateOpId
import org.ide.lti.core.data.tools.KotlinStoreLayout.validateRelativePath
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolOutputKind
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.OperationPhase
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolError
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.nio.channels.ClosedChannelException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

internal interface ToolInstallHost {
    val catalog: ToolGroupCatalog
    val json: Json
    val commandRunner: RemoteCommandRunner
    val recipeRunner: KotlinRecipeRunner
    val unconfirmedCancellations: UnconfirmedCancellations
    val activeOperations: ConcurrentHashMap<ToolGroupId, String>
    val activeJobs: ConcurrentHashMap<ToolGroupId, Job>
    val cancelledGroups: MutableSet<ToolGroupId>

    fun ensureNotCancelled(group: ToolGroupId)

    suspend fun homeDir(): String

    suspend fun toolsBaseDir(): String

    suspend fun requiredFreeSpaceKb(group: ToolGroupId): Long

    suspend fun exec(cmd: List<String>, workDir: String? = null, timeoutSeconds: Long = 30L): Result<String>

    suspend fun withGroupLock(group: ToolGroupId, block: suspend () -> Unit)

    suspend fun switchVersion(group: ToolGroupId, versionId: String, choice: ChoiceUpdate): Result<Unit>

    suspend fun writeResultFile(
        opId: String,
        group: String,
        versionId: String?,
        reused: Boolean,
        ok: Boolean = true,
        error: String? = null,
    )
}

internal fun validateRepoUrl(url: String) {
    if (url.isBlank()) {
        throw IllegalArgumentException("Repository URL must not be blank")
    }
    val hasCredentials =
        try {
            URI(url).userInfo != null
        } catch (_: Exception) {
            false
        } || Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://[^/\s@]+:[^/\s@]+@""").containsMatchIn(url) ||
            Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://[^/\s@]+@""").containsMatchIn(url)

    if (hasCredentials) {
        val err = ToolError.UnsupportedLayout(listOf("Repository URL must not contain credentials"))
        throw ToolErrorException(err)
    }
}

internal suspend fun findMissingLayoutPaths(
    catalog: ToolGroupCatalog,
    group: ToolGroupId,
    exists: suspend (String) -> Boolean,
): List<String> {
    val groupDef = catalog.group(group)
    val layoutPaths = groupDef?.layout ?: emptyList()
    val sourceSubdir =
        when (val r = groupDef?.recipe) {
            is RecipeConfig.CMake -> r.sourceSubdir?.trim('/')?.ifBlank { null }
            else -> null
        }
    return layoutPaths.filter { lp ->
        val candidatePaths =
            if (sourceSubdir != null && !lp.startsWith(sourceSubdir)) {
                listOf(lp, "$sourceSubdir/$lp")
            } else {
                listOf(lp)
            }
        candidatePaths.none { cp -> exists(cp) }
    }
}

internal fun isConnectionLostError(e: Exception, extraMessage: String? = null): Boolean = e is ToolingException ||
    e is ConnectException ||
    e is ClosedChannelException ||
    e is IOException ||
    matchesConnLostText(e.message) ||
    matchesConnLostText(extraMessage)

private fun matchesConnLostText(message: String?): Boolean =
    message != null && CONN_LOST_MARKERS.any { message.contains(it, ignoreCase = true) }

private val CONN_LOST_MARKERS = listOf(
    "Failed to execute command on remote daemon",
    "Stream closed without ExecutionFinished",
    "Stream error on remote daemon",
    "Connection refused",
    "Connection lost",
    "Broken pipe",
)

/** Orchestrates validated, cancellable tool-group installation onto the remote store. */
internal class KotlinToolInstaller(private val host: ToolInstallHost) {
    fun install(request: InstallRequest): Flow<InstallEvent> = flow {
        validateRequest(request)
        val base = host.toolsBaseDir()
        val opId = request.opId
        acquireOperationSlot(request.group, opId)
        try {
            emit(InstallEvent.Started(opId))
            emit(InstallEvent.Phase(OperationPhase.Fetching))
            val reqSource = request.source
            if (reqSource is ResolvedSource.Git) {
                validateRepoUrl(reqSource.repoUrl)
            }

            val target = resolveBuildTarget(request, base) ?: return@flow
            if (!checkPrerequisites(request)) return@flow

            val workDir = "$base/work/$opId"
            val srcDir = "$workDir/src"
            val outDir = "$workDir/out"

            prepareWorkspace(request, base, workDir, target.versionId)
            host.ensureNotCancelled(request.group)
            prepareSource(request, base, srcDir)
            if (!validateWorkspaceLayout(request, workDir, srcDir)) return@flow

            host.ensureNotCancelled(request.group)
            executeBuildRecipe(request, srcDir, outDir)

            host.ensureNotCancelled(request.group)
            verifyAndWriteMetadata(request, target.versionId, outDir)

            publishAndSelect(request, base, workDir, outDir, target)
            finalizeInstall(request, workDir, target.versionId)
        } catch (e: Exception) {
            handleInstallFailure(e, request)
        } finally {
            host.cancelledGroups.remove(request.group)
            if (!host.unconfirmedCancellations.isBlocking(request.group)) {
                host.activeOperations.remove(request.group)
                host.activeJobs.remove(request.group)
            }
        }
    }

    private fun validateRequest(request: InstallRequest) {
        validateGroupId(request.group)
        validateOpId(request.opId)
        request.outputs.forEach { validateRelativePath(it.file) }
        request.supportFiles.forEach(::validateRelativePath)
    }

    private suspend fun acquireOperationSlot(group: ToolGroupId, opId: String) {
        when (val blocked = host.unconfirmedCancellations.recheck(group)) {
            is Recheck.Blocked -> throw RemoteCancellationUnconfirmedException(blocked.executionId)

            // The block came from an operation that was left registered; its process is confirmed gone now.
            Recheck.Lifted -> {
                host.activeOperations.remove(group)
                host.activeJobs.remove(group)
            }

            Recheck.Clear -> Unit
        }
        check(host.activeOperations.putIfAbsent(group, opId) == null) {
            "An operation is already active for ${group.value}"
        }
        currentCoroutineContext()[Job]?.let { host.activeJobs[group] = it }
    }

    private suspend fun FlowCollector<InstallEvent>.resolveBuildTarget(
        request: InstallRequest,
        base: String,
    ): BuildTarget? {
        var versionId = KotlinStoreLayout.computeVersionId(request.source, request.label)
        var targetVerDir = "$base/bin/${request.group.value}/$versionId"
        var isRebuild = false
        var reused = false

        if (readVersionData(targetVerDir) != null) {
            val firstProbe = probeAndReuseIfUsable(request, targetVerDir, versionId)
            if (firstProbe.reused) {
                reused = true
            } else if (firstProbe.rebuild) {
                isRebuild = true
            } else {
                // Disambiguate if the existing folder holds other code (commit, repository or submodules)
                // under the same name; the whole source identity goes into the name, so two forks at one
                // commit get two folders.
                val suffix = KotlinStoreLayout.disambiguator(request.source)
                if (!versionId.endsWith("-$suffix")) {
                    versionId = "$versionId-$suffix"
                    targetVerDir = "$base/bin/${request.group.value}/$versionId"
                    val secondProbe = probeAndReuseIfUsable(request, targetVerDir, versionId)
                    if (secondProbe.reused) {
                        reused = true
                    } else if (secondProbe.rebuild || secondProbe.sameSource) {
                        isRebuild = true
                    }
                }
            }
        }
        if (request.forceNew) isRebuild = true
        return if (reused) null else BuildTarget(versionId, targetVerDir, isRebuild)
    }

    private suspend fun readVersionData(dir: String): VersionData? {
        val catJson = host.exec(listOf("cat", "$dir/version.json")).getOrNull()?.takeIf { it.isNotBlank() }
        return catJson?.let { runCatchingCancellable { host.json.decodeFromString<VersionData>(it) }.getOrNull() }
    }

    private suspend fun FlowCollector<InstallEvent>.probeAndReuseIfUsable(
        request: InstallRequest,
        dir: String,
        versionId: String,
    ): VersionProbeResult {
        val existing = readVersionData(dir)
            ?: return VersionProbeResult(reused = false, rebuild = false, sameSource = false)
        val sameSource = existing.source.matches(request.source.toData())
        val sameRecipe =
            existing.recipe.type == request.recipe.typeName() &&
                existing.recipe.revision == request.recipe.revision
        val usable = sameSource && sameRecipe && !request.forceNew &&
            host.exec(
                listOf(
                    "sh",
                    "-c",
                    KotlinStoreLayout.versionCheck(dir, request.outputs, request.supportFiles, probe = true),
                ),
            ).isSuccess
        if (usable) {
            if (request.select) {
                host.withGroupLock(request.group) {
                    host.ensureNotCancelled(request.group)
                    host.switchVersion(request.group, versionId, request.choice).getOrThrow()
                }
            }
            emit(InstallEvent.Finished(OperationResult.Reused(versionId)))
        }
        return VersionProbeResult(
            reused = usable,
            rebuild = !usable && sameSource && sameRecipe,
            sameSource = sameSource,
        )
    }

    private suspend fun FlowCollector<InstallEvent>.checkPrerequisites(request: InstallRequest): Boolean {
        val err = checkFreeSpaceError(request.group) ?: checkMissingPackagesError(request.packages)
        if (err != null) {
            emit(InstallEvent.Finished(OperationResult.Failed(err, err.message)))
        }
        return err == null
    }

    private suspend fun checkFreeSpaceError(group: ToolGroupId): ToolError? {
        val requiredKb = host.requiredFreeSpaceKb(group)
        val home = host.homeDir()
        val lines = if (requiredKb > 0) host.exec(listOf("df", "-Pk", home)).getOrNull()?.lines() else null
        val availKb = lines?.drop(1)?.firstOrNull()?.trim()?.split(Regex("""\s+"""))?.getOrNull(3)?.toLongOrNull()
        return if (requiredKb > 0 && availKb != null && availKb < requiredKb) {
            ToolError.DiskFull("Required $requiredKb KB, but only $availKb KB available on $home")
        } else {
            null
        }
    }

    private suspend fun checkMissingPackagesError(packages: List<String>): ToolError? {
        if (packages.isEmpty()) return null
        val missing = packages.filter { pkg ->
            host.exec(listOf("sh", "-c", KotlinStoreLayout.buildPackageCheck(pkg))).isFailure
        }
        return if (missing.isNotEmpty()) ToolError.MissingPackages(missing) else null
    }

    private suspend fun FlowCollector<InstallEvent>.prepareWorkspace(
        request: InstallRequest,
        base: String,
        workDir: String,
        versionId: String,
    ) {
        emit(InstallEvent.Output("Preparing build workspace at $workDir"))
        val opJsonPayload =
            buildJsonObject {
                put("group", request.group.value)
                put("versionId", versionId)
                put("startedAt", Instant.now().toString())
            }.toString()
        val prepWorkspaceScript =
            KotlinStoreLayout.buildPrepWorkspaceScript(
                baseDir = base,
                group = request.group.value,
                opId = request.opId,
                workDir = workDir,
                srcDir = "$workDir/src",
                outDir = "$workDir/out",
                opJsonPayload = opJsonPayload,
            )
        host.exec(listOf("sh", "-c", prepWorkspaceScript)).getOrThrow()
    }

    private suspend fun FlowCollector<InstallEvent>.prepareSource(
        request: InstallRequest,
        base: String,
        srcDir: String,
    ) {
        when (val src = request.source) {
            is ResolvedSource.Git -> prepareGitSource(request.group, base, srcDir, src)
            is ResolvedSource.Release -> emit(InstallEvent.Output("Preparing release download..."))
        }
    }

    private suspend fun FlowCollector<InstallEvent>.prepareGitSource(
        group: ToolGroupId,
        base: String,
        srcDir: String,
        src: ResolvedSource.Git,
    ) {
        emit(InstallEvent.Output("Extracting source commit ${src.commit.take(COMMIT_PREFIX_LEN)}..."))
        val gitPrepScript =
            KotlinStoreLayout.buildGitPrepScript(
                base = base,
                group = group.value,
                repoUrl = src.repoUrl,
                commit = src.commit,
                srcDir = srcDir,
                submodules = src.submodules,
            )
        val streamEnv = mapOf("LTI_TOOL_GROUP" to group.value)
        // Retried: the mirror and the submodule cache keep what each attempt fetched, so a retry
        // continues instead of starting over. Exit 5 (submodule at the wrong commit) is final.
        var attempt = 1
        while (true) {
            var exitCode = 0
            var summary: String? = null
            host.commandRunner.stream(
                script = gitPrepScript,
                category = CommandCategory.NETWORK,
                // ponytail: wall-clock limit per attempt; an idle-output limit suits slow links better.
                timeoutSeconds = CommandCategory.BUILD.defaultTimeoutSeconds,
                cwd = host.homeDir(),
                env = streamEnv,
                onFinished = { exitCode = it },
                onSummary = { summary = it },
            ).collect { emit(InstallEvent.Output(it)) }
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

    private suspend fun FlowCollector<InstallEvent>.validateWorkspaceLayout(
        request: InstallRequest,
        workDir: String,
        srcDir: String,
    ): Boolean {
        val missingLayout =
            findMissingLayoutPaths(host.catalog, request.group) { cp ->
                host.exec(listOf("sh", "-c", "[ -e " + shellQuote("$srcDir/$cp") + " ]")).isSuccess
            }
        if (missingLayout.isEmpty()) return true
        val err = ToolError.UnsupportedLayout(missingLayout)
        runCatchingCancellable {
            host.writeResultFile(
                request.opId,
                request.group.value,
                versionId = null,
                reused = false,
                ok = false,
                error = err.message,
            )
            host.exec(listOf("rm", "-rf", "--", workDir))
        }
        emit(InstallEvent.Finished(OperationResult.Failed(err, err.message)))
        return false
    }

    private suspend fun FlowCollector<InstallEvent>.executeBuildRecipe(
        request: InstallRequest,
        srcDir: String,
        outDir: String,
    ) {
        emit(InstallEvent.Phase(OperationPhase.Building))
        emit(InstallEvent.Output("Running build recipe: ${request.recipe::class.simpleName}..."))
        host.recipeRunner.executeRecipe(
            srcDir = srcDir,
            outDir = outDir,
            source = request.source,
            recipe = request.recipe,
            outputs = request.outputs,
            supportFiles = request.supportFiles,
            onLog = { emit(InstallEvent.Output(it)) },
            groupId = request.group.value,
        ).getOrThrow()
    }

    private suspend fun FlowCollector<InstallEvent>.verifyAndWriteMetadata(
        request: InstallRequest,
        versionId: String,
        outDir: String,
    ) {
        emit(InstallEvent.Phase(OperationPhase.Verifying))
        emit(InstallEvent.Output("Verifying build outputs..."))
        for (out in request.outputs) {
            emit(InstallEvent.Output("Running probe for ${out.toolId}: ${out.probe.args.joinToString(" ")}"))
            val ok =
                host.exec(
                    listOf(
                        "sh",
                        "-c",
                        KotlinStoreLayout.versionCheck(outDir, listOf(out), request.supportFiles, probe = true),
                    ),
                ).isSuccess
            if (!ok) throw ToolErrorException(ToolError.VerifyFailed(out.toolId))
        }

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
        val verJson = host.json.encodeToString(VersionData.serializer(), verData)
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
        host.exec(listOf("sh", "-c", writeVerScript)).getOrThrow()
    }

    private suspend fun FlowCollector<InstallEvent>.publishAndSelect(
        request: InstallRequest,
        base: String,
        workDir: String,
        outDir: String,
        target: BuildTarget,
    ) {
        emit(InstallEvent.Phase(OperationPhase.Publishing))
        emit(InstallEvent.Output("Publishing version ${target.versionId}..."))
        val publishScript =
            KotlinStoreLayout.buildPublishScript(
                groupDir = "$base/bin/${request.group.value}",
                targetDir = target.targetDir,
                outDir = outDir,
                workDir = if (target.isRebuild) workDir else null,
            )
        host.withGroupLock(request.group) {
            // The last point to stop: past it the version is published and (if asked) active.
            host.ensureNotCancelled(request.group)
            host.exec(listOf("sh", "-c", publishScript)).getOrThrow()
            if (request.select) {
                host.ensureNotCancelled(request.group)
                emit(InstallEvent.Phase(OperationPhase.Switching))
                host.switchVersion(request.group, target.versionId, request.choice).getOrThrow()
            }
        }
    }

    private suspend fun FlowCollector<InstallEvent>.finalizeInstall(
        request: InstallRequest,
        workDir: String,
        versionId: String,
    ) {
        // The version is published (and active if asked): from here on nothing may turn this into a failure.
        // Bookkeeping that doesn't succeed is a warning. A workspace left behind stays in work/ (nothing sweeps it;
        // with a result file it is not reported as interrupted either), which costs disk, not correctness.
        try {
            host.writeResultFile(request.opId, request.group.value, versionId, reused = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(InstallEvent.Output("Warning: $versionId is installed, its result file was not written"))
        }
        if (host.exec(listOf("rm", "-rf", "--", workDir)).isFailure) {
            emit(InstallEvent.Output("Warning: $versionId is installed; workspace $workDir was not removed"))
        }
        emit(InstallEvent.Finished(OperationResult.Installed(versionId)))
    }

    private suspend fun FlowCollector<InstallEvent>.handleInstallFailure(e: Exception, request: InstallRequest) {
        val wasCancelled = host.cancelledGroups.remove(request.group)
        if (e is RemoteCancellationUnconfirmedException) {
            host.unconfirmedCancellations.record(request.group, e.executionId)
            throw e
        }
        if (wasCancelled) {
            runCatchingCancellable {
                host.writeResultFile(
                    opId = request.opId,
                    group = request.group.value,
                    versionId = null,
                    reused = false,
                    ok = false,
                    error = "Operation cancelled",
                )
            }
            emit(InstallEvent.Finished(OperationResult.Cancelled))
            return
        }
        if (e is CancellationException) throw e
        val isAdmissionRequired = e is CommandAdmissionException
        val isConnLost = isConnectionLostError(e)
        val err =
            when {
                e is ToolErrorException -> {
                    val msg = e.toolError.message
                    if (isConnectionLostError(e, msg) && e.toolError !is ToolError.ConnectionLost) {
                        ToolError.ConnectionLost(msg)
                    } else {
                        e.toolError
                    }
                }

                isAdmissionRequired -> ToolError.AdmissionRequired(e.message ?: "Command admission is closed")

                isConnLost -> ToolError.ConnectionLost(e.message ?: "Connection lost")

                else -> ToolError.BuildFailed(e.message ?: "Build failed")
            }
        runCatchingCancellable {
            host.writeResultFile(
                request.opId,
                request.group.value,
                versionId = null,
                reused = false,
                ok = false,
                error = err.message,
            )
        }
        emit(InstallEvent.Finished(OperationResult.Failed(err, err.message)))
    }

    private class BuildTarget(val versionId: String, val targetDir: String, val isRebuild: Boolean)

    private class VersionProbeResult(val reused: Boolean, val rebuild: Boolean, val sameSource: Boolean)

    private companion object {
        const val COMMIT_PREFIX_LEN = 7
        const val GIT_PREP_ATTEMPTS = 3
        const val SUBMODULE_MISMATCH_EXIT = 5
        const val GIT_RETRY_DELAY_MS = 5_000L
    }
}
