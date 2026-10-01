/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.plugin.author

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.ide.lti.core.data.admission.atomicWrite
import org.ide.lti.core.domain.plugin.AuthorDiagnostic
import org.ide.lti.core.domain.plugin.AuthorProject
import org.ide.lti.core.domain.plugin.AuthorProjectTrustPort
import org.ide.lti.core.domain.plugin.AuthorProjectTrustUseCase
import org.ide.lti.core.domain.plugin.AuthorTaskRefusal
import org.ide.lti.core.domain.plugin.AuthorTaskResult
import org.ide.lti.core.domain.plugin.PluginPackagePolicy
import org.ide.lti.core.domain.plugin.TrustApproval
import org.ide.lti.core.domain.plugin.TrustCheck
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.concurrent.thread

@Suppress("ReturnCount")
class AuthorTaskProcessAdapter(
    private val trustStoreDir: Path,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) : AuthorProjectTrustPort {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        prettyPrint = false
    }

    private val trustUseCase = AuthorProjectTrustUseCase(this)

    suspend fun check(projectPath: String): TrustCheck = trustUseCase.check(projectPath)

    override suspend fun fingerprint(projectPath: String): String? {
        val projectDir = Path.of(projectPath).toAbsolutePath().normalize()
        if (!Files.exists(projectDir) || !Files.isDirectory(projectDir)) {
            return null
        }

        val relativePaths = mutableListOf<String>()
        val fixedFiles = listOf(
            "build.gradle.kts",
            "settings.gradle.kts",
            "gradle.properties",
            "gradle/wrapper/gradle-wrapper.properties",
        )
        for (rel in fixedFiles) {
            val file = projectDir.resolve(rel)
            if (Files.isRegularFile(file)) {
                relativePaths.add(rel)
            }
        }

        val scanDirs = listOf("src", "hooks")
        for (dirName in scanDirs) {
            val dir = projectDir.resolve(dirName)
            if (Files.isDirectory(dir)) {
                Files.walk(dir).use { stream ->
                    stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                        .forEach { file ->
                            val rel = projectDir.relativize(file).toString().replace('\\', '/')
                            relativePaths.add(rel)
                        }
                }
            }
        }

        val sortedPaths = relativePaths.sorted()
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(PluginPackagePolicy.TRANSFER_CHUNK_BYTES.toInt())
        for (relPath in sortedPaths) {
            // NUL-delimit the path from its content so a rename cannot produce the same digest
            // as a different file whose bytes happen to start with the renamed suffix.
            md.update(relPath.toByteArray(Charsets.UTF_8))
            md.update(0)
            val file = projectDir.resolve(relPath)
            Files.newInputStream(file).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    md.update(buffer, 0, read)
                }
            }
        }

        return md.digest().joinToString("") { "%02x".format(it) }
    }

    override suspend fun approval(projectPath: String): TrustApproval? {
        val file = approvalFileFor(projectPath)
        if (!Files.exists(file) || !Files.isRegularFile(file)) return null
        return try {
            val text = Files.readString(file)
            json.decodeFromString(TrustApproval.serializer(), text)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun approve(project: AuthorProject): TrustApproval {
        Files.createDirectories(trustStoreDir)
        val normalizedPath = Path.of(project.path).toAbsolutePath().normalize().toString()
        val approval = TrustApproval(
            projectPath = normalizedPath,
            // Recomputed from disk: a caller holding a stale AuthorProject would otherwise store an
            // approval whose fingerprint can never match, silently locking the project out.
            fingerprint = fingerprint(project.path) ?: project.fingerprint,
            approvedAtEpochMs = timeProvider(),
        )
        val file = approvalFileFor(project.path)
        val text = json.encodeToString(TrustApproval.serializer(), approval)
        atomicWrite(file, text)
        return approval
    }

    override suspend fun revoke(projectPath: String) {
        val file = approvalFileFor(projectPath)
        Files.deleteIfExists(file)
    }

    override suspend fun runTask(project: AuthorProject, task: String, timeoutMs: Long): AuthorTaskResult {
        if (task !in RESERVED_SDK_TASKS) {
            return AuthorTaskResult.Refused(AuthorTaskRefusal.UNKNOWN_TASK)
        }

        val trustCheck = check(project.path)
        if (trustCheck !is TrustCheck.Approved) {
            return AuthorTaskResult.Refused(AuthorTaskRefusal.NOT_TRUSTED)
        }

        return executeGradleTask(project, task, timeoutMs)
    }

    private suspend fun executeGradleTask(project: AuthorProject, task: String, timeoutMs: Long): AuthorTaskResult =
        withContext(Dispatchers.IO) {
            val projectDir = Path.of(project.path).toAbsolutePath().normalize()
            val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
            val gradlewBat = projectDir.resolve("gradlew.bat")
            val gradlewCmd = projectDir.resolve("gradlew.cmd")
            val gradlewSh = projectDir.resolve("gradlew")

            val gradlewFile = when {
                isWindows && Files.exists(gradlewBat) -> gradlewBat
                isWindows && Files.exists(gradlewCmd) -> gradlewCmd
                Files.exists(gradlewSh) -> gradlewSh
                isWindows -> gradlewBat
                else -> gradlewSh
            }

            val isBatch = gradlewFile.toString().endsWith(".bat", ignoreCase = true) ||
                gradlewFile.toString().endsWith(".cmd", ignoreCase = true)
            val command = if (isWindows && isBatch) {
                listOf("cmd.exe", "/c", gradlewFile.toString(), task)
            } else {
                listOf(gradlewFile.toString(), task)
            }

            val pb = ProcessBuilder(command)
            pb.directory(projectDir.toFile())
            val process = pb.start()

            var stdout = ""
            var stderr = ""
            // Daemon threads: on timeout destroyForcibly() closes these pipes underneath them.
            val stdoutReader = thread(name = "author-task-stdout", isDaemon = true) {
                stdout = runCatching { readBounded(process.inputStream, MAX_OUTPUT_BYTES) }.getOrDefault("")
            }
            val stderrReader = thread(name = "author-task-stderr", isDaemon = true) {
                stderr = runCatching { readBounded(process.errorStream, MAX_OUTPUT_BYTES) }.getOrDefault("")
            }

            try {
                withTimeout(timeoutMs) {
                    while (process.isAlive) {
                        delay(POLL_INTERVAL_MS)
                    }

                    val exitCode = process.exitValue()
                    stdoutReader.join()
                    stderrReader.join()

                    val diagnostics = parseDiagnostics(stdout, stderr)
                    if (exitCode != 0) {
                        AuthorTaskResult.Failed(
                            exitCode = exitCode,
                            diagnostics = diagnostics,
                            stdout = stdout,
                            stderr = stderr,
                        )
                    } else {
                        AuthorTaskResult.Success(
                            stdout = stdout,
                            stderr = stderr,
                            diagnostics = diagnostics,
                        )
                    }
                }
            } catch (_: TimeoutCancellationException) {
                destroyProcessTree(process)
                stdoutReader.join(READER_DRAIN_MS)
                stderrReader.join(READER_DRAIN_MS)
                AuthorTaskResult.TimedOut(afterMs = timeoutMs, stdout = stdout, stderr = stderr)
            } finally {
                if (process.isAlive) {
                    destroyProcessTree(process)
                }
            }
        }

    private fun destroyProcessTree(process: Process) {
        try {
            process.descendants().forEach { descendant ->
                runCatching { descendant.destroyForcibly() }
            }
            process.destroyForcibly()
        } catch (_: Exception) {
            process.destroyForcibly()
        }
    }

    private fun readBounded(input: InputStream, maxBytes: Int): String {
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var totalRead = 0
        input.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                if (totalRead < maxBytes) {
                    val toWrite = minOf(read, maxBytes - totalRead)
                    baos.write(buffer, 0, toWrite)
                    totalRead += toWrite
                }
            }
        }
        return baos.toString(Charsets.UTF_8)
    }

    private fun parseDiagnostics(stdout: String, stderr: String): List<AuthorDiagnostic> {
        val allLines = stdout.lines() + stderr.lines()
        val result = mutableListOf<AuthorDiagnostic>()
        for (line in allLines) {
            val diag = parseDiagnosticLine(line)
            if (diag != null) {
                result.add(diag)
            }
        }
        return result
    }

    private fun parseDiagnosticLine(line: String): AuthorDiagnostic? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.contains("|")) {
            val parts = trimmed.split("|", limit = 8)
            if (parts.size == 8) {
                val sev = parts[0]
                val code = parts[1]
                val file = parts[2]
                val lineStr = parts[3]
                val colStr = parts[4]
                val opId = parts[5]
                val fieldId = parts[6]
                val msg = parts[7]
                return AuthorDiagnostic(
                    severity = sev.trim(),
                    code = code.trim(),
                    file = file.trim().ifEmpty { null },
                    line = lineStr.trim().toIntOrNull(),
                    col = colStr.trim().toIntOrNull(),
                    operationId = opId.trim().ifEmpty { null },
                    fieldId = fieldId.trim().ifEmpty { null },
                    message = msg.trim(),
                )
            }
        }

        if (trimmed.startsWith("ERROR ")) {
            val rest = trimmed.removePrefix("ERROR ").trim()
            val code = rest.substringBefore(' ')
            val remainder = rest.substringAfter(' ', "").trim()
            val (fileOrTarget, msg) = if (remainder.contains(": ")) {
                remainder.substringBefore(": ").trim() to remainder.substringAfter(": ").trim()
            } else {
                null to remainder.ifEmpty { code }
            }
            return AuthorDiagnostic(
                severity = "ERROR",
                code = code,
                file = fileOrTarget,
                message = msg,
            )
        }

        if (trimmed.startsWith("OK ")) {
            val rest = trimmed.removePrefix("OK ").trim()
            val code = rest.substringBefore(' ')
            val msg = rest.substringAfter(' ', "").trim()
            return AuthorDiagnostic(
                severity = "OK",
                code = code,
                message = msg.ifEmpty { code },
            )
        }

        return null
    }

    private fun approvalFileFor(projectPath: String): Path {
        val normalized = Path.of(projectPath).toAbsolutePath().normalize().toString()
        val hash = sha256(normalized)
        return trustStoreDir.resolve("$hash.json")
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        val RESERVED_SDK_TASKS: Set<String> = setOf(
            "generateModPlan",
            "compileModPayload",
            "validateMod",
            "testMod",
            "packageMod",
            "inspectMod",
            "signMod",
        )
        private const val MAX_OUTPUT_BYTES = 256 * 1024
        private const val POLL_INTERVAL_MS = 50L
        private const val READER_DRAIN_MS = 500L
    }
}
