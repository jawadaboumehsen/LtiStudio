/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.ToolCapabilities

/**
 * Dedicated engine for compiling and verifying native toolchain binaries.
 *
 * Adheres to:
 * - Single Responsibility Principle: Exclusively manages CMake, Make, and binary distribution
 *   for native C/C++ and auxiliary tool binaries.
 * - Incremental Execution & Durable Runs: Checks [ToolchainSetupRepository] and `$binDir` executables
 *   to skip redundant compilations. When [RemoteTransportPort] is provided, executes CMake and Make
 *   as durable runs through [JournaledRunLauncher] under environment/attempt/child identity (no static
 *   idempotency keys); an unjournaled durable run is refused.
 * - No Shell Chains: All commands execute as discrete arguments without `bash -c` or `&&`.
 */
public class ToolchainBuildEngine(
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val repository: ToolchainSetupRepository? = null,
    private val transport: RemoteTransportPort? = null,
) {
    private val commands = DistroCommands(cli)

    public companion object {
        public val CORE_BINARIES: List<String> = listOf(
            "adb",
            "fastboot",
            "mke2fs",
            "dump.erofs",
            "mkfs.erofs",
            "img2sdat",
        )

        private val AUXILIARY_RECIPE_GROUPS: Set<String> = setOf(
            ToolCapabilities.GROUP_APKTOOL,
            ToolCapabilities.GROUP_SIGNAPK,
            ToolCapabilities.GROUP_PAYLOAD_DUMPER_GO,
            ToolCapabilities.GROUP_GH,
        )

        private const val RELEASE_DOWNLOAD_TIMEOUT_SECONDS: Long = 300
        private const val SHA256_HEX_LENGTH = 64

        /** The JAR tools, each built from its submodule with the submodule's own Gradle wrapper. */
        private val GRADLE_JARS: Map<String, GradleJar> = mapOf(
            ToolCapabilities.GROUP_APKTOOL to GradleJar(
                tool = "apktool",
                project = "apktool",
                task = ":brut.apktool:apktool-cli:shadowJar",
                output = "brut.apktool/apktool-cli/build/libs/apktool-cli.jar",
            ),
            ToolCapabilities.GROUP_SIGNAPK to GradleJar(
                tool = "signapk",
                project = "signapk",
                task = ":signapk:shadowJar",
                output = "signapk/build/libs/signapk-all.jar",
            ),
        )

        /** First build downloads Gradle and every dependency. */
        private const val GRADLE_TIMEOUT_SECONDS: Long = 1_200

        /**
         * `git am` records a committer; a fresh machine has no git identity, and the user's own is never
         * touched. Given to the configure step only.
         */
        private val GIT_AM_IDENTITY: Map<String, String> = mapOf(
            "GIT_AUTHOR_NAME" to "LtiRom Studio",
            "GIT_AUTHOR_EMAIL" to "ltirom@localhost",
            "GIT_COMMITTER_NAME" to "LtiRom Studio",
            "GIT_COMMITTER_EMAIL" to "ltirom@localhost",
        )

        /** How much of a failed local command's stderr goes into its error message. */
        private const val ERROR_DETAIL_CHARS = 600

        /** Modules the img2sdat script imports from its own directory. */
        private val IMG2SDAT_MODULES =
            listOf("blockimgdiff.py", "common.py", "images.py", "rangelib.py", "sparse_img.py")

        /** The file a tool is installed as: JARs as `<id>.jar`, everything else under its id. */
        internal fun installedName(toolId: String): String = ToolCatalog.entryFor(toolId)?.binaryName ?: toolId
    }

    /**
     * Installs each pinned release whose group is targeted: kept when `bin` already holds the pinned
     * binary, otherwise downloaded, digest-checked and installed. Returns the failures (empty when all are
     * verified): a stale or unverified binary never counts as installed, and a mismatch is never installed.
     */
    private suspend fun installPinnedReleases(
        distro: String,
        extDir: String,
        binDir: String,
        targetRecipeGroups: Set<String>?,
        log: (String) -> Unit,
    ): List<String> {
        val failures = mutableListOf<String>()
        val targeted = PinnedReleases.ALL.filter {
            targetRecipeGroups == null || ToolCapabilities.recipeGroupFor(it.toolId) in targetRecipeGroups
        }
        for (release in targeted) {
            val dest = "$binDir/${release.toolId}"
            val label = "${release.toolId} ${release.version}"
            val message = if (sha256Of(distro, dest) == release.sha256) {
                "$label already installed (checksum verified)."
            } else {
                downloadVerified(distro, release, "$extDir/tmp/${release.toolId}", dest)
                    ?.let { "Could not install $label from ${release.url}: $it".also(failures::add) }
                    ?.let { "ERROR: $it" }
                    ?: "Installed $label (checksum verified)."
            }
            log("[Step 5/5] $message")
        }
        return failures
    }

    /**
     * Downloads [release] into [scratch], extracts only its binary, and installs it as [dest] only when its
     * digest matches the pin. Discrete commands, no shell (see class doc). Returns null on success.
     */
    private suspend fun downloadVerified(
        distro: String,
        release: PinnedRelease,
        scratch: String,
        dest: String,
    ): String? {
        val archive = "$scratch/release.tar.gz"
        val extracted = "$scratch/extract/${release.archiveMember}"
        commands.run(distro, listOf("rm", "-rf", "$scratch/extract"))
        commands.run(distro, listOf("mkdir", "-p", "$scratch/extract"))
        val steps = listOf(
            "download" to listOf("curl", "-fsSL", "--retry", "3", "-o", archive, release.url),
            "extract" to listOf("tar", "-xzf", archive, "-C", "$scratch/extract", release.archiveMember),
        )
        for ((step, command) in steps) {
            val res = commands.run(distro, command, timeoutSeconds = RELEASE_DOWNLOAD_TIMEOUT_SECONDS)
            if (res.exitCode != 0) {
                return "$step failed: " + res.error.ifBlank { res.output }.ifBlank { "exit ${res.exitCode}" }
            }
        }
        val actual = sha256Of(distro, extracted)
        val failure = if (actual != release.sha256) {
            "checksum mismatch (expected ${release.sha256}, got ${actual ?: "none"}); not installed"
        } else {
            val installed =
                commands.run(distro, listOf("install", "-m", "755", extracted, "$dest.new")).exitCode == 0 &&
                    commands.run(distro, listOf("mv", "-f", "$dest.new", dest)).exitCode == 0
            if (installed) null else "could not write $dest"
        }
        commands.run(distro, listOf("rm", "-rf", "$scratch/extract", archive))
        return failure
    }

    /**
     * A rebuild configures from scratch: CMake never re-checks a cached tool path, so a cache that points at
     * a tool that has since gone (e.g. `/usr/bin/llvm-ar` after the `llvm` package was removed) would fail
     * every rebuild. Compiled objects stay; only the configuration is redone.
     */
    private suspend fun discardCmakeCache(distro: String, buildDir: String, log: (String) -> Unit) {
        commands.run(distro, listOf("rm", "-rf", "$buildDir/CMakeCache.txt", "$buildDir/CMakeFiles"))
        log("[Step 5/5] Rebuild: discarded the previous CMake configuration in $buildDir.")
    }

    /** The sha256 of [path] in the distro, or null when it doesn't exist or can't be read. */
    private suspend fun sha256Of(distro: String, path: String): String? {
        val res = commands.run(distro, listOf("sha256sum", path))
        return res.output.trim().substringBefore(' ').takeIf { res.exitCode == 0 && it.length == SHA256_HEX_LENGTH }
    }

    /**
     * Records installation evidence for every tool of [recipeGroup] whose binary now exists.
     * This is file discovery only: it never marks a tool execution-verified — that fact comes from
     * the domain probe run by the provisioner (FR-010).
     */
    private suspend fun recordInstalledBinaries(distro: String, binDir: String, recipeGroup: String) {
        for (toolId in ToolCapabilities.toolsForRecipeGroup(recipeGroup)) {
            val path = "$binDir/${installedName(toolId)}"
            if (isUsable(distro, binDir, toolId)) {
                repository?.updateTool(toolId) { tool -> tool.copy(isCompiled = true, binaryPath = path) }
            }
        }
    }

    /** Runs one build step; the result is null on success, otherwise why it failed (the tool's own stderr). */
    @Suppress("ReturnCount") // local CLI path, unjournaled refusal and journaled verdict are three distinct exits
    private suspend fun executeBuildCommand(
        distro: String,
        workingDir: String,
        toolId: String,
        arguments: List<String>,
        timeoutSeconds: Long,
        actionName: String,
        journal: SetupJournalContext? = null,
        log: (String) -> Unit,
        onRunStarted: (SetupStepStage, String) -> Unit = { _, _ -> },
        environment: Map<String, String> = emptyMap(),
    ): Result<String?> {
        if (transport == null) {
            val env = environment.map { "${it.key}=${it.value}" }
            val command = (if (env.isEmpty()) emptyList() else listOf("env") + env) + toolId + arguments
            val res = commands.run(distro, command, timeoutSeconds = timeoutSeconds)
            log("[$toolId] ${res.output.takeLast(300)}")
            return Result.success(
                if (res.exitCode == 0) null else res.error.ifBlank { res.output }.takeLast(ERROR_DETAIL_CHARS),
            )
        }
        val repo = repository
        val ctx = journal
        if (repo == null || ctx == null) {
            return Result.failure(
                IllegalStateException(
                    "Durable build step '$actionName' requires a journaled attempt; " +
                        "refusing an unjournaled server run.",
                ),
            )
        }
        val runResult = JournaledRunLauncher(transport, repo, ctx).run(
            stage = SetupStepStage.TOOLCHAIN_COMPILATION,
            actionId = "build:$actionName",
            request = ToolExecutionRequest(
                toolId = toolId,
                arguments = arguments,
                workingDirectory = workingDir,
                environment = environment,
                timeoutMs = timeoutSeconds * 1000L,
            ),
            onRunStarted = { stage, runId ->
                onRunStarted(stage, runId)
                log("[Step 5/5] Attached to build run $runId ($actionName).")
            },
            log = log,
        )
        return when (runResult) {
            is JournaledRunResult.Finished -> Result.success(
                runResult.takeIf { it.exitCode != 0 }?.let { it.errorTail.ifBlank { "exit code ${it.exitCode}" } },
            )
            is JournaledRunResult.JournalRejected -> Result.failure(IllegalStateException(runResult.reason))
            is JournaledRunResult.Interrupted -> Result.failure(IllegalStateException(runResult.reason))
        }
    }

    /**
     * Compiles and installs toolchain binaries into [binDir].
     *
     * A compiled group is skipped only while every tool of it is present (not just one sample binary), and a
     * rebuild ([forceRebuild]) always compiles. Every install step is checked: a failed copy or a failed
     * pinned release fails the result instead of leaving older binaries to pass the final checks.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    public suspend fun buildAndDistributeAll(
        distro: String,
        extDir: String,
        binDir: String,
        journal: SetupJournalContext? = null,
        targetRecipeGroups: Set<String>? = null,
        forceRebuild: Boolean = false,
        onRunStarted: (SetupStepStage, String) -> Unit = { _, _ -> },
        onProgress: (currentItem: String, index: Int, total: Int) -> Unit = { _, _, _ -> },
        log: (String) -> Unit = {},
    ): Result<Unit> {
        commands.run(distro, listOf("mkdir", "-p", binDir))
        fun targeted(group: String) = targetRecipeGroups == null || group in targetRecipeGroups
        val auxiliaryGroups = AUXILIARY_RECIPE_GROUPS.filter(::targeted)
        val groupsToBuild = buildList {
            if (targeted(ToolCapabilities.GROUP_ANDROID_TOOLS)) add(ToolCapabilities.GROUP_ANDROID_TOOLS)
            if (targeted(ToolCapabilities.GROUP_EROFS_UTILS)) add(ToolCapabilities.GROUP_EROFS_UTILS)
            if (targeted(ToolCapabilities.GROUP_IMG2SDAT)) add(ToolCapabilities.GROUP_IMG2SDAT)
            if (auxiliaryGroups.isNotEmpty()) add("auxiliary-tools")
        }
        val totalGroups = groupsToBuild.size.coerceAtLeast(1)

        for ((index, group) in groupsToBuild.withIndex()) {
            onProgress(group, index + 1, totalGroups)
            val failure = when (group) {
                ToolCapabilities.GROUP_ANDROID_TOOLS -> buildAndroidTools(
                    distro,
                    extDir,
                    binDir,
                    journal,
                    forceRebuild,
                    onRunStarted,
                    log,
                )
                ToolCapabilities.GROUP_EROFS_UTILS -> buildErofsUtils(
                    distro,
                    extDir,
                    binDir,
                    journal,
                    forceRebuild,
                    onRunStarted,
                    log,
                )
                ToolCapabilities.GROUP_IMG2SDAT -> installImg2sdat(distro, extDir, binDir, forceRebuild)
                else -> installAuxiliary(
                    AuxiliaryRequest(distro, extDir, binDir, auxiliaryGroups, forceRebuild, journal, onRunStarted, log),
                )
            }
            if (failure != null) return Result.failure(IllegalStateException(failure))
        }

        val targetedTools = targetRecipeGroups?.flatMap { ToolCapabilities.toolsForRecipeGroup(it) }?.toSet()
        val missing = CORE_BINARIES.filter { targetedTools == null || it in targetedTools }
            .filterNot { commands.exists(distro, "$binDir/$it", "-x") }
        if (missing.isNotEmpty()) {
            return Result.failure(
                IllegalStateException("Missing core binaries in $binDir: ${missing.joinToString(", ")}"),
            )
        }
        if (targetRecipeGroups == null) {
            repository?.markStageCompleted(SetupStepStage.TOOLCHAIN_COMPILATION.name, true)
        }
        return Result.success(Unit)
    }

    /** Returns null on success, or why android-tools could not be built or installed. */
    @Suppress("LongParameterList", "ReturnCount") // distinct exits: skipped, build failed, install failed
    private suspend fun buildAndroidTools(
        distro: String,
        extDir: String,
        binDir: String,
        journal: SetupJournalContext?,
        forceRebuild: Boolean,
        onRunStarted: (SetupStepStage, String) -> Unit,
        log: (String) -> Unit,
    ): String? {
        val group = ToolCapabilities.GROUP_ANDROID_TOOLS
        if (!forceRebuild && isRecorded("adb") && groupComplete(distro, binDir, group)) {
            log("[Step 5/5] android-tools: every tool is present in $binDir. Skipping CMake/Make.")
            return null
        }
        log("[Step 5/5] Compiling android-tools via CMake and Make...")
        val src = "$extDir/android-tools"
        val buildDir = "$src/build"
        commands.run(distro, listOf("mkdir", "-p", buildDir))
        if (forceRebuild) discardCmakeCache(distro, buildDir, log)
        val built = configureAndMake(
            distro = distro,
            name = "android-tools",
            configureDir = src,
            configureArgs = listOf(
                "-B", "build",
                "-S", src,
                "-DCMAKE_BUILD_TYPE=Release",
                "-DCMAKE_C_COMPILER=clang",
                "-DCMAKE_CXX_COMPILER=clang++",
                "-DANDROID_TOOLS_USE_BUNDLED_FMT=ON",
                "-DANDROID_TOOLS_USE_BUNDLED_LIBUSB=ON",
                // Upstream's own fixes for its vendored AOSP sources (configure resets them to their pins and
                // applies patches/ with `git am`); without them adb does not compile.
                "-DANDROID_TOOLS_PATCH_VENDOR=ON",
            ),
            configureEnvironment = GIT_AM_IDENTITY,
            configureTimeoutSeconds = 120,
            buildDir = buildDir,
            makeTimeoutSeconds = 3_600,
            journal = journal,
            onRunStarted = onRunStarted,
            log = log,
        )
        if (built != null) return built

        val vendor = "$src/vendor"
        val failure = commands.firstFailure(
            {
                if (commands.exists(
                        distro,
                        "$buildDir/vendor",
                        "-d",
                    )
                ) {
                    commands.copy(distro, "$buildDir/vendor/.", "$binDir/")
                } else {
                    "the build produced no vendor outputs"
                }
            },
            { commands.copyIfPresent(distro, "$vendor/avb/avbtool.py", "$binDir/avbtool") },
            { commands.copyIfPresent(distro, "$vendor/mkbootimg/mkbootimg.py", "$binDir/mkbootimg.py") },
            // mkbootimg imports its `gki` package from the directory it runs in.
            { commands.copyIfPresent(distro, "$vendor/mkbootimg/gki", "$binDir/") },
            {
                commands.copyIfPresent(
                    distro,
                    "$vendor/extras/ext4_utils/mkuserimg_mke2fs.py",
                    "$binDir/mkuserimg_mke2fs",
                )
            },
            { commands.linkIfPresent(distro, "$binDir/mke2fs.android", "mke2fs.android", "$binDir/mke2fs") },
            { commands.linkIfPresent(distro, "$binDir/mkbootimg.py", "mkbootimg.py", "$binDir/mkbootimg") },
            {
                commands.checked(
                    distro,
                    listOf(
                        "chmod",
                        "+x",
                        "$binDir/avbtool",
                        "$binDir/mkbootimg",
                        "$binDir/mke2fs",
                        "$binDir/mkuserimg_mke2fs",
                    ),
                )
            },
        )
        if (failure != null) return "android-tools built, but installing its outputs failed: $failure"
        recordInstalledBinaries(distro, binDir, group)
        return null
    }

    /** Returns null on success, or why erofs-utils could not be built or installed. */
    @Suppress("LongParameterList", "ReturnCount") // distinct exits: skipped, build failed, install failed
    private suspend fun buildErofsUtils(
        distro: String,
        extDir: String,
        binDir: String,
        journal: SetupJournalContext?,
        forceRebuild: Boolean,
        onRunStarted: (SetupStepStage, String) -> Unit,
        log: (String) -> Unit,
    ): String? {
        val group = ToolCapabilities.GROUP_EROFS_UTILS
        if (!forceRebuild && isRecorded("mkfs.erofs") && groupComplete(distro, binDir, group)) {
            log("[Step 5/5] erofs-utils: every tool is present in $binDir. Skipping CMake/Make.")
            return null
        }
        log("[Step 5/5] Compiling erofs-utils...")
        val src = "$extDir/erofs-utils"
        val outDir = "$src/out"
        commands.run(distro, listOf("mkdir", "-p", outDir))
        if (forceRebuild) discardCmakeCache(distro, outDir, log)
        val built = configureAndMake(
            distro = distro,
            name = "erofs-utils",
            configureDir = src,
            // As upstream's build.sh: this fork bundles libc++ with clang-only flags, so the compiler is
            // explicit rather than whatever an earlier configure left in the cache.
            configureArgs = listOf(
                "-S",
                "build/cmake",
                "-B",
                "out",
                "-DCMAKE_BUILD_TYPE=Release",
                "-DCMAKE_C_COMPILER=clang",
                "-DCMAKE_CXX_COMPILER=clang++",
                "-DMAX_BLOCK_SIZE=4096",
            ),
            configureTimeoutSeconds = 300,
            buildDir = outDir,
            makeTimeoutSeconds = 300,
            journal = journal,
            onRunStarted = onRunStarted,
            log = log,
        )
        if (built != null) return built

        val failure = commands.firstFailure(
            {
                if (commands.exists(
                        distro,
                        "$outDir/erofs-tools",
                        "-d",
                    )
                ) {
                    commands.copy(distro, "$outDir/erofs-tools/.", "$binDir/")
                } else {
                    "the build produced no erofs-tools"
                }
            },
            { commands.linkIfPresent(distro, "$binDir/fuse.erofs", "fuse.erofs", "$binDir/erofsfuse") },
            { commands.checked(distro, listOf("chmod", "+x", "$binDir/mkfs.erofs", "$binDir/dump.erofs")) },
        )
        if (failure != null) return "erofs-utils built, but installing its outputs failed: $failure"
        recordInstalledBinaries(distro, binDir, group)
        return null
    }

    @Suppress("LongParameterList", "ReturnCount") // distinct exits: cmake failed, make failed
    private suspend fun configureAndMake(
        distro: String,
        name: String,
        configureDir: String,
        configureArgs: List<String>,
        configureEnvironment: Map<String, String> = emptyMap(),
        configureTimeoutSeconds: Long,
        buildDir: String,
        makeTimeoutSeconds: Long,
        journal: SetupJournalContext?,
        onRunStarted: (SetupStepStage, String) -> Unit,
        log: (String) -> Unit,
    ): String? {
        val steps = listOf(
            Triple("cmake", configureDir, configureArgs) to (configureTimeoutSeconds to configureEnvironment),
            Triple("make", buildDir, listOf("-C", buildDir, "-j4")) to (makeTimeoutSeconds to emptyMap()),
        )
        for ((step, limits) in steps) {
            val (tool, dir, args) = step
            val (timeout, environment) = limits
            val failure = executeBuildCommand(
                distro = distro,
                workingDir = dir,
                toolId = tool,
                arguments = args,
                timeoutSeconds = timeout,
                actionName = "$name:$tool",
                journal = journal,
                log = log,
                onRunStarted = onRunStarted,
                environment = environment,
            ).getOrElse { return it.message ?: "$name $tool failed" }
            if (failure != null) {
                val what = if (tool == "cmake") "cmake configuration failed" else "make build failed"
                return "$name $what:\n$failure"
            }
        }
        return null
    }

    /** img2sdat is a Python script plus the modules it imports; they are installed side by side. */
    @Suppress("ReturnCount") // each return is a distinct failure exit: skipped, build failed, install failed
    private suspend fun installImg2sdat(
        distro: String,
        extDir: String,
        binDir: String,
        forceRebuild: Boolean,
    ): String? {
        if (!forceRebuild && isRecorded("img2sdat") && groupComplete(distro, binDir, ToolCapabilities.GROUP_IMG2SDAT)) {
            return null
        }
        val src = "$extDir/img2sdat"
        if (!commands.exists(distro, "$src/img2sdat", "-f")) return "img2sdat sources are missing in $src"
        val failure = commands.firstFailure(
            { commands.copy(distro, "$src/img2sdat", "$binDir/img2sdat") },
            *IMG2SDAT_MODULES.map { module ->
                suspend { commands.copy(distro, "$src/$module", "$binDir/$module") }
            }.toTypedArray(),
            { commands.checked(distro, listOf("chmod", "+x", "$binDir/img2sdat")) },
        )
        if (failure != null) return "installing img2sdat failed: $failure"
        recordInstalledBinaries(distro, binDir, ToolCapabilities.GROUP_IMG2SDAT)
        return null
    }

    private class AuxiliaryRequest(
        val distro: String,
        val extDir: String,
        val binDir: String,
        val groups: List<String>,
        val forceRebuild: Boolean,
        val journal: SetupJournalContext?,
        val onRunStarted: (SetupStepStage, String) -> Unit,
        val log: (String) -> Unit,
    )

    /**
     * Installs only the auxiliary groups asked for: each tool's own artifact, never a whole pre-staged
     * directory (which overwrote freshly built tools of other groups with older copies).
     */
    @Suppress("ReturnCount") // each return is a distinct failure exit: build failed, install failed
    private suspend fun installAuxiliary(req: AuxiliaryRequest): String? {
        req.log("[Step 5/5] Installing ${req.groups.joinToString()}...")
        for (group in req.groups) {
            val jar = GRADLE_JARS[group] ?: continue // payload-dumper-go and gh: pinned releases below
            buildAndInstallJar(req, jar)?.let { return it }
        }
        val releaseFailures = installPinnedReleases(req.distro, req.extDir, req.binDir, req.groups.toSet(), req.log)
        if (releaseFailures.isNotEmpty()) return releaseFailures.joinToString("; ")
        req.groups.forEach { recordInstalledBinaries(req.distro, req.binDir, it) }
        return null
    }

    /**
     * Builds [jar] with its submodule's Gradle wrapper (when missing, or always on a rebuild) and installs it
     * as `<tool>.jar`. The wrapper runs through `sh` with `-p` so neither an executable bit nor the working
     * directory matters; the build is a journaled run like CMake and Make.
     */
    @Suppress("ReturnCount") // distinct exits: sources missing, build failed, output missing
    private suspend fun buildAndInstallJar(req: AuxiliaryRequest, jar: GradleJar): String? {
        val projectDir = "${req.extDir}/${jar.project}"
        val output = "$projectDir/${jar.output}"
        // Built when missing, on a rebuild, or when its sources changed since (sync clears the record).
        if (req.forceRebuild || !isRecorded(jar.tool) || !commands.exists(req.distro, output, "-f")) {
            if (!commands.exists(req.distro, "$projectDir/gradlew", "-f")) {
                return "${jar.tool} sources are missing ($projectDir/gradlew); sync the source repositories first"
            }
            req.log("[Step 5/5] Building ${jar.tool} with Gradle (${jar.task})...")
            val failure = executeBuildCommand(
                distro = req.distro,
                workingDir = projectDir,
                toolId = "sh",
                arguments = listOf("$projectDir/gradlew", "-p", projectDir, "--no-daemon", jar.task),
                timeoutSeconds = GRADLE_TIMEOUT_SECONDS,
                actionName = "${jar.tool}:gradle",
                journal = req.journal,
                log = req.log,
                onRunStarted = req.onRunStarted,
            ).getOrElse { return it.message ?: "${jar.tool} Gradle build failed" }
            if (failure != null) return "${jar.tool} Gradle build failed (${jar.task}):\n$failure"
            if (!commands.exists(req.distro, output, "-f")) return "${jar.tool} build finished without $output"
        }
        return commands.copy(req.distro, output, "${req.binDir}/${jar.tool}.jar")
    }

    private fun isRecorded(toolId: String): Boolean = repository?.currentState?.tools?.get(toolId)?.isCompiled == true

    /** Every tool of [group] is usable in [binDir]: present is not enough (a file can lose its +x). */
    private suspend fun groupComplete(distro: String, binDir: String, group: String): Boolean =
        ToolCapabilities.toolsForRecipeGroup(group).all { isUsable(distro, binDir, it) }

    /** The probe's own check: a native tool executable, a JAR readable. */
    private suspend fun isUsable(distro: String, binDir: String, toolId: String): Boolean =
        commands.run(distro, ToolCatalog.probePlan(toolId, binDir, installedName(toolId)).existsCheck).exitCode == 0
}

/** Discrete, cancellable distro commands with checked results, shared by the build engine's steps. */
internal class DistroCommands(private val cli: WslCliExecutor) {
    suspend fun run(distro: String, command: List<String>, timeoutSeconds: Long = 10) =
        cli.run(distro, command, timeoutSeconds)

    suspend fun exists(distro: String, path: String, flag: String): Boolean =
        run(distro, listOf("test", flag, path)).exitCode == 0

    /** Runs [command]; null on success, else what failed. */
    suspend fun checked(distro: String, command: List<String>): String? {
        val res = run(distro, command)
        return if (res.exitCode == 0) {
            null
        } else {
            "${command.joinToString(" ")}: " + res.error.ifBlank { res.output }.ifBlank { "exit ${res.exitCode}" }
        }
    }

    suspend fun copy(distro: String, from: String, to: String): String? = checked(distro, listOf("cp", "-a", from, to))

    suspend fun copyIfPresent(distro: String, from: String, to: String): String? =
        if (exists(distro, from, "-e")) copy(distro, from, to) else null

    suspend fun linkIfPresent(distro: String, target: String, linkValue: String, link: String): String? =
        if (exists(distro, target, "-f")) checked(distro, listOf("ln", "-sf", linkValue, link)) else null

    /** Runs [steps] in order and returns the first failure, stopping there. */
    suspend fun firstFailure(vararg steps: suspend () -> String?): String? {
        for (step in steps) step()?.let { return it }
        return null
    }
}

/** A JAR tool built by [task] in the submodule [project]; [output] is relative to that submodule. */
private data class GradleJar(val tool: String, val project: String, val task: String, val output: String)
