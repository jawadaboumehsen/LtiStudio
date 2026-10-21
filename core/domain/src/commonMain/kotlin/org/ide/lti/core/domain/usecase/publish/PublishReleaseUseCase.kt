/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.usecase.publish

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ide.lti.core.domain.pipeline.CacheKeys
import org.ide.lti.core.domain.pipeline.digestToken
import org.ide.lti.core.domain.pipeline.stages.GenerateOtaManifestStage
import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.runToEnd
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.workspace.CollisionPolicy
import org.ide.lti.core.model.workspace.PublishProvider
import org.ide.lti.core.model.workspace.PublishSettings
import org.ide.lti.core.model.workspace.Workspace

/** The release the last build produced: `out/manifest.json` and the zip it names. */
public data class ReleaseFiles(val zip: String, val sha256: String, val size: Long)

/** What Publish would do now, or why it cannot. */
public data class PublishPreflight(
    val release: ReleaseFiles?,
    val problems: List<String>,
    /** Where the release goes, as the service sees it (a WSL path for a local export). */
    val destination: String?,
    val journal: PublishJournal?,
)

/** The running step, and how many of its [total] bytes are through when it moves data (null: not measurable). */
public data class PublishTransfer(val stepId: String, val done: Long? = null, val total: Long? = null)

@Serializable
public enum class PublishStepState { PENDING, DONE, FAILED }

@Serializable
public data class PublishJournalStep(
    val id: String,
    val label: String,
    val state: PublishStepState = PublishStepState.PENDING,
    val detail: String? = null,
    /** Only a completed copy establishes ownership; a failed collision does not. */
    val copiedByAttempt: Boolean = false,
)

/**
 * One publish attempt (`out/publish/journal.json`). Publishing again with the same package and destination resumes
 * it: steps already done are not redone.
 */
@Serializable
public data class PublishJournal(
    val provider: PublishProvider,
    val destination: String,
    val zip: String,
    val sha256: String,
    val steps: List<PublishJournalStep>,
    /** "Exported to …" when every step is done; a local export is never "live". */
    val outcome: String? = null,
    /** Includes publication settings and manifest contents; older journals cannot safely resume. */
    val requestKey: String? = null,
) {
    val finished: Boolean get() = steps.all { it.state == PublishStepState.DONE }

    /** A copy that failed verification is copied again: `verify-x` failed means `export-x` runs again too. */
    public fun retryingFailedCopies(): PublishJournal {
        val redo = steps.filter { it.state == PublishStepState.FAILED && it.id.startsWith("verify-") }
            .map { if (it.id == "verify-remote") "upload-assets" else "export-" + it.id.removePrefix("verify-") }
        return copy(steps = steps.map { if (it.id in redo) it.copy(state = PublishStepState.PENDING) else it })
    }
}

/**
 * Publish stage: copies the signed package and its manifest to their destination and verifies them there. It
 * only ever runs when the person confirms it (the build never publishes by itself). Modelled on LtiRom's
 * scripts/release/publish.sh (journal, resume, verify after upload).
 */
public class PublishReleaseUseCase(
    private val workspaces: WorkspaceRepository,
    private val store: WorkspaceSettingsStore,
    private val port: PipelineExecutionPort,
    /** A Windows folder as WSL sees it (`D:\ROM` -> `/mnt/d/ROM`), as for Acquire's imports. */
    private val toLinuxPath: (String) -> String,
) {
    /** [edited] is the editor's current settings (they may not be saved yet); the saved ones otherwise. */
    public suspend fun preflight(workspaceId: String, edited: PublishSettings? = null): PublishPreflight {
        val ws = workspaces.getWorkspace(workspaceId)
            ?: return PublishPreflight(null, listOf("The workspace is not available."), null, null)
        val settings = edited ?: store.current(workspaceId)?.publish ?: PublishSettings()
        val problems = mutableListOf<String>()
        val release = releaseOf(ws, problems)
        val destination = destinationOf(settings, problems)
        return PublishPreflight(release, problems, destination, readJournal(ws))
    }

    /**
     * Runs (or resumes) the publish; [onProgress] gets the journal when it starts and after every step, [onTransfer]
     * the running step and its bytes. Never throws for a step. Cancelling it stops the running command and journals
     * the step as cancelled, so publishing again resumes there.
     */
    public suspend fun publish(
        workspaceId: String,
        edited: PublishSettings? = null,
        onProgress: (PublishJournal) -> Unit = {},
        onTransfer: (PublishTransfer) -> Unit = {},
    ): PublishJournal? {
        val ws = workspaces.getWorkspace(workspaceId)
        val check = preflight(workspaceId, edited)
        val release = check.release
        val destination = check.destination
        if (ws == null || release == null || destination == null || check.problems.isNotEmpty()) return null
        val settings = edited ?: store.current(workspaceId)?.publish ?: PublishSettings()
        val requestKey = CacheKeys.sha256(
            json.encodeToString(PublishSettings.serializer(), settings.copy(collisionPolicy = CollisionPolicy.FAIL)) +
                "\n" + port.readFile(ws, MANIFEST),
        )
        val previous = check.journal?.takeIf {
            it.matches(settings.provider, destination, release, requestKey)
        }
        var journal = previous?.retryingFailedCopies() ?: PublishJournal(
            provider = settings.provider,
            destination = destination,
            zip = release.zip,
            sha256 = release.sha256,
            steps = stepsFor(settings.provider).map { (id, label) -> PublishJournalStep(id, label) },
            requestKey = requestKey,
        )
        onProgress(journal)
        for (step in journal.steps.filter { it.state != PublishStepState.DONE }) {
            onTransfer(PublishTransfer(step.id))
            val report = { done: Long, total: Long -> onTransfer(PublishTransfer(step.id, done, total)) }
            val result = runCatching { runStep(ws, settings, release, destination, step, report) }
            (result.exceptionOrNull() as? CancellationException)?.let { cancelled ->
                val stopped = journal.with(step.copy(state = PublishStepState.FAILED, detail = CANCELLED))
                withContext(NonCancellable) { save(ws, stopped) }
                onProgress(stopped)
                throw cancelled
            }
            val updated = result.fold(
                onSuccess = { step.completed(it) },
                onFailure = { step.copy(state = PublishStepState.FAILED, detail = it.message) },
            )
            journal = journal.with(updated)
            if (journal.finished) journal = journal.copy(outcome = outcomeOf(settings.provider, destination))
            save(ws, journal)
            onProgress(journal)
            if (updated.state == PublishStepState.FAILED) break
        }
        return journal
    }

    private fun PublishJournal.with(step: PublishJournalStep) =
        copy(steps = steps.map { if (it.id == step.id) step else it })

    private fun PublishJournal.matches(
        provider: PublishProvider,
        destination: String,
        release: ReleaseFiles,
        requestKey: String,
    ): Boolean = this.provider == provider && this.destination == destination && sha256 == release.sha256 &&
        zip == release.zip && this.requestKey == requestKey

    private fun PublishJournalStep.completed(detail: String) = copy(
        state = PublishStepState.DONE,
        detail = detail,
        copiedByAttempt = copiedByAttempt || (id == "export-zip" && detail != "already there, kept"),
    )

    @Suppress("LongParameterList")
    private suspend fun runStep(
        ws: Workspace,
        settings: PublishSettings,
        release: ReleaseFiles,
        dest: String,
        step: PublishJournalStep,
        report: (Long, Long) -> Unit,
    ): String = if (settings.provider == PublishProvider.GITHUB_RELEASE && step.id != "verify-package") {
        GithubReleaseSteps(port, ws, settings, release, report).run(step.id)
    } else {
        runLocalStep(ws, settings, release, dest, step, report)
    }

    @Suppress("LongParameterList")
    private suspend fun runLocalStep(
        ws: Workspace,
        settings: PublishSettings,
        release: ReleaseFiles,
        dest: String,
        step: PublishJournalStep,
        report: (Long, Long) -> Unit,
    ): String = when (step.id) {
        "verify-package" -> {
            val actual = sha256(ws, ws.abs("out/package/${release.zip}"))
            check(actual == release.sha256) { "The package does not match its manifest (sha256 $actual)." }
            "sha256 ${release.sha256}"
        }
        "export-zip" -> {
            val policy = if (step.copiedByAttempt) CollisionPolicy.OVERWRITE else settings.collisionPolicy
            export(ws, policy, ws.abs("out/package/${release.zip}"), dest, release.zip) { done ->
                report(done, release.size)
            }
        }
        "verify-zip" -> {
            val actual = sha256(ws, "$dest/${release.zip}")
            check(actual == release.sha256) { "The exported package differs from the built one (sha256 $actual)." }
            "sha256 matches"
        }
        "export-manifest" -> export(ws, CollisionPolicy.OVERWRITE, ws.abs(MANIFEST), dest, manifestName(release))
        "verify-manifest" -> {
            val built = sha256(ws, ws.abs(MANIFEST))
            check(sha256(ws, "$dest/${manifestName(release)}") == built) { "The exported manifest differs." }
            "sha256 matches"
        }
        else -> error("unknown step ${step.id}")
    }

    /**
     * Copies through a temporary name and renames, so the destination never holds a half-copied file. [copied]
     * gets the temporary file's size while it grows.
     */
    @Suppress("LongParameterList")
    private suspend fun export(
        ws: Workspace,
        policy: CollisionPolicy,
        source: String,
        dest: String,
        name: String,
        copied: (Long) -> Unit = {},
    ): String {
        run(ws, "mkdir", "-p", dest)
        val exists = port.runToEnd(ws, "stat", listOf("-c", "%s", "$dest/$name")).exitCode == 0
        if (exists) {
            when (policy) {
                CollisionPolicy.FAIL -> error("$dest/$name already exists (collision policy: fail).")
                CollisionPolicy.SKIP -> return "already there, kept"
                CollisionPolicy.OVERWRITE -> Unit
            }
        }
        val partial = "$dest/.$name.partial"
        sampling(sample = { sizeOf(ws, partial) }, report = copied) { run(ws, "cp", "--", source, partial) }
        run(ws, "mv", "-f", "--", "$dest/.$name.partial", "$dest/$name")
        return if (exists) "replaced $dest/$name" else "copied to $dest/$name"
    }

    private suspend fun releaseOf(ws: Workspace, problems: MutableList<String>): ReleaseFiles? {
        val text = port.readFile(ws, MANIFEST)
        val entry = text?.let {
            runCatching { Json.parseToJsonElement(it).jsonObject.getValue("response").jsonArray.first().jsonObject }
                .getOrNull()
        }
        val zip = entry?.get("filename")?.jsonPrimitive?.content
        val sha = entry?.get("sha256")?.jsonPrimitive?.content
        val size = entry?.get("size")?.jsonPrimitive?.content?.toLongOrNull()
        if (zip == null || sha == null || size == null) {
            problems += if (text == null) {
                "No release yet: run the build through Release metadata first."
            } else {
                "out/manifest.json does not name a package; build Release metadata again."
            }
            return null
        }
        if (port.stat(ws, "out/package/UNSIGNED").exists) problems += "The package is unsigned; it cannot be published."
        if (!port.stat(ws, "out/package/$zip").exists) problems += "out/package/$zip is missing; build it again."
        return ReleaseFiles(zip, sha, size)
    }

    private fun destinationOf(settings: PublishSettings, problems: MutableList<String>): String? =
        when (settings.provider) {
            PublishProvider.NONE -> null.also { problems += "Choose where to publish (Provider)." }
            PublishProvider.LOCAL_EXPORT -> settings.localExportFolder?.trim()?.takeIf { WINDOWS_FOLDER.matches(it) }
                ?.let { toLinuxPath(it).trimEnd('/') }
                ?: null.also { problems += "Set the export folder (a Windows folder like D:\\ROM\\releases)." }
            PublishProvider.GITHUB_RELEASE -> settings.repository?.takeIf { it.isNotBlank() }
                ?.let { "github.com/$it" }
                ?: null.also { problems += "Set the release repository (owner/name)." }
        }

    private suspend fun sizeOf(ws: Workspace, path: String): Long? =
        port.execute(ws, stepCommand("stat", listOf("-c", "%s", "--", path), ws.root))
            .takeIf { it.exitCode == 0 }?.stdout?.trim()?.toLongOrNull()

    private suspend fun sha256(ws: Workspace, path: String): String {
        val result = port.runToEnd(ws, "sha256sum", listOf("--", path))
        check(result.exitCode == 0) { "sha256sum $path failed: ${result.stderr.trim()}" }
        return result.stdout.digestToken()
    }

    private suspend fun run(ws: Workspace, tool: String, vararg args: String) {
        val result = port.runToEnd(ws, tool, args.toList())
        val why = result.stderr.trim().ifEmpty { "exit ${result.exitCode}" }
        check(result.exitCode == 0) { "$tool ${args.last()} failed: $why" }
    }

    private suspend fun readJournal(ws: Workspace): PublishJournal? =
        port.readFile(ws, JOURNAL)?.let { runCatching { json.decodeFromString<PublishJournal>(it) }.getOrNull() }

    private suspend fun save(ws: Workspace, journal: PublishJournal) {
        port.uploadFile(ws, JOURNAL, json.encodeToString(PublishJournal.serializer(), journal).encodeToByteArray())
    }

    private val Workspace.root: String get() = checkNotNull(linuxPath) { "workspace $id has no WSL folder" }

    private fun Workspace.abs(rel: String): String = "$root/$rel"

    public companion object {
        public const val JOURNAL: String = "out/publish/journal.json"
        public const val CANCELLED: String = "Cancelled"
        private const val MANIFEST = GenerateOtaManifestStage.MANIFEST
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        private val LOCAL_STEPS = listOf(
            "verify-package" to "Check the package against its manifest",
            "export-zip" to "Copy the package",
            "verify-zip" to "Verify the copied package",
            "export-manifest" to "Copy the OTA manifest",
            "verify-manifest" to "Verify the copied manifest",
        )

        /** `D:\ROM`, `D:/ROM` or `\\wsl.localhost\<distro>\…` (`\\wsl$\…`). */
        private val WINDOWS_FOLDER = Regex("""^([A-Za-z]:[\\/].*|\\\\wsl(\.localhost|[$])\\.+)$""")

        private fun stepsFor(provider: PublishProvider) =
            if (provider == PublishProvider.GITHUB_RELEASE) GithubReleaseSteps.STEPS else LOCAL_STEPS

        /** A local export is "exported"; a GitHub release is "published" (its manifest step says if the OTA is). */
        private fun outcomeOf(provider: PublishProvider, destination: String) =
            if (provider == PublishProvider.GITHUB_RELEASE) "Published to $destination" else "Exported to $destination"

        private fun manifestName(release: ReleaseFiles) = release.zip.removeSuffix(".zip") + ".json"
    }
}

/**
 * Runs [block] while [sample] is read every second and, when it answers, handed to [report]. A sample is only a
 * progress reading: one that fails (even while being cancelled, as a remote query may) never fails [block].
 */
internal suspend fun <T> sampling(sample: suspend () -> Long?, report: (Long) -> Unit, block: suspend () -> T): T =
    coroutineScope {
        val sampler = launch {
            while (true) {
                delay(SAMPLE_MS)
                runCatching { sample() }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull()?.let(report)
            }
        }
        try {
            block()
        } finally {
            sampler.cancel()
        }
    }

private const val SAMPLE_MS = 1_000L
