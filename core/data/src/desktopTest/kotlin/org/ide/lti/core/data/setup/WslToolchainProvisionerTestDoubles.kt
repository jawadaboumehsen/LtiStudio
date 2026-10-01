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

import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.RunHandle
import io.ltirom.tooling.core.remote.RunStatus
import io.ltirom.tooling.core.remote.RunStatusValue
import io.ltirom.tooling.core.remote.SequencedStreamEvent
import io.ltirom.tooling.core.remote.StartRunRequest
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.ToolStatusInfo
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import org.ide.lti.core.domain.ports.PublicationReport
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.ports.ToolRegistryResult
import org.ide.lti.core.domain.ports.ToolSource
import org.ide.lti.core.domain.ports.ToolStatus
import java.nio.charset.Charset

internal class FakeDaemonSupervisor(
    var descriptor: ServerConnectionDescriptor? =
        ServerConnectionDescriptor("127.0.0.1", 9090, "token", 1234L, "Ubuntu"),
    var isHealthyResult: Boolean = true,
    var shouldFailEnsureStarted: Boolean = false,
    var onEnsureStarted: (suspend () -> Unit)? = null,
) : DaemonSupervisorPort {
    var getConnectionInfoCount = 0
    var isHealthyCount = 0
    var ensureStartedCount = 0

    override suspend fun getConnectionInfo(): ServerConnectionDescriptor? {
        getConnectionInfoCount++
        return descriptor
    }

    override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean {
        isHealthyCount++
        return isHealthyResult
    }

    override suspend fun ensureStarted(): ServerConnectionDescriptor {
        ensureStartedCount++
        onEnsureStarted?.invoke()
        if (shouldFailEnsureStarted) {
            throw IllegalStateException("Failed to launch daemon")
        }
        return descriptor ?: throw IllegalStateException("No descriptor")
    }

    override suspend fun shutdownDaemon(): Boolean = true
    override fun close() {}
}

internal class FakeRemoteTransport(
    val fakeCli: FakeWslCliExecutor? = null,
    var healthInfo: WslServerInfo? = WslServerInfo(
        status = "UP",
        distro = "Ubuntu",
        kernelRelease = "5.15.0",
        architecture = "x86_64",
        javaVersion = "21.0.11",
        serverUptimeMs = 100_000L,
        availableTools = listOf("adb", "fastboot"),
    ),
    var publishedTools: MutableList<ToolStatusInfo> = mutableListOf(
        projectTool("payload-dumper-go"),
        projectTool("lpunpack"),
        projectTool("lpmake"),
        projectTool("simg2img"),
        projectTool("erofsfuse"),
        projectTool("mkfs.erofs"),
        projectTool("avbtool"),
        projectTool("unpack_bootimg"),
        projectTool("img2sdat"),
        projectTool("signapk"),
    ),
) : RemoteTransportPort {
    var checkHealthCount = 0

    override suspend fun checkHealth(): WslServerInfo? {
        checkHealthCount++
        return healthInfo
    }

    @Suppress("CyclomaticComplexMethod") // one branch per faked command
    override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
        if (fakeCli != null) {
            val fullCmd = listOf(request.toolId) + request.arguments
            val res = fakeCli.execute("Ubuntu", fullCmd)
            return ToolExecutionResponse(res.exitCode, res.output, res.error, 10L)
        }
        return when {
            request.toolId == "find" && request.arguments.any { it.contains("/dev") } -> {
                val devNodes = listOf("fuse", "loop-control", "null", "zero", "random", "urandom")
                ToolExecutionResponse(0, devNodes.joinToString("\n"), "", 10L)
            }
            request.toolId == "find" && "%m %f\n" in request.arguments -> {
                val output = listOf(
                    "payload-dumper-go", "lpunpack", "lpmake", "simg2img",
                    "erofsfuse", "mkfs.erofs", "avbtool", "unpack_bootimg",
                    "img2sdat", "signapk", "adb", "fastboot",
                ).let(::fakeBinListing)
                ToolExecutionResponse(0, output, "", 10L)
            }
            request.toolId == "find" && request.arguments.any { it.contains("external") } -> {
                val output = listOf(
                    "android-tools",
                    "apktool",
                    "erofs-utils",
                    "img2sdat",
                    "signapk",
                ).joinToString("\n")
                ToolExecutionResponse(0, output, "", 10L)
            }
            request.toolId == "test" -> ToolExecutionResponse(0, "", "", 10L)
            request.toolId == "git" && fakeGitRevParse(listOf("git") + request.arguments) != null ->
                fakeGitRevParse(listOf("git") + request.arguments)!!.let {
                    ToolExecutionResponse(it.exitCode, it.output, it.error, 2L)
                }
            request.toolId == "sha256sum" -> fakeSha256(request.arguments.firstOrNull().orEmpty()).let {
                ToolExecutionResponse(it.exitCode, it.output, it.error, 2L)
            }
            request.toolId == "dpkg-query" -> ToolExecutionResponse(0, "all ok installed", "", 2L)
            request.toolId == "df" -> ToolExecutionResponse(0, "Filesystem 100G 50G 50G 50% /home/lti\n", "", 2L)
            request.toolId == "mkfs.erofs" -> ToolExecutionResponse(0, "mkfs.erofs 1.7.1 --fs-config-file", "", 10L)
            else -> ToolExecutionResponse(0, "ok", "", 10L)
        }
    }

    override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()

    override suspend fun startRun(request: StartRunRequest): RunHandle {
        if (fakeCli != null) {
            if (request.request.toolId == "git" && request.request.arguments.firstOrNull() == "clone") {
                val targetDir = request.request.arguments.lastOrNull() ?: ""
                if (targetDir.isNotBlank()) {
                    fakeCli.existingPaths.add(targetDir)
                }
            }
        }
        return RunHandle(
            runId = "fake-run-${request.idempotencyKey}",
            status = RunStatusValue.RUNNING,
            startedAtEpochMs = 1000L,
        )
    }

    override fun attachRun(runId: String, fromSeq: Long): Flow<SequencedStreamEvent> = listOf(
        SequencedStreamEvent(1L, StreamEvent.OutputChunk("running...")),
        SequencedStreamEvent(2L, StreamEvent.ExecutionFinished(0, 100L)),
    ).asFlow()

    override suspend fun getRun(runId: String): RunStatus = RunStatus(
        runId = runId,
        status = RunStatusValue.COMPLETED,
        exitCode = 0,
        startedAtEpochMs = 1000L,
        endedAtEpochMs = 1100L,
    )

    override suspend fun listTools(): ToolListResult = ToolListResult.Tools(publishedTools)
    override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(publishedTools)
    override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true
    override suspend fun downloadFile(remotePath: String): ByteArray? = null
    override suspend fun shutdown(): Boolean = true
}

internal class FakeToolPublicationPort(
    var publishedResult: PublicationReport = PublicationReport(
        requested = ToolCatalog.ALL_TOOL_IDS,
        registered = ToolCatalog.ALL_TOOL_IDS,
        failed = emptyMap(),
        pruned = emptySet(),
    ),
) : ToolPublicationPort {
    var publishCallCount = 0
    var lastBinDir: String? = null

    override suspend fun publish(binDir: String, toolIds: Set<String>): PublicationReport {
        publishCallCount++
        lastBinDir = binDir
        return publishedResult
    }

    override suspend fun resolvedTools(): ToolRegistryResult = ToolRegistryResult.Tools(
        publishedResult.registered.map {
            ToolStatus(
                tool = it,
                installed = true,
                path = ToolCatalog.expectedPath(FAKE_BIN_DIR, it) ?: "$FAKE_BIN_DIR/$it",
                source = ToolSource.DYNAMIC,
                reason = null,
            )
        },
    )
}

@Suppress("CyclomaticComplexMethod", "ReturnCount", "ComplexCondition", "LongMethod")
internal class FakeWslCliExecutor : WslCliExecutor() {
    var hostDistros: List<String> = listOf("Ubuntu")
    var userHome: String = "/home/lti"
    var missingDevPackages: Boolean = false
    var pythonMissing: Boolean = false
    var wslInstalled: Boolean = true
    val existingPaths: MutableSet<String> = mutableSetOf()
    val fileContents: MutableMap<String, String> = mutableMapOf()
    val commandLog = mutableListOf<String>()

    /** Pinned tools whose installed file hashes to something other than its pin (an older download). */
    val staleDigests: MutableSet<String> = mutableSetOf()

    override fun executeHost(command: List<String>, timeoutSeconds: Long, charset: Charset): CliExecutionResult {
        val cmdStr = command.joinToString(" ")
        commandLog.add("HOST: $cmdStr")
        if (!wslInstalled) {
            return CliExecutionResult(1, "", "WSL 2 is not installed")
        }
        if (command.contains("-l") && command.contains("-q")) {
            return CliExecutionResult(0, hostDistros.joinToString("\n"), "")
        }
        if (command.contains("-l") && command.contains("-v")) {
            val lines =
                listOf("  NAME      STATE           VERSION") +
                    hostDistros.mapIndexed { i, d -> (if (i == 0) "* " else "  ") + "$d   Running         2" }
            return CliExecutionResult(0, lines.joinToString("\n"), "")
        }
        if (command.contains("--status")) {
            return if (hostDistros.isNotEmpty()) {
                CliExecutionResult(0, "Default Version: 2", "")
            } else {
                CliExecutionResult(1, "", "WSL not found")
            }
        }
        return CliExecutionResult(0, "", "")
    }

    override fun execute(
        distro: String,
        command: List<String>,
        timeoutSeconds: Long,
        charset: Charset,
        user: String?,
    ): CliExecutionResult {
        val cmdStr = command.joinToString(" ")
        commandLog.add("WSL: $cmdStr")
        if (cmdStr.contains("/etc/os-release")) {
            return CliExecutionResult(
                0,
                "NAME=\"Ubuntu\"\nVERSION=\"24.04 LTS (Noble Numbat)\"\nID=ubuntu\nVERSION_ID=\"24.04\"\n",
                "",
            )
        }
        if (cmdStr.contains("id -un; printf '%s' \"\$HOME\"") || cmdStr.contains("id -un")) {
            val u = userHome.substringAfterLast('/').ifBlank { "lti" }
            return CliExecutionResult(0, "$u\n$userHome", "")
        }
        if (cmdStr.contains("echo -n \$HOME") || cmdStr.contains("printenv HOME") || cmdStr.contains("printenv")) {
            return CliExecutionResult(0, userHome, "")
        }
        if (cmdStr.contains("JAVA_HOMES_BEGIN")) {
            val javaSection = "JAVA_HOMES_BEGIN\n" +
                "/usr/lib/jvm/java-21-openjdk-amd64|openjdk version \"21.0.4\" 2024-07-16\nJAVA_HOMES_END\n"
            val reqSection = RequirementCatalog.REQUIREMENTS.joinToString("\n") {
                "${it.id}=present"
            }
            return CliExecutionResult(0, "$javaSection$reqSection\nPACKAGES_FRESH=1\n", "")
        }
        if (cmdStr.contains("dpkg-query")) {
            if (missingDevPackages) {
                return CliExecutionResult(0, "", "no packages installed")
            }
            val pkgLines = WslToolchainProvisioner.REQUIRED_DEV_PACKAGES.joinToString("\n") {
                "$it install ok installed"
            }
            return CliExecutionResult(0, pkgLines, "")
        }
        if (pythonMissing && (cmdStr.contains("python") || cmdStr.contains("pip"))) {
            return CliExecutionResult(1, "", "python3: command not found")
        }
        if (cmdStr.contains("python3 --version") || cmdStr.contains("python3 -V")) {
            return CliExecutionResult(0, "Python 3.12.3", "")
        }
        if (cmdStr.contains("java -version")) {
            return CliExecutionResult(0, "openjdk version \"21.0.11\" 2024-04-16", "")
        }
        if (cmdStr.contains("df -BG")) {
            return CliExecutionResult(
                0,
                "Filesystem 1G-blocks Used Available Use% Mounted on\n/dev/sdc 250G 50G 190G 21% /home/lti\n",
                "",
            )
        }
        if (cmdStr.contains("git clone")) {
            val subDir = command.lastOrNull() ?: ""
            existingPaths.add(subDir)
        }
        if (cmdStr.contains("cp") || cmdStr.contains("mkdir") || cmdStr.contains("chmod")) {
            val target = command.lastOrNull() ?: ""
            if (target.isNotBlank()) {
                existingPaths.add(target)
            }
            if (cmdStr.contains("mkdir")) {
                val paths = command.dropWhile { it != "mkdir" }.drop(1).filter { !it.startsWith("-") }
                existingPaths.addAll(paths)
            }
            if (cmdStr.contains("/bin")) {
                ToolCatalog.entries.forEach {
                    existingPaths.add("$userHome/LtiRomTools/bin/${it.binaryName}")
                    existingPaths.add("$userHome/LtiRomTools/bin/${it.id}")
                }
                org.ide.lti.core.data.setup.doctor.ToolchainIntegrityInspector.DEFAULT_CORE_TOOLS.forEach {
                    existingPaths.add("$userHome/LtiRomTools/bin/$it")
                }
            }
        }
        if (cmdStr.contains("> '")) {
            val filePath = cmdStr.substringAfter("> '").substringBefore("'")
            if (filePath.isNotBlank() && filePath != cmdStr) {
                existingPaths.add(filePath)
            }
        }
        if (cmdStr.contains("openssl") && cmdStr.contains("-out")) {
            val outIdx = command.indexOf("-out")
            if (outIdx >= 0 && outIdx + 1 < command.size) {
                existingPaths.add(command[outIdx + 1])
            }
        }
        if (command.firstOrNull() == "find") {
            val dir =
                (if (command.getOrNull(1) == "-L") command.getOrNull(2) else command.getOrNull(1))?.trimEnd('/')
                    ?: ""
            val matches = existingPaths.filter { it.startsWith("$dir/") }
            if (command.contains("-printf")) {
                val direct = matches.filter { !it.removePrefix("$dir/").contains('/') }
                    .map { it.removePrefix("$dir/") }
                val listing = if (command.contains("%m %f\n")) fakeBinListing(direct) else direct.joinToString("\n")
                return CliExecutionResult(0, listing, "")
            }
            return if (matches.isNotEmpty()) {
                CliExecutionResult(0, matches.joinToString("\n"), "")
            } else {
                CliExecutionResult(0, "", "")
            }
        }
        if (command.firstOrNull() == "cat") {
            val target = command.lastOrNull()?.trim() ?: ""
            val content = fileContents[target]
            if (content != null) {
                return CliExecutionResult(0, content, "")
            }
            val parentDir = target.substringBeforeLast('/', "")
            val isKnown = existingPaths.contains(target) ||
                (parentDir.isNotBlank() && existingPaths.contains(parentDir))
            if (isKnown) {
                if (target.endsWith("install.json")) {
                    val installId = target.substringBeforeLast("/install.json").substringAfterLast('/')
                    return CliExecutionResult(
                        0,
                        """{"schema":1,"installId":"$installId","distro":"Ubuntu","arch":"x86_64","groups":{}}""",
                        "",
                    )
                }
                if (target.endsWith("manifest.json")) {
                    val artifactId = target.substringBeforeLast("/manifest.json").substringAfterLast('/')
                    val mf = """{"schema":1,"group":"test","artifactId":"$artifactId","fingerprint":"fp",""" +
                        """"source":{"kind":"git"},"recipe":{"type":"git","revision":1}}"""
                    return CliExecutionResult(0, mf, "")
                }
                return CliExecutionResult(0, "", "")
            }
            return CliExecutionResult(1, "", "cat: $target: No such file or directory")
        }
        if (command.firstOrNull() == "ls") {
            val dir = command.lastOrNull()?.trimEnd('/') ?: ""
            val names = existingPaths.filter { it.startsWith("$dir/") && !it.removePrefix("$dir/").contains('/') }
                .map { it.removePrefix("$dir/") }
            return CliExecutionResult(0, names.joinToString("\n"), "")
        }
        if (command.firstOrNull() == "rm") {
            val target = command.lastOrNull()?.trimEnd('/') ?: ""
            if (target.isNotBlank()) {
                existingPaths.removeIf { it == target || it.startsWith("$target/") }
            }
            return CliExecutionResult(0, "", "")
        }
        if (cmdStr.contains("test -e") ||
            cmdStr.contains("test -d") ||
            cmdStr.contains("test -x") ||
            cmdStr.contains("test -r") ||
            cmdStr.contains("test -f")
        ) {
            if (cmdStr.contains("java-17-openjdk") || cmdStr.contains("java-21-openjdk")) {
                return CliExecutionResult(0, "", "")
            }
            if (cmdStr.contains("/dev/fuse") || cmdStr.contains("/dev/loop-control")) {
                return CliExecutionResult(0, "", "")
            }
            val targetPath = command.lastOrNull() ?: ""
            // A synced submodule has its sources and build outputs.
            val insideSyncedSource = targetPath.contains("/LtiRomTools/external/") &&
                existingPaths.any { it.contains("/LtiRomTools/external/") && targetPath.startsWith("$it/") }
            return if (existingPaths.contains(targetPath) || insideSyncedSource) {
                CliExecutionResult(0, "", "")
            } else {
                CliExecutionResult(1, "", "Path does not exist: $targetPath")
            }
        }
        if (command.firstOrNull() == "sha256sum") {
            val path = command.getOrNull(1).orEmpty()
            if (staleDigests.any { path.endsWith("/$it") }) return CliExecutionResult(0, "${"0".repeat(64)}  $path", "")
            return fakeSha256(path)
        }
        fakeGitRevParse(command)?.let { return it }
        if (cmdStr.contains("mkfs.erofs")) {
            return CliExecutionResult(0, "mkfs.erofs 1.7.1\noptions: --fs-config-file -C -T", "")
        }
        return CliExecutionResult(0, "ok", "")
    }
}

/**
 * What `find <bin> -printf "%m %f\n"` prints for [names]: JARs readable (644), everything else executable (755).
 */
internal fun fakeBinListing(names: Collection<String>): String =
    names.joinToString("\n") { if (it.endsWith(".jar")) "644 $it" else "755 $it" }

/** The fake distro's project tool directory. */
internal const val FAKE_BIN_DIR: String = "/home/lti/LtiRomTools/bin"

/** A tool the service resolves from this app's manifest at its project path (what publication produces). */
internal fun projectTool(id: String): ToolStatusInfo = ToolStatusInfo(
    tool = id,
    installed = true,
    path = ToolCatalog.expectedPath(FAKE_BIN_DIR, id) ?: "$FAKE_BIN_DIR/$id",
    source = "DYNAMIC",
)

/** `sha256sum` on a fake machine: pinned release binaries (installed or downloaded) are the genuine ones. */
internal fun fakeSha256(path: String): CliExecutionResult {
    val release = PinnedReleases.ALL.firstOrNull {
        path.endsWith("/${it.toolId}") ||
            path.endsWith("/${it.archiveMember}")
    }
    return if (release != null) {
        CliExecutionResult(0, "${release.sha256}  $path", "")
    } else {
        CliExecutionResult(1, "", "sha256sum: $path: No such file or directory")
    }
}

/**
 * `git -C <dir> rev-parse ...` on a correctly synced fake machine: every source repository is its own clone
 * at its pinned commit. Null for any other git command.
 */
@Suppress("ReturnCount") // not git rev-parse / no -C dir / not a source repo / answer
internal fun fakeGitRevParse(command: List<String>): CliExecutionResult? {
    if (command.firstOrNull() != "git" || "rev-parse" !in command) return null
    val dir = command.getOrNull(command.indexOf("-C") + 1)?.trimEnd('/') ?: return null
    val pin = WslToolchainProvisioner.SUBMODULES.firstOrNull { dir.endsWith("/${it.name}") }?.commit
        ?: return CliExecutionResult(128, "", "fatal: not a git repository")
    val out = buildList {
        if ("--show-toplevel" in command) add(dir)
        if ("HEAD" in command) add(pin)
    }
    return CliExecutionResult(0, out.joinToString("\n"), "")
}
