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

import kotlinx.coroutines.yield
import org.ide.lti.core.domain.pipeline.HostRequest
import org.ide.lti.core.domain.pipeline.HostStepPort
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.text.SidecarEditor
import org.ide.lti.core.domain.plugin.ApkJarOutcome
import org.ide.lti.core.domain.plugin.ApplyModules
import org.ide.lti.core.domain.plugin.ConcreteOperation
import org.ide.lti.core.domain.plugin.EffectKind
import org.ide.lti.core.domain.plugin.FileMutationPort
import org.ide.lti.core.domain.plugin.MutationContext
import org.ide.lti.core.domain.plugin.MutationEffect
import org.ide.lti.core.domain.plugin.MutationOutcome
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.model.plugin.FileMetadata
import org.ide.lti.core.model.workspace.Workspace
import java.nio.file.Files
import java.nio.file.Path

/**
 * Runs the Patches/Mods stage's [ApplyModules] in the app: the plans are applied by [files] straight to the
 * workspace's work tree in WSL, through its `\\wsl.localhost\<distro>\…` path, and every file a plan adds or
 * deletes gets its `fs_config` / `file_context` entries so the image build labels it.
 */
class ModuleHostSteps(
    private val files: FileMutationPort = FileMutationAdapter(),
    /** Runs apktool, signapk and zipalign for bytecode edits; null in apps that cannot reach the build service. */
    private val execution: PipelineExecutionPort? = null,
    /** The WSL distribution the workspaces live in; null when setup has not found one. */
    private val distro: () -> String?,
) : HostStepPort {

    override suspend fun run(workspace: Workspace, request: HostRequest): String {
        yield()
        require(request is ApplyModules) { "unsupported host request ${request::class.simpleName}" }
        val work = workTree(workspace)
        val ctx = MutationContext(
            workTreeRelPath = work.toString(),
            partitionRoots = request.partitions.filter { Files.isDirectory(work.resolve(it)) }.associateWith { it },
        )
        val bytecode by lazy { bytecodeAdapter(workspace, work, request.platformKeyRef) }
        val effects = request.plans.flatMap { plan ->
            yield()
            val key = "${plan.publisher}:${plan.id}:${plan.version}"
            val assets = request.assetRoots[key].orEmpty()
            val (componentOps, fileOps) = plan.operations.partition { it.isComponentEdit() }
            val fileEffects = when (val outcome = files.apply(plan.copy(operations = fileOps), ctx, assets)) {
                is MutationOutcome.Applied -> outcome.effects
                is MutationOutcome.Refused -> refused(key, outcome.report.errors)
            }
            val copies = fileOps.filter { it is ConcreteOperation.Copy || it is ConcreteOperation.Replace }
                .map { it.id }.toSet()
            val copied = fileEffects.filter { it.operationId in copies }
            if (copied.isNotEmpty()) {
                val values = buildValues(work, request)
                copied.forEach {
                    yield()
                    fillValues(work.resolve(it.partition).resolve(it.relativePath), values)
                }
            }
            val componentEffects = if (componentOps.isEmpty()) {
                emptyList()
            } else {
                val values = buildValues(work, request)
                when (val outcome = bytecode.apply(componentOps.map { it.withValues(values) }, ctx, assets)) {
                    is ApkJarOutcome.Applied -> outcome.effects
                    is ApkJarOutcome.Refused -> refused(key, outcome.report.errors)
                }
            }
            fileEffects + componentEffects
        }
        val metadata = request.plans.flatMap { it.operations }.filterIsInstance<ConcreteOperation.Copy>()
            .mapNotNull { op -> op.metadata?.let { op.id to it } }.toMap()
        yield()
        updateSidecars(work, effects, metadata)
        return "ok: ${effects.size} change(s) from ${request.plans.size} module(s)"
    }

    /**
     * Values only the workspace knows, written where a plugin puts `{{name}}` (bytecode edits, copied text files):
     * `platform.certificate` (DER hex of the platform certificate, LtiRom's signature patch),
     * `platform.certificate.pem` (the certificate itself, for otacerts.zip) and, from the
     * firmware's vbmeta.img, `vbmeta.size`, `vbmeta.publickey.offset`, `vbmeta.publickey.size` (prophide).
     */
    private fun buildValues(work: Path, request: ApplyModules): Map<String, String> {
        val values = mutableMapOf<String, String>()
        val pem = work.parent.resolve("${request.platformKeyRef}.x509.pem")
        if (Files.isRegularFile(pem)) {
            val base64 = Files.readAllLines(pem).filterNot { it.startsWith("-----") }.joinToString("")
            val der = java.util.Base64.getMimeDecoder().decode(base64)
            values["platform.certificate"] = der.joinToString("") { "%02x".format(it) }
            values["platform.certificate.pem"] = Files.readString(pem)
        }
        val vbmeta = work.parent.resolve("firmware").resolve("extracted").resolve("vbmeta.img")
        if (Files.isRegularFile(vbmeta)) {
            // AVB header: authentication / auxiliary block sizes at 12 / 20, public key offset / size at 64 / 72.
            val header = java.nio.ByteBuffer.wrap(Files.newInputStream(vbmeta).use { it.readNBytes(AVB_HEADER) })
            val auth = header.getLong(12)
            values["vbmeta.size"] = (AVB_HEADER + auth + header.getLong(20)).toString()
            values["vbmeta.publickey.offset"] = (AVB_HEADER + auth + header.getLong(64)).toString()
            values["vbmeta.publickey.size"] = header.getLong(72).toString()
        }
        return values
    }

    /** Writes the build values into a copied text file, or the text members of a copied zip, that ask for them. */
    private fun fillValues(file: Path, values: Map<String, String>) {
        if (!Files.isRegularFile(file) || Files.size(file) > TEXT_LIMIT) return
        val bytes = Files.readAllBytes(file)
        val filled = if (file.fileName.toString().endsWith(".zip")) fillZip(bytes, values) else fill(bytes, values)
        filled?.let { Files.write(file, it) }
    }

    /** The text with its values written, or null when it is binary or asks for none. */
    private fun fill(bytes: ByteArray, values: Map<String, String>): ByteArray? {
        val text = bytes.takeIf { 0.toByte() !in it }?.let(::String)?.takeIf { BUILD_VALUE.find(it) != null }
            ?: return null
        val filled = values.entries.fold(text) { acc, (name, value) -> acc.replace("{{$name}}", value) }
        BUILD_VALUE.find(filled)?.let { error("no value for ${it.value}") }
        return filled.toByteArray()
    }

    private fun fillZip(bytes: ByteArray, values: Map<String, String>): ByteArray? {
        val members = java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.map { it.name to zip.readBytes() }.toList()
        }
        val filled = members.map { (name, content) -> Triple(name, content, fill(content, values)) }
        if (filled.none { it.third != null }) return null
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            filled.forEach { (name, content, new) ->
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(new ?: content)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun ConcreteOperation.withValues(values: Map<String, String>): ConcreteOperation =
        if (this !is ConcreteOperation.ApkJarPatch) {
            this
        } else {
            copy(
                edits = edits.map { edit ->
                    fun fill(text: String): String {
                        val filled = values.entries.fold(text) { t, (name, value) -> t.replace("{{$name}}", value) }
                        BUILD_VALUE.find(filled)?.let { error("${edit.memberDescriptor}: no value for ${it.value}") }
                        return filled
                    }
                    edit.copy(
                        unifiedDiff = fill(edit.unifiedDiff),
                        xml = edit.xml.map { it.copy(fragment = fill(it.fragment)) },
                    )
                },
            )
        }

    private fun refused(key: String, errors: List<ValidationError>): Nothing =
        error("$key refused: " + errors.joinToString("; ") { "${it.code}: ${it.message}" })

    private fun ConcreteOperation.isComponentEdit() = this is ConcreteOperation.ApkJarPatch ||
        this is ConcreteOperation.CompiledClassMerge || this is ConcreteOperation.HookInjection

    /** apktool on the build service; the framework tag is the firmware's incremental, as LtiRom's apktool.sh. */
    private fun bytecodeAdapter(workspace: Workspace, work: Path, platformKeyRef: String): ApkJarMutationAdapter {
        val port = checkNotNull(execution) { "this app cannot run apktool" }
        val linuxRoot = checkNotNull(workspace.linuxPath)
        val uncRoot = work.parent.toString()
        val buildProp = Files.readString(work.resolve("system").resolve("system").resolve("build.prop"))
        val tag = buildProp.lineSequence().firstOrNull { it.startsWith("ro.build.version.incremental=") }
            ?.substringAfter('=')?.trim() ?: "default"
        return ApkJarMutationAdapter(
            ApktoolComponentPort(
                port = port,
                ws = workspace,
                frameworkTag = tag,
                platformKeyRef = platformKeyRef,
                toLinux = { path -> linuxRoot + path.removePrefix(uncRoot).replace('\\', '/') },
                toWindows = { path -> Path.of(uncRoot + path.removePrefix(linuxRoot).replace('/', '\\')) },
            ),
        )
    }

    private fun workTree(workspace: Workspace): Path {
        val linux = checkNotNull(workspace.linuxPath) { "workspace ${workspace.id} has no WSL folder" }
        val name = checkNotNull(workspace.environmentId?.distro ?: distro()) { "no WSL distribution is selected" }
        return Path.of("\\\\wsl.localhost\\$name\\" + linux.trim('/').replace('/', '\\'), "work")
    }

    /** One read and one write of each partition's two sidecars, whatever the number of changes. */
    private suspend fun updateSidecars(work: Path, effects: List<MutationEffect>, metadata: Map<String, FileMetadata>) {
        effects.filter { it.kind == EffectKind.DELETED || it.kind == EffectKind.CREATED || it.operationId in metadata }
            .groupBy { it.partition }
            .forEach { (partition, changes) ->
                yield()
                val fsFile = work.resolve("configs").resolve("fs_config-$partition")
                val ctxFile = work.resolve("configs").resolve("file_context-$partition")
                var fs = Files.readString(fsFile)
                var ctx = Files.readString(ctxFile)
                changes.forEach { change ->
                    if (change.kind == EffectKind.DELETED) {
                        fs = SidecarEditor.withoutFsConfig(fs, partition, listOf(change.relativePath))
                        ctx = SidecarEditor.withoutFileContext(ctx, partition, listOf(change.relativePath))
                    } else {
                        val isDir = Files.isDirectory(work.resolve(partition).resolve(change.relativePath))
                        val meta = metadata[change.operationId]
                        fs = SidecarEditor.withFsConfig(fs, partition, change.relativePath, isDir, meta)
                        ctx = SidecarEditor.withFileContext(ctx, partition, change.relativePath, meta?.label)
                    }
                }
                Files.writeString(fsFile, fs)
                Files.writeString(ctxFile, ctx)
            }
    }

    private companion object {
        const val AVB_HEADER = 256
        const val TEXT_LIMIT = 256L * 1024
        val BUILD_VALUE = Regex("""\{\{[a-z]+(\.[a-z]+)+}}""")
    }
}
