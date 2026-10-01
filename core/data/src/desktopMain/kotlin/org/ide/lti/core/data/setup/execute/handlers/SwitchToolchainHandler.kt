package org.ide.lti.core.data.setup.execute.handlers

import io.ltirom.tooling.client.wsl.WslCliExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.ide.lti.core.data.setup.AptCommandBuilder
import org.ide.lti.core.data.setup.RequirementCatalog
import org.ide.lti.core.data.setup.ToolVerifier
import org.ide.lti.core.data.setup.execute.ExecutionContext
import org.ide.lti.core.data.setup.execute.PlanActionHandler
import org.ide.lti.core.data.setup.execute.actionId
import org.ide.lti.core.data.setup.install.ActivationReconciler
import org.ide.lti.core.data.setup.install.ArtifactFileEntry
import org.ide.lti.core.data.setup.install.ArtifactManifest
import org.ide.lti.core.data.setup.install.ArtifactRecipeInfo
import org.ide.lti.core.data.setup.install.ArtifactSourceInfo
import org.ide.lti.core.data.setup.install.ArtifactStore
import org.ide.lti.core.data.setup.install.InstallationManager
import org.ide.lti.core.data.setup.install.detectHostArch
import org.ide.lti.core.data.setup.recipe.BuildRecipeDispatcher
import org.ide.lti.core.data.setup.recipe.RecipeContext
import org.ide.lti.core.data.setup.recipe.RecipeResult
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolGroup
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.UserRepairHandoff
import org.ide.lti.core.domain.setup.ports.ActivationOutcome
import org.ide.lti.core.domain.setup.ports.ActivationRequest
import org.ide.lti.core.domain.setup.ports.ArtifactId
import org.ide.lti.core.domain.setup.ports.InstallId
import org.ide.lti.core.domain.setup.ports.ToolchainInstallationPort
import org.ide.lti.core.model.setup.PersistedPlanAction
import org.ide.lti.core.model.setup.PersistedSetupPlan
import org.ide.lti.core.model.setup.PersistedUserHandoff
import org.ide.lti.core.model.setup.SwitchCheckpoint
import java.io.File
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true }

@Suppress("ReturnCount", "LoopWithTooManyJumpStatements")
public class SwitchToolchainHandler(
    private val artifactStore: ArtifactStore,
    private val installationManager: InstallationManager,
    private val installationPort: ToolchainInstallationPort,
    private val toolVerifier: ToolVerifier = ToolVerifier(WslCliExecutor()),
    private val recipeDispatcher: BuildRecipeDispatcher = BuildRecipeDispatcher(),
    private val missingPackageProber: (
        suspend (distro: String, needed: List<String>, cli: WslCliExecutor?) -> List<String>
    )? = null,
    private val repository: ToolchainSetupRepository? = null,
    reconciler: ActivationReconciler? = null,
) : PlanActionHandler<SetupPlanAction.SwitchToolchain> {
    private val explicitReconciler: ActivationReconciler? = reconciler
    private val reconciler: ActivationReconciler = reconciler ?: ActivationReconciler(installationPort, repository)

    private fun getEffectiveReconciler(context: ExecutionContext): ActivationReconciler {
        if (explicitReconciler != null) return explicitReconciler
        val repo = context.repository ?: repository
        return ActivationReconciler(installationPort, repo)
    }

    override suspend fun execute(action: SetupPlanAction.SwitchToolchain, context: ExecutionContext): SetupOutcome? {
        var outcome: SetupOutcome? = null
        run {
            context.setStepStatus(SetupStepStage.TOOLCHAIN_COMPILATION, StepStatus.RUNNING, null, null, null, null)
            validateFrozenInputs(action.inputs)?.let {
                outcome = handleFailure(context, it)
                return@run
            }

            val cli = context.serviceCli ?: toolVerifier.cli
            val toolchainVersions = probeToolchainVersions(context.distro, cli)

            checkMissingPackages(action, context, toolchainVersions)?.let {
                outcome = it
                return@run
            }
            val artifacts = resolveOrBuildArtifacts(action, context, toolchainVersions).getOrElse {
                outcome = handleFailure(context, it.message ?: "Artifact preparation failed")
                return@run
            }
            val candidateInstallId = assembleCandidate(artifacts).getOrElse {
                outcome = handleFailure(context, it.message ?: "Candidate assembly failed")
                return@run
            }
            verifyCandidate(candidateInstallId, context)?.let {
                outcome = handleFailure(context, it)
                return@run
            }
            val rec = getEffectiveReconciler(context)
            val cpVerify = rec.recordCheckpoint(
                requestId = "pre-activation",
                expectedActive = installationPort.state().activeInstallId?.value,
                target = candidateInstallId.value,
                revision = action.selectionRevision,
                checkpoint = SwitchCheckpoint.CANDIDATE_VERIFIED,
            )
            if (cpVerify.isFailure) {
                outcome = handleFailure(
                    context,
                    "Failed to record candidate verified checkpoint: ${cpVerify.exceptionOrNull()?.message}",
                )
                return@run
            }
            activateCandidate(action, candidateInstallId, context)?.let {
                outcome = it
                return@run
            }
            outcome = commitInstalled(candidateInstallId, context)
            rec.clearPendingSwitch()
        }
        return outcome
    }

    private suspend fun checkMissingPackages(
        action: SetupPlanAction.SwitchToolchain,
        context: ExecutionContext,
        toolchainVersions: Map<String, String>,
    ): SetupOutcome? {
        val neededPackages = ToolGroupCatalog.DEFAULT_GROUPS.filter { group ->
            val input = action.inputs.getValue(group.id)
            val fingerprint = computeInputFingerprint(group, input, toolchainVersions)
            group.id in action.forceRebuildGroups ||
                findReusableArtifact(group, fingerprint, context) == null
        }.flatMap { it.buildPackages }.distinct()

        if (neededPackages.isEmpty()) return null

        val missing = when {
            missingPackageProber != null -> missingPackageProber.invoke(
                context.distro,
                neededPackages,
                context.serviceCli,
            )
            context.serviceCli != null -> probePackagesViaDpkg(context.distro, neededPackages, context.serviceCli)
            else -> emptyList()
        }
        return if (missing.isNotEmpty()) createPackageHandoffOutcome(action, context, missing) else null
    }

    private suspend fun createPackageHandoffOutcome(
        action: SetupPlanAction.SwitchToolchain,
        context: ExecutionContext,
        missing: List<String>,
    ): SetupOutcome {
        val handoffPackages = missing.filter { RequirementCatalog.isPackageApproved(it) }
        val terminalCommand = AptCommandBuilder.install(handoffPackages)
        val handoff = UserRepairHandoff(
            actionId = action.actionId(),
            description = "Install missing packages required to build toolchain: ${handoffPackages.joinToString(" ")}",
            terminalCommand = terminalCommand,
            packages = handoffPackages,
            distro = context.distro,
        )
        persistHandoffState(action, context, handoffPackages, terminalCommand)
        context.setStepStatus(
            SetupStepStage.TOOLCHAIN_COMPILATION,
            StepStatus.WARNING,
            "Awaiting external authorization in WSL terminal: $terminalCommand",
            null,
            null,
            null,
        )
        return SetupOutcome.AwaitingUserAction(
            pendingPlanId = context.plan.planId,
            stage = SetupStepStage.TOOLCHAIN_COMPILATION,
            reason = "Missing required build packages: ${handoffPackages.joinToString(" ")}",
            handoff = handoff,
        )
    }

    private suspend fun persistHandoffState(
        action: SetupPlanAction.SwitchToolchain,
        context: ExecutionContext,
        packages: List<String>,
        command: String,
    ) {
        val persistedHandoff = PersistedUserHandoff(
            actionId = action.actionId(),
            distro = context.distro,
            command = command,
            packages = packages,
            timestampEpochMs = System.currentTimeMillis(),
        )
        val persistedPlan = PersistedSetupPlan(
            planId = context.plan.planId,
            revisionHash = context.plan.revisionHash,
            environmentKey = context.plan.environmentKey,
            kind = context.plan.kind.name,
            targetStageOrToolId = context.plan.targetStageOrToolId,
            autoDoctorEnabled = context.plan.autoDoctorEnabled,
            actions = context.plan.orderedActions.map { act ->
                PersistedPlanAction(act.actionId(), "SWITCH_TOOLCHAIN", action.inputs.keys.map { it.value })
            },
        )
        context.repository?.recordAttemptAwaitingUserAction(
            context.journal?.attemptId ?: "attempt",
            persistedHandoff,
            persistedPlan,
        )
    }

    private fun findReusableArtifact(
        group: ToolGroup,
        fingerprint: String,
        context: ExecutionContext,
    ): ArtifactManifest? {
        if (artifactStore.rootDir != null) {
            return findReusableLocalArtifact(group, fingerprint)
        }
        return findReusableWslArtifact(group, fingerprint, context)
    }

    private fun findReusableLocalArtifact(group: ToolGroup, fingerprint: String): ArtifactManifest? {
        val rootDir = artifactStore.rootDir ?: return null
        val groupDir = File(rootDir, group.id.value)
        if (!groupDir.exists()) return null
        val subdirs = groupDir.listFiles { f -> f.isDirectory } ?: return null
        for (dir in subdirs) {
            if (File(dir, "unusable.json").exists()) continue
            val manifest = artifactStore.getManifest(group.id.value, dir.name)
            if (manifest?.fingerprint == fingerprint) {
                return manifest
            }
        }
        return null
    }

    private fun findReusableWslArtifact(
        group: ToolGroup,
        fingerprint: String,
        context: ExecutionContext,
    ): ArtifactManifest? {
        val cli = context.serviceCli ?: toolVerifier.cli
        val homeRes = cli.execute(context.distro, listOf("printenv", "HOME"))
        if (homeRes.exitCode != 0) return null
        val home = homeRes.output.trim()
        val wslArtGroup = "$home/LtiRomTools/artifacts/${group.id.value}"

        val existsRes = cli.execute(context.distro, listOf("test", "-d", wslArtGroup))
        if (existsRes.exitCode != 0) return null

        val lsRes = cli.execute(context.distro, listOf("ls", "-1", wslArtGroup))
        val subdirs = if (lsRes.exitCode == 0) {
            lsRes.output.lines().map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        return subdirs.firstNotNullOfOrNull { dir ->
            validateWslCandidate(group, "$wslArtGroup/$dir", fingerprint, context, cli)
        }
    }

    private fun validateWslCandidate(
        group: ToolGroup,
        dirPath: String,
        fingerprint: String,
        context: ExecutionContext,
        cli: WslCliExecutor,
    ): ArtifactManifest? {
        val unusableCheck = cli.execute(context.distro, listOf("test", "-f", "$dirPath/unusable.json"))
        if (unusableCheck.exitCode == 0) return null

        val catRes = cli.execute(context.distro, listOf("cat", "$dirPath/manifest.json"))
        if (catRes.exitCode != 0 || catRes.output.isBlank()) return null

        val manifest = runCatching { json.decodeFromString<ArtifactManifest>(catRes.output) }.getOrNull()
        if (manifest?.fingerprint != fingerprint) return null

        val intact = manifest.files.all { file ->
            val sumRes = cli.execute(context.distro, listOf("sha256sum", "$dirPath/${file.path}"))
            sumRes.exitCode == 0 && sumRes.output.trim().substringBefore(" ") == file.sha256
        }
        return if (intact) {
            manifest
        } else {
            artifactStore.markUnusable(group.id.value, manifest.artifactId, "Checksum mismatch")
            null
        }
    }

    private suspend fun resolveOrBuildArtifacts(
        action: SetupPlanAction.SwitchToolchain,
        context: ExecutionContext,
        toolchainVersions: Map<String, String>,
    ): Result<Map<ToolGroupId, ArtifactId>> {
        val artifacts = mutableMapOf<ToolGroupId, ArtifactId>()
        val cli = context.serviceCli ?: toolVerifier.cli
        val publisher = WslArtifactPublisher(cli, context.distro, json)

        for (group in ToolGroupCatalog.DEFAULT_GROUPS) {
            val input = action.inputs.getValue(group.id)
            val fingerprint = computeInputFingerprint(group, input, toolchainVersions)

            val reusable = if (group.id in action.forceRebuildGroups) {
                null
            } else {
                findReusableArtifact(group, fingerprint, context)
            }

            if (reusable != null) {
                context.appendLog("[Switch] Reusing artifact for ${group.id.value}: ${reusable.artifactId}")
                artifacts[group.id] = ArtifactId(reusable.artifactId)
                continue
            }

            val built = buildGroupArtifact(group, input, fingerprint, context, publisher, cli)
            val buildException = built.exceptionOrNull()
            if (buildException != null) return Result.failure(buildException)
            artifacts[group.id] = ArtifactId(built.getOrThrow().artifactId)
        }
        return Result.success(artifacts)
    }

    private suspend fun buildGroupArtifact(
        group: ToolGroup,
        input: ResolvedInput,
        fingerprint: String,
        context: ExecutionContext,
        publisher: WslArtifactPublisher,
        cli: WslCliExecutor,
    ): Result<ArtifactManifest> {
        val rootDir = artifactStore.rootDir
        return if (rootDir != null) {
            buildLocalGroupArtifact(rootDir, group, input, fingerprint, context, cli)
        } else {
            buildWslGroupArtifact(group, input, fingerprint, context, publisher, cli)
        }
    }

    private suspend fun buildLocalGroupArtifact(
        rootDir: File,
        group: ToolGroup,
        input: ResolvedInput,
        fingerprint: String,
        context: ExecutionContext,
        cli: WslCliExecutor,
    ): Result<ArtifactManifest> {
        val attemptId = UUID.randomUUID().toString()
        val tmpBuild = File(rootDir, ".tmp-build-${group.id.value}-$attemptId")
        val tmpBin = File(tmpBuild, "bin").apply { mkdirs() }
        return try {
            val ctx = RecipeContext(
                distro = context.distro,
                extDir = context.extDir,
                binDir = tmpBin.absolutePath,
                group = group,
                cli = cli,
                repository = context.repository,
                transport = context.transport,
                journal = context.journal,
                log = { context.appendLog(it) },
            )
            when (val res = recipeDispatcher.dispatch(ctx, group.recipe)) {
                is RecipeResult.Built -> {
                    val files = tmpBin.listFiles()?.filter { it.isFile }?.map { f ->
                        ArtifactFileEntry(path = "bin/${f.name}", sha256 = ArtifactStore.sha256(f), mode = "755")
                    } ?: emptyList()
                    val contentId = ArtifactStore.computeContentId(fingerprint, files)
                    val artifactId = ArtifactStore.computeArtifactId(contentId, attemptId)
                    val sourceInfo = when (input) {
                        is ResolvedInput.Git -> ArtifactSourceInfo(
                            kind = "git",
                            repoUrl = input.repoUrl,
                            commit = input.commit,
                            submodules = input.submoduleCommits,
                        )
                        is ResolvedInput.Release -> ArtifactSourceInfo(
                            kind = "release",
                            version = input.version,
                            sha256 = input.sha256,
                        )
                    }
                    val template = ArtifactManifest(
                        schema = 1,
                        group = group.id.value,
                        artifactId = artifactId,
                        fingerprint = fingerprint,
                        source = sourceInfo,
                        recipe = ArtifactRecipeInfo(
                            type = group.recipe::class.simpleName ?: "Recipe",
                            revision = 1,
                        ),
                        arch = detectHostArch(),
                        files = files,
                        outputs = group.outputs.map { it.file },
                        verifiedAt = System.currentTimeMillis(),
                    )
                    val published = artifactStore.publish(group.id.value, tmpBuild, template, attemptId)
                    Result.success(published)
                }
                is RecipeResult.Failed -> Result.failure(IllegalStateException("Build failed: ${res.reason}"))
                is RecipeResult.Unsupported -> Result.failure(
                    IllegalStateException("Unsupported layout: ${res.missing}"),
                )
            }
        } finally {
            tmpBuild.deleteRecursively()
        }
    }

    private suspend fun buildWslGroupArtifact(
        group: ToolGroup,
        input: ResolvedInput,
        fingerprint: String,
        context: ExecutionContext,
        publisher: WslArtifactPublisher,
        cli: WslCliExecutor,
    ): Result<ArtifactManifest> {
        val homeRes = cli.execute(context.distro, listOf("printenv", "HOME"))
        if (homeRes.exitCode != 0) return Result.failure(IllegalStateException("Failed to get HOME"))
        val home = homeRes.output.trim()
        val wslArtifactsRoot = "$home/LtiRomTools/artifacts"
        val attemptId = UUID.randomUUID().toString()
        val wslTmpBuild = "$wslArtifactsRoot/.tmp-build-${group.id.value}-$attemptId"
        val wslTmpBin = "$wslTmpBuild/bin"
        val wslTmpSrc = "$wslTmpBuild/src"

        val mkRes = cli.execute(context.distro, listOf("mkdir", "-p", wslTmpBin))
        if (mkRes.exitCode != 0) {
            return Result.failure(IllegalStateException("Failed to create build dir $wslTmpBin: ${mkRes.error}"))
        }

        val effectiveExtDir = if (input is ResolvedInput.Git) {
            prepareWslSourceWorkspace(cli, context.distro, context.extDir, wslTmpSrc, group, input)
        } else {
            context.extDir
        }

        return try {
            val ctx = RecipeContext(
                distro = context.distro,
                extDir = effectiveExtDir,
                binDir = wslTmpBin,
                group = group,
                cli = cli,
                repository = context.repository ?: repository,
                transport = context.transport,
                journal = context.journal,
                resolvedInput = input,
                log = { context.appendLog(it) },
            )
            when (val res = recipeDispatcher.dispatch(ctx, group.recipe)) {
                is RecipeResult.Built -> publisher.publishCandidate(group, wslTmpBuild, wslTmpBin, input, fingerprint)
                is RecipeResult.Failed -> Result.failure(IllegalStateException("Build failed: ${res.reason}"))
                is RecipeResult.Unsupported -> Result.failure(
                    IllegalStateException("Unsupported layout: ${res.missing}"),
                )
            }
        } finally {
            cli.execute(context.distro, listOf("rm", "-rf", wslTmpBuild))
        }
    }

    private fun prepareWslSourceWorkspace(
        cli: WslCliExecutor,
        distro: String,
        extDir: String,
        wslTmpSrc: String,
        group: ToolGroup,
        input: ResolvedInput.Git,
    ): String {
        cli.execute(distro, listOf("mkdir", "-p", wslTmpSrc))
        val localSource = "$extDir/${group.id.value}"
        val wslGroupSrc = "$wslTmpSrc/${group.id.value}"
        val repoUrl = input.repoUrl
        if (repoUrl != null) {
            val r = cli.execute(distro, listOf("git", "clone", repoUrl, wslGroupSrc))
            if (r.exitCode != 0 && cli.execute(distro, listOf("test", "-d", localSource)).exitCode == 0) {
                cli.execute(distro, listOf("git", "clone", localSource, wslGroupSrc))
            }
        } else {
            if (cli.execute(distro, listOf("test", "-d", localSource)).exitCode == 0) {
                cli.execute(distro, listOf("git", "clone", localSource, wslGroupSrc))
            }
        }

        if (cli.execute(distro, listOf("test", "-d", wslGroupSrc)).exitCode == 0) {
            cli.execute(distro, listOf("git", "-C", wslGroupSrc, "checkout", input.commit))
            if (input.submoduleCommits.isNotEmpty()) {
                cli.execute(
                    distro,
                    listOf("git", "-C", wslGroupSrc, "submodule", "update", "--init", "--recursive"),
                )
                for ((subPath, subCommit) in input.submoduleCommits) {
                    cli.execute(distro, listOf("git", "-C", "$wslGroupSrc/$subPath", "checkout", subCommit))
                }
            }
            return wslTmpSrc
        }
        return extDir
    }

    private suspend fun assembleCandidate(artifacts: Map<ToolGroupId, ArtifactId>): Result<InstallId> = try {
        Result.success(installationPort.assembleCandidate(artifacts))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun verifyCandidate(installId: InstallId, context: ExecutionContext): String? {
        val verifyResults = installationManager.verifyCandidate(context.distro, installId.value, toolVerifier)
        val failed = verifyResults.filter { !it.isSuccess }
        if (failed.isNotEmpty()) {
            return "Tools failed verification: ${failed.map { it.toolId }}"
        }
        return null
    }

    private suspend fun activateCandidate(
        action: SetupPlanAction.SwitchToolchain,
        installId: InstallId,
        context: ExecutionContext,
    ): SetupOutcome? {
        val rec = getEffectiveReconciler(context)
        val currentState = installationPort.state()
        val requestId = UUID.randomUUID().toString()
        val cp1 = rec.recordCheckpoint(
            requestId = requestId,
            expectedActive = currentState.activeInstallId?.value,
            target = installId.value,
            revision = action.selectionRevision,
            checkpoint = SwitchCheckpoint.ACTIVATION_REQUESTED,
        )
        if (cp1.isFailure) {
            return SetupOutcome.Failed(
                SetupStepStage.TOOLCHAIN_COMPILATION.name,
                "Failed to record checkpoint: ${cp1.exceptionOrNull()?.message}",
            )
        }
        val req = ActivationRequest(
            requestId = requestId,
            expectedActiveInstallId = currentState.activeInstallId,
            targetInstallId = installId,
        )
        return when (val outcome = rec.reconcile(req)) {
            is ActivationOutcome.Committed -> {
                val cp2 = rec.recordCheckpoint(
                    requestId = requestId,
                    expectedActive = currentState.activeInstallId?.value,
                    target = installId.value,
                    revision = action.selectionRevision,
                    checkpoint = SwitchCheckpoint.ACTIVATION_COMMITTED,
                )
                if (cp2.isFailure) {
                    return SetupOutcome.Failed(
                        SetupStepStage.TOOLCHAIN_COMPILATION.name,
                        "Failed to record checkpoint: ${cp2.exceptionOrNull()?.message}",
                    )
                }
                rec.clearPendingSwitch()
                null
            }
            is ActivationOutcome.Blocked -> SetupOutcome.Failed(
                SetupStepStage.TOOLCHAIN_COMPILATION.name,
                "Activation blocked by running jobs (${outcome.activeWork})",
            )
            is ActivationOutcome.Conflict -> SetupOutcome.Failed(
                SetupStepStage.TOOLCHAIN_COMPILATION.name,
                "Activation conflict with active install",
            )
            is ActivationOutcome.VerifyFailedRestored -> SetupOutcome.Failed(
                SetupStepStage.TOOLCHAIN_COMPILATION.name,
                "Verification failed on server: ${outcome.reason}",
            )
            is ActivationOutcome.MaintenanceFailed -> SetupOutcome.Failed(
                SetupStepStage.TOOLCHAIN_COMPILATION.name,
                "Maintenance failed during restore",
            )
            else -> SetupOutcome.Failed(SetupStepStage.TOOLCHAIN_COMPILATION.name, "Activation failed: $outcome")
        }
    }

    private fun commitInstalled(installId: InstallId, context: ExecutionContext): SetupOutcome? {
        context.setStepStatus(
            SetupStepStage.TOOLCHAIN_COMPILATION,
            StepStatus.SUCCESS,
            "Toolchain active: ${installId.value}",
            null,
            null,
            null,
        )
        context.appendLog("[Switch] Toolchain successfully switched to ${installId.value}")
        return null
    }

    private fun handleFailure(context: ExecutionContext, reason: String): SetupOutcome {
        context.setStepStatus(SetupStepStage.TOOLCHAIN_COMPILATION, StepStatus.FAILED, null, reason, null, null)
        context.appendLog("[Switch] ERROR: $reason")
        return SetupOutcome.Failed(SetupStepStage.TOOLCHAIN_COMPILATION.name, reason)
    }
}

private fun validateFrozenInputs(inputs: Map<ToolGroupId, ResolvedInput>): String? {
    if (inputs.isEmpty()) return "No frozen inputs provided for switch"
    val missing = ToolGroupCatalog.DEFAULT_GROUPS.map { it.id }.filter { it !in inputs }
    return if (missing.isNotEmpty()) {
        "Missing frozen inputs for required groups: ${missing.joinToString { it.value }}"
    } else {
        null
    }
}

private fun probePackagesViaDpkg(distro: String, needed: List<String>, cli: WslCliExecutor): List<String> {
    val res = cli.execute(distro, listOf("dpkg-query", "-W", "-f=\${Package} \${Status}\\n") + needed)
    val text = res.output + "\n" + res.error
    val installed = text.lines()
        .filter { it.contains("install ok installed") }
        .map { it.substringBefore(" ").trim() }
        .toSet()
    return needed.filter { it !in installed }
}

internal fun computeInputFingerprint(
    group: ToolGroup,
    input: ResolvedInput,
    toolchainVersions: Map<String, String>,
): String {
    val arch = detectHostArch()
    val canonical = when (input) {
        is ResolvedInput.Git -> buildString {
            append("group:").append(group.id.value).append("\n")
            append("kind:git\n")
            append("repo:").append(input.repoUrl.orEmpty()).append("\n")
            append("commit:").append(input.commit).append("\n")
            val sortedSubmodules = input.submoduleCommits.toSortedMap()
            for ((subPath, subCommit) in sortedSubmodules) {
                append("submodule:").append(subPath).append("=").append(subCommit).append("\n")
            }
            append("arch:").append(arch).append("\n")
            for ((tool, ver) in toolchainVersions.toSortedMap()) {
                append("toolchain:").append(tool).append("=").append(ver).append("\n")
            }
            append("recipe:").append(group.recipe::class.simpleName ?: "Recipe").append("\n")
            append("recipeRevision:1")
        }
        is ResolvedInput.Release -> buildString {
            append("group:").append(group.id.value).append("\n")
            append("kind:release\n")
            append("version:").append(input.version).append("\n")
            append("url:").append(input.url).append("\n")
            append("sha256:").append(input.sha256).append("\n")
            append("platform:").append(input.platform).append("\n")
            append("archiveMember:").append(input.archiveMember).append("\n")
            append("arch:").append(arch).append("\n")
            append("recipe:").append(group.recipe::class.simpleName ?: "Recipe").append("\n")
            append("recipeRevision:1")
        }
    }
    return ArtifactStore.sha256(canonical)
}

private fun probeToolchainVersions(distro: String, cli: WslCliExecutor): Map<String, String> {
    val clangRes = cli.execute(distro, listOf("clang", "--version"), timeoutSeconds = 5)
    val cmakeRes = cli.execute(distro, listOf("cmake", "--version"), timeoutSeconds = 5)
    val javaRes = cli.execute(distro, listOf("java", "-version"), timeoutSeconds = 5)
    val clang = if (clangRes.exitCode == 0) clangRes.output.lines().firstOrNull()?.trim().orEmpty() else ""
    val cmake = if (cmakeRes.exitCode == 0) cmakeRes.output.lines().firstOrNull()?.trim().orEmpty() else ""
    val java = if (javaRes.exitCode == 0) {
        (javaRes.output.ifBlank { javaRes.error }).lines().firstOrNull()?.trim().orEmpty()
    } else {
        ""
    }
    return buildMap {
        if (clang.isNotBlank()) put("clang", clang)
        if (cmake.isNotBlank()) put("cmake", cmake)
        if (java.isNotBlank()) put("java", java)
    }
}