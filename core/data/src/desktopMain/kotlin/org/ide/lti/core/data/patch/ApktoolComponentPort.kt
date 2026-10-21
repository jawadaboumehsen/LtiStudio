/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.patch

import org.ide.lti.core.domain.plugin.ComponentPatchPort
import org.ide.lti.core.domain.plugin.DecodedComponent
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.runToEnd
import org.ide.lti.core.model.workspace.Workspace
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Decodes and rebuilds APKs and JARs of the work tree with apktool, in WSL through the build service, as LtiRom's
 * scripts/apktool.sh does: the firmware's framework-*.apk are installed once (tagged with the build's
 * incremental), decode uses `--no-debug-info`, a rebuild keeps the original META-INF, an APK is then signed with
 * the platform key and a JAR zip-aligned, and the component's stale oat/ and *.prof are removed.
 *
 * Paths the adapter passes are the work tree's `\\wsl.localhost\…` paths; [toLinux] maps them. Decoded trees live
 * in `apktool/` of the workspace, outside `work/`, so they never reach an image.
 */
class ApktoolComponentPort(
    private val port: PipelineExecutionPort,
    private val ws: Workspace,
    /** `ro.build.version.incremental` of the firmware: the apktool framework tag. */
    private val frameworkTag: String,
    /** The platform key pair the rebuilt APKs are signed with (`SigningPolicy.platformKeyRef`). */
    private val platformKeyRef: String,
    private val toLinux: (String) -> String,
    private val toWindows: (String) -> Path,
) : ComponentPatchPort {
    private val root = checkNotNull(ws.linuxPath) { "workspace ${ws.id} has no WSL folder" }
    private val frameworkDir = "$root/apktool/.framework"
    private var frameworksInstalled = false

    override suspend fun decode(componentAbsPath: String): DecodedComponent? {
        val input = toLinux(componentAbsPath)
        installFrameworks()
        val out = "$root/apktool/${input.substringAfterLast('/')}-${hash(input)}"
        tool("rm", "-rf", "--", out)
        tool(
            "apktool", "d", "--no-debug-info", "-p", frameworkDir, "-t", frameworkTag, "-f", "-o", out, input,
        )
        return DecodedComponent(componentPath = componentAbsPath, decodeRoot = out)
    }

    override suspend fun readMember(decoded: DecodedComponent, relPath: String): String? =
        member(decoded, relPath).takeIf { Files.isRegularFile(it) }?.let { Files.readString(it) }

    override suspend fun writeMember(decoded: DecodedComponent, relPath: String, content: String): Boolean {
        val file = member(decoded, relPath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
        return true
    }

    /** One `find` in WSL: walking a decoded framework jar file by file over `\\wsl.localhost` is slow. */
    override suspend fun listMembers(decoded: DecodedComponent, prefix: String): List<String> {
        val dir = decoded.decodeRoot + if (prefix.isEmpty()) "" else "/" + prefix.trim('/')
        val listing = port.runToEnd(ws, "find", listOf(dir, "-type", "f", "-printf", "%P\\n"))
        if (listing.exitCode != 0) return emptyList()
        val base = prefix.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        return listing.stdout.lines().filter { it.isNotBlank() }.map { base + it }
    }

    override suspend fun rebuild(decoded: DecodedComponent, outputAbsPath: String): Boolean {
        val dir = decoded.decodeRoot
        val output = toLinux(outputAbsPath)
        val built = "$dir/dist/rebuilt"
        // apktool drops the original signature block of a JAR otherwise (framework jars keep their META-INF).
        if (port.runToEnd(ws, "test", listOf("-d", "$dir/original/META-INF")).exitCode == 0) {
            tool("mkdir", "-p", "$dir/build/apk")
            tool("cp", "-a", "$dir/original/META-INF", "$dir/build/apk/META-INF")
        }
        tool("apktool", "b", "-p", frameworkDir, dir, "-o", built)
        if (output.endsWith(".apk")) {
            val key = "$root/$platformKeyRef"
            tool("signapk", "$key.x509.pem", "$key.pk8", built, output)
        } else {
            tool("zipalign", "-p", "-f", "4", built, output)
        }
        val parent = output.substringBeforeLast('/')
        tool("rm", "-rf", "--", "$parent/oat")
        val profiles = listOf("(", "-name", "*.prof", "-o", "-name", "*.bprof", ")")
        port.runToEnd(ws, "find", listOf(parent, "-maxdepth", "1") + profiles + "-delete")
        return true
    }

    override suspend fun discard(decoded: DecodedComponent) {
        port.runToEnd(ws, "rm", listOf("-rf", "--", decoded.decodeRoot))
    }

    private suspend fun installFrameworks() {
        if (frameworksInstalled) return
        val fw = "$root/work/system/system/framework"
        val apks = port.runToEnd(ws, "find", listOf(fw, "-maxdepth", "1", "-name", "framework-*.apk"))
            .stdout.lines().filter { it.isNotBlank() }.sorted()
        tool("mkdir", "-p", frameworkDir)
        apks.forEach { tool("apktool", "if", "-p", frameworkDir, it, "-t", frameworkTag) }
        frameworksInstalled = true
    }

    private fun member(decoded: DecodedComponent, relPath: String): Path =
        toWindows(decoded.decodeRoot + "/" + relPath.trimStart('/'))

    private suspend fun tool(id: String, vararg args: String) {
        val result = port.runToEnd(ws, id, args.toList())
        check(result.exitCode == 0) {
            "$id ${args.firstOrNull().orEmpty()} failed (${result.exitCode}): " +
                result.stderr.lines().lastOrNull { it.isNotBlank() }.orEmpty()
        }
    }

    private fun hash(text: String) =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(4).joinToString("") { "%02x".format(it) }
}
