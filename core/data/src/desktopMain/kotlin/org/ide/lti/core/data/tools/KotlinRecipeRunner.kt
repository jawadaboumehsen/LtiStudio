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

import io.ltirom.tooling.core.RemoteCancellationUnconfirmedException
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.RunPurpose
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.CancellationException
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ToolOutput
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolError

public class KotlinRecipeRunner(
    private val transport: RemoteTransportPort,
    private val commandRunner: RemoteCommandRunner = RemoteCommandRunner(transport),
) {
    @Suppress("CyclomaticComplexMethod")
    public suspend fun executeRecipe(
        srcDir: String,
        outDir: String,
        source: ResolvedSource,
        recipe: RecipeConfig,
        outputs: List<ToolOutput>,
        supportFiles: List<String>,
        onLog: suspend (String) -> Unit,
        groupId: String? = null,
    ): Result<Unit> = try {
        when (recipe) {
            is RecipeConfig.AndroidTools ->
                runAndroidTools(
                    srcDir,
                    outDir,
                    recipe,
                    outputs,
                    supportFiles,
                    onLog,
                    groupId,
                )
            is RecipeConfig.CMake -> runCmake(srcDir, outDir, recipe, outputs, supportFiles, onLog, groupId)
            is RecipeConfig.GradleJar -> runGradleJar(srcDir, outDir, recipe, outputs, onLog, groupId)
            is RecipeConfig.ScriptCopy -> runScriptCopy(srcDir, outDir, recipe, outputs, onLog)
            is RecipeConfig.Release ->
                runRelease(
                    srcDir,
                    outDir,
                    source as ResolvedSource.Release,
                    outputs,
                    onLog,
                    groupId,
                )
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: RemoteCancellationUnconfirmedException) {
        throw e
    } catch (e: ToolErrorException) {
        val msg = e.toolError.message
        if (isConnectionLostMessage(msg) && e.toolError !is ToolError.ConnectionLost) {
            Result.failure(ToolErrorException(ToolError.ConnectionLost(msg ?: "Connection lost")))
        } else {
            Result.failure(e)
        }
    } catch (e: Exception) {
        val isAdmissionRequired = e is io.ltirom.tooling.client.resilience.CommandAdmissionException
        val isConnLost =
            e is io.ltirom.tooling.core.ToolingException ||
                e is java.net.ConnectException ||
                e is java.nio.channels.ClosedChannelException ||
                e is java.io.IOException ||
                isConnectionLostMessage(e.message)
        val err =
            when {
                isAdmissionRequired -> ToolError.AdmissionRequired(e.message ?: "Command admission is closed")
                isConnLost -> ToolError.ConnectionLost(e.message ?: "Connection lost")
                else -> ToolError.BuildFailed(e.message ?: "Build failed")
            }
        Result.failure(ToolErrorException(err))
    }

    private fun isConnectionLostMessage(msg: String?): Boolean {
        if (msg == null) return false
        return msg.contains("Failed to execute command on remote daemon", ignoreCase = true) ||
            msg.contains("Stream closed without ExecutionFinished", ignoreCase = true) ||
            msg.contains("Stream error on remote daemon", ignoreCase = true) ||
            msg.contains("Connection refused", ignoreCase = true) ||
            msg.contains("Broken pipe", ignoreCase = true) ||
            msg.contains("Connection reset", ignoreCase = true) ||
            msg.contains("Connection lost", ignoreCase = true) ||
            msg.contains("socket closed", ignoreCase = true)
    }

    @Suppress("ThrowsCount")
    private suspend fun runCmd(
        tool: String,
        args: List<String>,
        workDir: String,
        onLog: suspend (String) -> Unit,
        acceptedExitCodes: Set<Int> = setOf(0),
        env: Map<String, String> = emptyMap(),
    ): Int {
        onLog("Executing: $tool ${args.joinToString(" ")}")
        val res =
            transport.execute(
                ToolExecutionRequest(
                    toolId = tool,
                    arguments = args,
                    workingDirectory = workDir,
                    environment = env,
                    timeoutMs = 600_000L,
                    purpose = RunPurpose.SETUP,
                ),
            )
        if (res.stdout.isNotBlank()) onLog(res.stdout)
        if (res.stderr.isNotBlank()) onLog(res.stderr)
        if (res.exitCode !in acceptedExitCodes) {
            val cmdPreview = "$tool ${args.take(3).joinToString(" ")}"
            val errDetail = res.stderr.ifBlank { res.stdout }
            @Suppress("ComplexCondition")
            if (res.exitCode == 127 ||
                errDetail.contains("command not found", ignoreCase = true) ||
                errDetail.contains("$tool: not found", ignoreCase = true) ||
                (errDetail.startsWith("$tool: ") && errDetail.contains("not found", ignoreCase = true))
            ) {
                throw ToolErrorException(ToolError.MissingPackages(listOf(tool)))
            }
            if (isConnectionLostMessage(errDetail)) {
                throw ToolErrorException(ToolError.ConnectionLost(errDetail))
            }
            throw ToolErrorException(
                ToolError.BuildFailed("Command '$cmdPreview' failed with exit ${res.exitCode}: $errDetail"),
            )
        }
        return res.exitCode
    }

    @Suppress("LongMethod")
    private suspend fun runAndroidTools(
        srcDir: String,
        outDir: String,
        recipe: RecipeConfig.AndroidTools,
        outputs: List<ToolOutput>,
        supportFiles: List<String>,
        onLog: suspend (String) -> Unit,
        groupId: String? = null,
    ) {
        val buildDir = "$srcDir/build"
        runCmd("mkdir", listOf("-p", buildDir), srcDir, onLog)
        val confArgs =
            listOf(
                "-S", srcDir,
                "-B", buildDir,
                "-DCMAKE_BUILD_TYPE=Release",
                "-DCMAKE_C_COMPILER=clang",
                "-DCMAKE_CXX_COMPILER=clang++",
                "-DANDROID_TOOLS_USE_BUNDLED_FMT=ON",
                "-DANDROID_TOOLS_USE_BUNDLED_LIBUSB=ON",
                "-DANDROID_TOOLS_PATCH_VENDOR=${if (recipe.patchVendor) "ON" else "OFF"}",
            ) + recipe.cmakeArgs

        val gitEnv =
            if (recipe.gitIdentity) {
                mapOf(
                    "GIT_COMMITTER_NAME" to "LtiRom Studio",
                    "GIT_COMMITTER_EMAIL" to "studio@ltirom.local",
                    "GIT_AUTHOR_NAME" to "LtiRom Studio",
                    "GIT_AUTHOR_EMAIL" to "studio@ltirom.local",
                )
            } else {
                emptyMap()
            }

        runCmd("cmake", confArgs, srcDir, onLog, env = gitEnv)

        val buildScript = "cmake --build ${KotlinStoreLayout.shellQuote(buildDir)} -j\$(nproc)"
        var buildExit = 0
        val streamEnv = if (groupId != null) gitEnv + ("LTI_TOOL_GROUP" to groupId) else gitEnv
        commandRunner.stream(
            script = buildScript,
            category = CommandCategory.BUILD,
            cwd = srcDir,
            env = streamEnv,
            onFinished = { buildExit = it },
        ).collect { line ->
            onLog(line)
        }
        if (buildExit != 0) {
            throw ToolErrorException(
                ToolError.BuildFailed("cmake --build failed with exit $buildExit"),
            )
        }

        runCmd("mkdir", listOf("-p", outDir), srcDir, onLog)
        for (out in outputs) {
            KotlinStoreLayout.validateRelativePath(out.file)
            val qOutFile = KotlinStoreLayout.shellQuote(out.file)
            val qDestination = KotlinStoreLayout.shellQuote("$outDir/${out.file}")
            val candidateNames =
                buildList {
                    add(out.file)
                    add("${out.file}.py")
                    add("${out.file}.sh")
                    out.aliasOf?.let {
                        add(it)
                        add("$it.py")
                        add("$it.sh")
                    }
                    for (a in out.aliases.keys) {
                        add(a)
                        add("$a.py")
                        add("$a.sh")
                    }
                    for (a in out.aliases.values) {
                        add(a)
                        add("$a.py")
                        add("$a.sh")
                    }
                    if (out.file == "mke2fs") {
                        add("mke2fs.android")
                    }
                }.distinct()

            val copyScript =
                buildString {
                    appendLine("file=\"\"")
                    appendLine("buildDir=" + KotlinStoreLayout.shellQuote(buildDir))
                    appendLine("srcDir=" + KotlinStoreLayout.shellQuote(srcDir))
                    for (cand in candidateNames) {
                        val qCand = KotlinStoreLayout.shellQuote(cand)
                        appendLine(
                            "if [ -z \"\$file\" ] && [ -f \"\$buildDir/$cand\" ]; then file=\"\$buildDir/$cand\"; fi",
                        )
                        appendLine(
                            "if [ -z \"\$file\" ] && [ -f \"\$buildDir/vendor/$cand\" ]; then " +
                                "file=\"\$buildDir/vendor/$cand\"; fi",
                        )
                        appendLine(
                            "if [ -z \"\$file\" ] && [ -f \"\$srcDir/$cand\" ]; then file=\"\$srcDir/$cand\"; fi",
                        )
                        appendLine(
                            "if [ -z \"\$file\" ] && [ -f \"\$srcDir/vendor/$cand\" ]; then " +
                                "file=\"\$srcDir/vendor/$cand\"; fi",
                        )
                        appendLine(
                            "if [ -z \"\$file\" ] && [ -f \"\$srcDir/vendor/avb/$cand\" ]; then " +
                                "file=\"\$srcDir/vendor/avb/$cand\"; fi",
                        )
                        appendLine(
                            "if [ -z \"\$file\" ] && [ -f \"\$srcDir/vendor/mkbootimg/$cand\" ]; then " +
                                "file=\"\$srcDir/vendor/mkbootimg/$cand\"; fi",
                        )
                        appendLine(
                            "if [ -z \"\$file\" ] && [ -f \"\$srcDir/vendor/extras/ext4_utils/$cand\" ]; then " +
                                "file=\"\$srcDir/vendor/extras/ext4_utils/$cand\"; fi",
                        )
                        appendLine(
                            "if [ -z \"\$file\" ] && [ -f \"\$srcDir/vendor/extras/f2fs_utils/$cand\" ]; then " +
                                "file=\"\$srcDir/vendor/extras/f2fs_utils/$cand\"; fi",
                        )
                        appendLine(
                            "if [ -z \"\$file\" ] && [ -f \"\$srcDir/vendor/libufdt/utils/src/$cand\" ]; then " +
                                "file=\"\$srcDir/vendor/libufdt/utils/src/$cand\"; fi",
                        )
                    }
                    appendLine("if [ -z \"\$file\" ]; then")
                    for (cand in candidateNames) {
                        val qCand = KotlinStoreLayout.shellQuote(cand)
                        appendLine(
                            "  if [ -z \"\$file\" ]; then file=\$(find \"\$buildDir\" -type f -name $qCand " +
                                "2>/dev/null | head -n 1); fi",
                        )
                    }
                    appendLine("fi")
                    appendLine("if [ -z \"\$file\" ]; then")
                    for (cand in candidateNames) {
                        val qCand = KotlinStoreLayout.shellQuote(cand)
                        appendLine(
                            "  if [ -z \"\$file\" ]; then file=\$(find \"\$srcDir\" -maxdepth 8 -type f -name " +
                                "$qCand 2>/dev/null | head -n 1); fi",
                        )
                    }
                    appendLine("fi")
                    appendLine("if [ -n \"\$file\" ] && [ -f \"\$file\" ]; then")
                    appendLine("  mkdir -p \"\$(dirname $qDestination)\"")
                    appendLine("  cp -a \"\$file\" $qDestination")
                    appendLine("  chmod 755 $qDestination")
                    appendLine("fi")
                }
            runCmd("sh", listOf("-c", copyScript), srcDir, onLog)
        }

        for (sfile in supportFiles) {
            KotlinStoreLayout.validateRelativePath(sfile)
            val trimmed = sfile.trimEnd('/')
            val qSource = KotlinStoreLayout.shellQuote("$srcDir/$trimmed")
            val qDestination = KotlinStoreLayout.shellQuote("$outDir/$trimmed")
            val qVendorSource = KotlinStoreLayout.shellQuote("$srcDir/vendor/mkbootimg/$trimmed")
            val copySupp =
                buildString {
                    appendLine("if [ -e $qSource ]; then")
                    appendLine("  mkdir -p \"\$(dirname $qDestination)\"")
                    appendLine("  cp -r $qSource $qDestination")
                    appendLine("elif [ -e $qVendorSource ]; then")
                    appendLine("  mkdir -p \"\$(dirname $qDestination)\"")
                    appendLine("  cp -r $qVendorSource $qDestination")
                    appendLine("fi")
                }
            runCmd("sh", listOf("-c", copySupp), srcDir, onLog)
        }
    }

    private suspend fun runCmake(
        srcDir: String,
        outDir: String,
        recipe: RecipeConfig.CMake,
        outputs: List<ToolOutput>,
        supportFiles: List<String>,
        onLog: suspend (String) -> Unit,
        groupId: String? = null,
    ) {
        val hasSubdir = !recipe.sourceSubdir.isNullOrBlank() && recipe.sourceSubdir != "."
        val sourcePath = if (hasSubdir) "$srcDir/${recipe.sourceSubdir}" else srcDir
        val buildPath = "$srcDir/${recipe.buildSubdir}"
        runCmd("mkdir", listOf("-p", buildPath), srcDir, onLog)
        val confArgs =
            listOf(
                "-S",
                sourcePath,
                "-B",
                buildPath,
                "-DCMAKE_BUILD_TYPE=Release",
                "-DCMAKE_C_COMPILER=clang",
                "-DCMAKE_CXX_COMPILER=clang++",
            ) + recipe.cmakeArgs
        runCmd("cmake", confArgs, srcDir, onLog)

        val buildScript = "cmake --build ${KotlinStoreLayout.shellQuote(buildPath)} -j\$(nproc)"
        var buildExit = 0
        val streamEnv = if (groupId != null) mapOf("LTI_TOOL_GROUP" to groupId) else emptyMap()
        commandRunner.stream(
            script = buildScript,
            category = CommandCategory.BUILD,
            cwd = srcDir,
            env = streamEnv,
            onFinished = { buildExit = it },
        ).collect { line ->
            onLog(line)
        }
        if (buildExit != 0) {
            throw ToolErrorException(
                ToolError.BuildFailed("cmake --build failed with exit $buildExit"),
            )
        }

        runCmd("mkdir", listOf("-p", outDir), srcDir, onLog)
        for (out in outputs) {
            KotlinStoreLayout.validateRelativePath(out.file)
            val qOutFile = KotlinStoreLayout.shellQuote(out.file)
            val qDestination = KotlinStoreLayout.shellQuote("$outDir/${out.file}")
            val copyScript =
                buildString {
                    appendLine("file=" + KotlinStoreLayout.shellQuote("$buildPath/${out.file}"))
                    appendLine("if [ ! -f \"\$file\" ]; then")
                    appendLine(
                        "  file=\$(find " + KotlinStoreLayout.shellQuote(buildPath) +
                            " -type f -name $qOutFile | head -n 1)",
                    )
                    appendLine("fi")
                    val alias = out.aliasOf
                    if (alias != null) {
                        val qAlias = KotlinStoreLayout.shellQuote(alias)
                        appendLine("if [ ! -f \"\$file\" ]; then")
                        appendLine(
                            "  file=\$(find " + KotlinStoreLayout.shellQuote(buildPath) +
                                " -type f -name $qAlias | head -n 1)",
                        )
                        appendLine("fi")
                    }
                    appendLine("if [ -n \"\$file\" ] && [ -f \"\$file\" ]; then")
                    appendLine("  mkdir -p \"\$(dirname $qDestination)\"")
                    appendLine("  cp -a \"\$file\" $qDestination")
                    appendLine("  chmod 755 $qDestination")
                    appendLine("fi")
                }
            runCmd("sh", listOf("-c", copyScript), srcDir, onLog)
        }

        for (sfile in supportFiles) {
            KotlinStoreLayout.validateRelativePath(sfile)
            val qSource = KotlinStoreLayout.shellQuote("$srcDir/$sfile")
            val qDestination = KotlinStoreLayout.shellQuote("$outDir/$sfile")
            val copySupp = "if [ -e $qSource ]; then cp -r $qSource $qDestination; fi"
            runCmd("sh", listOf("-c", copySupp), srcDir, onLog)
        }
    }

    private suspend fun runGradleJar(
        srcDir: String,
        outDir: String,
        recipe: RecipeConfig.GradleJar,
        outputs: List<ToolOutput>,
        onLog: suspend (String) -> Unit,
        groupId: String? = null,
    ) {
        val gradlewCheck =
            transport.execute(
                ToolExecutionRequest(
                    toolId = "test",
                    arguments = listOf("-x", "$srcDir/gradlew"),
                    workingDirectory = srcDir,
                ),
            )
        val cmd = if (gradlewCheck.exitCode == 0) "./gradlew" else "gradle"
        val gradleScript =
            buildString {
                appendLine(
                    "jdk=\"\$(dirname \"\$(dirname \"\$(readlink -f \"\$(which javac 2>/dev/null)\" 2>/dev/null)\" " +
                        "2>/dev/null)\" 2>/dev/null)\"",
                )
                appendLine("if [ -z \"\$jdk\" ] || [ ! -d \"\$jdk\" ]; then")
                appendLine("  jdk=\$(find /usr/lib/jvm -maxdepth 1 -name '*openjdk*' -type d 2>/dev/null | head -n 1)")
                appendLine("fi")
                appendLine("if [ -n \"\$jdk\" ] && [ -d \"\$jdk\" ]; then")
                appendLine("  export JAVA_HOME=\"\$jdk\"")
                appendLine("  export PATH=\"\$JAVA_HOME/bin:\$PATH\"")
                appendLine("fi")
                appendLine("$cmd ${recipe.task} --no-daemon")
            }
        var gradleExit = 0
        val streamEnv = if (groupId != null) mapOf("LTI_TOOL_GROUP" to groupId) else emptyMap()
        commandRunner.stream(
            script = gradleScript,
            category = CommandCategory.GRADLE,
            cwd = srcDir,
            env = streamEnv,
            onFinished = { gradleExit = it },
        ).collect { line ->
            onLog(line)
        }
        if (gradleExit != 0) {
            throw ToolErrorException(
                ToolError.BuildFailed("$cmd ${recipe.task} failed with exit $gradleExit"),
            )
        }

        runCmd("mkdir", listOf("-p", outDir), srcDir, onLog)
        val targetJar = if (recipe.outputPath.isNotBlank()) "$srcDir/${recipe.outputPath}" else ""
        for (out in outputs) {
            KotlinStoreLayout.validateRelativePath(out.file)
            val qDestination = KotlinStoreLayout.shellQuote("$outDir/${out.file}")
            val copyScript =
                buildString {
                    appendLine("jar=" + KotlinStoreLayout.shellQuote(targetJar))
                    appendLine("if [ ! -f \"\$jar\" ]; then")
                    appendLine(
                        "  jar=\$(find " + KotlinStoreLayout.shellQuote(srcDir) +
                            " -path \"*/build/libs/*.jar\" ! -name \"*-sources.jar\" ! -name \"*-javadoc.jar\" " +
                            "2>/dev/null | head -n 1)",
                    )
                    appendLine("fi")
                    appendLine("if [ -n \"\$jar\" ] && [ -f \"\$jar\" ]; then")
                    appendLine("  mkdir -p \"\$(dirname $qDestination)\"")
                    appendLine("  cp -a \"\$jar\" $qDestination")
                    appendLine("fi")
                }
            runCmd("sh", listOf("-c", copyScript), srcDir, onLog)
        }
    }

    private suspend fun runScriptCopy(
        srcDir: String,
        outDir: String,
        recipe: RecipeConfig.ScriptCopy,
        @Suppress("UnusedParameter") outputs: List<ToolOutput>,
        onLog: suspend (String) -> Unit,
    ) {
        runCmd("mkdir", listOf("-p", outDir), srcDir, onLog)
        for (file in recipe.files) {
            KotlinStoreLayout.validateRelativePath(file)
            val qFile = KotlinStoreLayout.shellQuote(file)
            val script =
                buildString {
                    appendLine("if [ ! -e '$srcDir/'$qFile ]; then exit 1; fi")
                    appendLine("mkdir -p \"\$(dirname '$outDir/'$qFile)\"")
                    appendLine("cp -a '$srcDir/'$qFile '$outDir/'$qFile")
                    appendLine("chmod 755 '$outDir/'$qFile")
                }
            runCmd("sh", listOf("-c", script), srcDir, onLog)
        }
    }

    @Suppress("ThrowsCount")
    private suspend fun runRelease(
        srcDir: String,
        outDir: String,
        source: ResolvedSource.Release,
        outputs: List<ToolOutput>,
        onLog: suspend (String) -> Unit,
        groupId: String? = null,
    ) {
        val archivePath = "$srcDir/archive.download"
        onLog("Downloading release archive from ${source.url}")
        val qUrl = KotlinStoreLayout.shellQuote(source.url)
        val qArchive = KotlinStoreLayout.shellQuote(archivePath)
        val curlScript = "curl -fsSL --retry 3 $qUrl -o $qArchive"
        var curlExit = 0
        val streamEnv = if (groupId != null) mapOf("LTI_TOOL_GROUP" to groupId) else emptyMap()
        commandRunner.stream(
            script = curlScript,
            category = CommandCategory.NETWORK,
            cwd = srcDir,
            env = streamEnv,
            onFinished = { curlExit = it },
        ).collect { line ->
            onLog(line)
        }
        if (curlExit != 0) {
            throw ToolErrorException(
                ToolError.BuildFailed("Download failed from ${source.url} with exit $curlExit"),
            )
        }

        if (source.sha256.isNotBlank()) {
            val shaRes =
                commandRunner.execute(
                    script = "sha256sum $qArchive",
                    category = CommandCategory.SHORT,
                    cwd = srcDir,
                ).getOrElse { throw it }
            val actualSha = shaRes.stdout.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
            if (shaRes.exitCode != 0 || !actualSha.equals(source.sha256.trim(), ignoreCase = true)) {
                throw ToolErrorException(
                    ToolError.ChecksumMismatch("Release archive SHA-256 mismatch for ${source.url}"),
                )
            }
        }

        val qOutDir = KotlinStoreLayout.shellQuote(outDir)
        commandRunner.execute("mkdir -p $qOutDir", cwd = srcDir)
        val member = source.member
        if (member.isNotBlank()) {
            KotlinStoreLayout.validateRelativePath(member)
        }
        val qMember = KotlinStoreLayout.shellQuote(member)
        val fallbackFile = outputs.firstOrNull()?.file ?: "tool"
        val qFallbackFile = KotlinStoreLayout.shellQuote(fallbackFile)
        val extractScript =
            buildString {
                appendLine("if tar -tf $qArchive >/dev/null 2>&1; then")
                appendLine("  tar -xf $qArchive -C $qOutDir $qMember")
                appendLine("elif unzip -t $qArchive >/dev/null 2>&1; then")
                appendLine("  unzip -q -o $qArchive $qMember -d $qOutDir")
                appendLine("else")
                appendLine("  cp -a $qArchive $qOutDir/$qFallbackFile")
                appendLine("fi")
            }
        val extractRes = commandRunner.execute(extractScript, cwd = srcDir).getOrElse { throw it }
        if (extractRes.exitCode != 0) {
            val errDetail = extractRes.stderr.ifBlank { extractRes.stdout }
            val msg = "Extraction failed with exit ${extractRes.exitCode}: $errDetail"
            throw ToolErrorException(ToolError.BuildFailed(msg))
        }

        for (out in outputs) {
            KotlinStoreLayout.validateRelativePath(out.file)
            val dest = "$outDir/${out.file}"
            val qDest = KotlinStoreLayout.shellQuote(dest)
            val moveScript =
                buildString {
                    appendLine("if [ -f $qOutDir/$qMember ] && [ $qOutDir/$qMember != $qDest ]; then")
                    appendLine("  mkdir -p \"\$(dirname $qDest)\"")
                    appendLine("  mv $qOutDir/$qMember $qDest")
                    appendLine("fi")
                    appendLine("chmod 755 $qDest")
                }
            val moveRes = commandRunner.execute(moveScript, cwd = outDir).getOrElse { throw it }
            if (moveRes.exitCode != 0) {
                val errDetail = moveRes.stderr.ifBlank { moveRes.stdout }
                throw ToolErrorException(ToolError.BuildFailed("Failed to setup output ${out.file}: $errDetail"))
            }
        }
    }
}
