/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.desktop.window

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ide.lti.core.common.utils.formatBytes
import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.theme.AccentColor
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.Appearance
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassSceneHost
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.copilot.BootloopCopilotAnalyzer
import org.ide.lti.core.domain.copilot.CrashSource
import org.ide.lti.core.domain.desktop.WorkspaceDropCoordinator
import org.ide.lti.core.domain.diff.ConfigDiffEngine
import org.ide.lti.core.domain.pipeline.toolkit.ApkCertInspector
import org.ide.lti.core.domain.pipeline.toolkit.ApkSignatureReport
import org.ide.lti.core.domain.pipeline.toolkit.FsType
import org.ide.lti.core.domain.pipeline.toolkit.FsTypes
import org.ide.lti.core.domain.pipeline.toolkit.SuperLayout
import org.ide.lti.core.domain.provenance.BuildProvenanceGenerator
import org.ide.lti.core.domain.provenance.ProvenanceVerifier
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.workbench.ImageWorkbench
import org.ide.lti.core.domain.workbench.WorkbenchRunner
import org.ide.lti.core.domain.workspace.FirmwareArchives
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.ui.copilot.BootloopCopilotSheet
import org.ide.lti.core.ui.desktop.DetachablePanelId
import org.ide.lti.core.ui.diff.PropertyDiffViewer
import org.ide.lti.core.ui.drop.DropIngestOverlay
import org.ide.lti.core.ui.settings.AppSettingsState
import org.ide.lti.core.ui.timeline.InteractiveBuildTimeline
import org.ide.lti.desktop.drop.AwtFileDropBridge
import org.ide.lti.desktop.tray.rememberClasspathPainter
import org.koin.core.context.GlobalContext
import java.io.File
import java.io.RandomAccessFile

private const val APPEARANCE_PERCENT = 100f
private const val WINDOW_WIDTH_MULTIPLIER = 1.45f
private const val WINDOW_HEIGHT_MULTIPLIER = 1.25f

/** Tracks which detachable secondary monitor windows are currently popped out. */
data class DetachedPanelWindowsState(
    val isCopilotOpen: Boolean = false,
    val isPartitionExplorerOpen: Boolean = false,
    val isDiffInspectorOpen: Boolean = false,
    val partitionImagePath: String? = null,
    /** An APK dropped on the window, whose signers the Diff Inspector reads. */
    val apkPath: String? = null,
) {
    fun openPanel(panel: DetachablePanelId, argument: String? = null): DetachedPanelWindowsState = when (panel) {
        DetachablePanelId.LOGCAT_COPILOT -> copy(isCopilotOpen = true)
        DetachablePanelId.PARTITION_EXPLORER -> copy(
            isPartitionExplorerOpen = true,
            partitionImagePath = argument?.takeIf { it.isNotBlank() } ?: partitionImagePath,
        )
        DetachablePanelId.DIFF_INSPECTOR -> copy(
            isDiffInspectorOpen = true,
            apkPath = argument?.takeIf { it.isNotBlank() } ?: apkPath,
        )
    }
}

/** Hosts popped-out secondary OS windows inheriting active LtiRom theme, glass scene, and OS file drop support. */
@Composable
fun ApplicationScope.DetachedToolWindows(
    state: DetachedPanelWindowsState,
    onStateChange: (DetachedPanelWindowsState) -> Unit,
    dropCoordinator: WorkspaceDropCoordinator?,
) {
    val koin = remember { GlobalContext.getOrNull() }
    val settings = remember(koin) { koin?.getOrNull<AppSettingsState>() }
    val appTheme = AppTheme.fromId(settings?.theme)
    val effectsEnabled = settings?.effectsEnabled ?: true
    val reducedMotion = settings?.reducedMotion ?: false
    val appearance = settings?.appearance?.let {
        Appearance(AccentColor.fromId(it.accent), it.compact, it.textScalePercent / APPEARANCE_PERCENT)
    }

    if (state.isCopilotOpen) {
        DetachedToolWindowFrame(
            windowId = DetachablePanelId.LOGCAT_COPILOT.name,
            title = "LtiRom Studio — Live Logcat & Bootloop Copilot",
            onClose = { onStateChange(state.copy(isCopilotOpen = false)) },
            appTheme = appTheme,
            effectsEnabled = effectsEnabled,
            reducedMotion = reducedMotion,
            appearance = appearance,
            dropCoordinator = dropCoordinator,
        ) {
            LiveCopilotPanelBody()
        }
    }

    if (state.isPartitionExplorerOpen) {
        DetachedToolWindowFrame(
            windowId = DetachablePanelId.PARTITION_EXPLORER.name,
            title = state.partitionImagePath?.let {
                "LtiRom Studio — Partition Explorer (${it.substringAfterLast('/').substringAfterLast('\\')})"
            } ?: "LtiRom Studio — Partition Explorer",
            onClose = { onStateChange(state.copy(isPartitionExplorerOpen = false)) },
            appTheme = appTheme,
            effectsEnabled = effectsEnabled,
            reducedMotion = reducedMotion,
            appearance = appearance,
            dropCoordinator = dropCoordinator,
        ) {
            LivePartitionExplorerBody(imagePath = state.partitionImagePath)
        }
    }

    if (state.isDiffInspectorOpen) {
        DetachedToolWindowFrame(
            windowId = DetachablePanelId.DIFF_INSPECTOR.name,
            title = "LtiRom Studio — Diff, Timeline & Provenance Inspector",
            onClose = { onStateChange(state.copy(isDiffInspectorOpen = false)) },
            appTheme = appTheme,
            effectsEnabled = effectsEnabled,
            reducedMotion = reducedMotion,
            appearance = appearance,
            dropCoordinator = dropCoordinator,
        ) {
            LiveDiffInspectorBody(apkPath = state.apkPath)
        }
    }
}

@Composable
private fun ApplicationScope.DetachedToolWindowFrame(
    windowId: String,
    title: String,
    onClose: () -> Unit,
    appTheme: AppTheme,
    effectsEnabled: Boolean,
    reducedMotion: Boolean,
    appearance: Appearance?,
    dropCoordinator: WorkspaceDropCoordinator?,
    content: @Composable () -> Unit,
) {
    val windowState = rememberWindowState(
        width = ComponentSize.MaxPanelWidth * WINDOW_WIDTH_MULTIPLIER,
        height = ComponentSize.MaxPanelHeight * WINDOW_HEIGHT_MULTIPLIER,
    )
    val coroutineScope = rememberCoroutineScope()

    Window(
        onCloseRequest = onClose,
        state = windowState,
        title = title,
        icon = rememberClasspathPainter("icons/ic_launcher.png"),
    ) {
        DisposableEffect(window, dropCoordinator) {
            WindowRegistry.register(windowId, window)
            val detachDrop = dropCoordinator?.let { AwtFileDropBridge.attach(window, it, coroutineScope) }
            onDispose {
                detachDrop?.invoke()
                WindowRegistry.unregister(windowId)
            }
        }

        GlassSceneHost(effectsEnabled = effectsEnabled) {
            LtiTheme(
                appTheme = appTheme,
                effectsEnabled = effectsEnabled,
                reducedMotion = reducedMotion,
                appearance = appearance,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    GlassBackdrop()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.Medium),
                    ) {
                        content()
                    }
                    if (dropCoordinator != null) {
                        val dropState by dropCoordinator.state.collectAsState()
                        DropIngestOverlay(
                            state = dropState,
                            onDismissBanner = dropCoordinator::clearBanner,
                        )
                    }
                }
            }
        }
    }
}

/** Copilot panel: the newest run's log of this workspace, read for known crash patterns. No sample log is shown. */
@Composable
fun LiveCopilotPanelBody() {
    val koin = remember { GlobalContext.getOrNull() }
    val workspaceManager = remember(koin) { koin?.getOrNull<WorkspaceManager>() }
    val runRepository = remember(koin) { koin?.getOrNull<RunRepository>() }
    val workspace = workspaceManager?.currentWorkspace?.collectAsState()?.value

    var liveLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var sourceLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(workspace?.id) {
        liveLogs = emptyList()
        sourceLabel = null
        val ws = workspace ?: return@LaunchedEffect
        val repo = runRepository ?: return@LaunchedEffect
        repo.observeRuns(ws.id).collect { runs ->
            val latest = runs.maxByOrNull { it.startedAt } ?: return@collect
            val cached = runCatchingCancellable { repo.cachedEvents(latest.id) }.getOrDefault(emptyList())
            liveLogs = cached
            sourceLabel = "Workspace ${ws.name} · Run ${latest.id} (${cached.size} lines)"
        }
    }

    val diagnoses = remember(liveLogs) { BootloopCopilotAnalyzer.analyze(CrashSource.LOGCAT, liveLogs) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        val source = sourceLabel
        if (source == null) {
            PanelNote("No run log in this workspace yet. Run a build, then open the Copilot again.")
        } else {
            GlassChip(label = "Source: $source", selected = true)
            if (diagnoses.isEmpty()) {
                PanelNote("No known crash pattern in this log.")
            } else {
                BootloopCopilotSheet(diagnoses = diagnoses)
            }
        }
    }
}

/**
 * A dropped partition image: what the file is (size, and the filesystem its superblock names), read from the file.
 * Its files are browsed where the app can mount it, Assemble's image browser.
 */
@Composable
fun LivePartitionExplorerBody(imagePath: String? = null) {
    val facts by produceState<String?>(null, imagePath) {
        value = imagePath?.let { path -> withContext(Dispatchers.IO) { imageFacts(path) } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        if (imagePath == null) {
            PanelNote("Drop a partition image (.img) on the window to see what it is.")
        } else {
            GlassChip(label = "Image: $imagePath", selected = true)
            PanelNote(facts ?: "Reading…")
        }
        PanelNote(
            "To browse its files, put it in the workspace and open Assemble > Image browser > Browse an image…; " +
                "it is mounted read-only there.",
        )
    }
}

/** Size and filesystem of the image at [path], from its own bytes. */
private fun imageFacts(path: String): String {
    val file = File(path)
    if (!file.isFile) return "Not a readable file."
    val head = ByteArray(SPARSE_MAGIC.size)
    val superblock = ByteArray(FsTypes.SUPERBLOCK_BYTES)
    RandomAccessFile(file, "r").use { raf ->
        raf.readFully(head)
        if (raf.length() >= FsTypes.SUPERBLOCK_OFFSET + FsTypes.SUPERBLOCK_BYTES) {
            raf.seek(FsTypes.SUPERBLOCK_OFFSET.toLong())
            raf.readFully(superblock)
        }
    }
    val format = when {
        head.contentEquals(SPARSE_MAGIC) -> "Android sparse image (expand it to read its filesystem)"
        else -> when (FsTypes.fromSuperblock(superblock)) {
            FsType.Erofs -> "EROFS"
            FsType.Ext4 -> "ext4"
            FsType.F2fs -> "F2FS"
            else -> "No EROFS, ext4 or F2FS superblock (a super image, or another format)"
        }
    }
    return "${formatBytes(file.length())} · $format"
}

/** The stock super image's groups and partitions as lpdump reads them (what the phone has room for). */
@Composable
fun LiveSuperLayoutBody() {
    val koin = remember { GlobalContext.getOrNull() }
    val workspace = remember(koin) { koin?.getOrNull<WorkspaceManager>() }?.currentWorkspace?.collectAsState()?.value
    val images = remember(koin) { koin?.getOrNull<ImageWorkbench>() }
    val text by produceState<String?>(null, workspace?.id) {
        val ws = workspace
        value = when {
            ws == null -> "Open a workspace to read its super image."
            images == null -> "This app cannot read super images."
            else -> runCatchingCancellable { describe(images.superLayout(ws.id, stockOnly = true)) }
                .getOrElse { it.message ?: "Could not read the super image." }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        (text ?: "Reading the stock super image…").lines().forEach { PanelNote(it) }
        PanelNote("Build & Sign > Partition layout projects what the next build will need.")
    }
}

private fun describe(layout: SuperLayout): String = buildString {
    appendLine("Super image: ${formatBytes(layout.superSize)}")
    layout.groups.filter { it.maxSize > 0 }.forEach { group ->
        val members = layout.partitions.filter { it.group == group.name }
        val used = members.sumOf { it.size }
        appendLine("${group.name}: ${formatBytes(used)} of ${formatBytes(group.maxSize)}")
        members.filter { it.size > 0 }.forEach { appendLine("  ${it.name}: ${formatBytes(it.size)}") }
    }
}.trimEnd()

/**
 * The workspace's own differences: stock `system/build.prop` against the one in work/, the newest run's timeline and
 * its provenance (verified once per finished run), and the signers of an APK dropped on the window.
 */
@Composable
fun LiveDiffInspectorBody(apkPath: String? = null) {
    val koin = remember { GlobalContext.getOrNull() }
    val workspaceManager = remember(koin) { koin?.getOrNull<WorkspaceManager>() }
    val runRepository = remember(koin) { koin?.getOrNull<RunRepository>() }
    val runner = remember(koin) { koin?.getOrNull<WorkbenchRunner>() }
    val archives = remember(koin) { koin?.getOrNull<FirmwareArchives>() }
    val workspace = workspaceManager?.currentWorkspace?.collectAsState()?.value

    var latestRun by remember { mutableStateOf<BuildRun?>(null) }
    var sbomSummary by remember { mutableStateOf<String?>(null) }
    var props by remember { mutableStateOf<Pair<String, String>?>(null) }
    var propsNote by remember { mutableStateOf<String?>(NO_WORKSPACE) }

    LaunchedEffect(workspace?.id) {
        latestRun = null
        sbomSummary = null
        props = null
        propsNote = NO_WORKSPACE
        val ws = workspace ?: return@LaunchedEffect
        val job = runner ?: return@LaunchedEffect
        val read = runCatchingCancellable {
            job.read(ws.id) { readText(STOCK_BUILD_PROP) to readText(WORK_BUILD_PROP) }
        }
        val (stock, built) = read.getOrDefault(null to null)
        props = if (stock != null && built != null) stock to built else null
        propsNote = when {
            read.isFailure -> "Could not read build.prop: ${read.exceptionOrNull()?.message}"
            stock == null -> "No stock build.prop yet: run the build through Extract."
            built == null -> "No build.prop in work/ yet: run the build through Assemble."
            else -> null
        }
        val repo = runRepository ?: return@LaunchedEffect
        // Verifying rehashes the packages and images: once per finished run, not on every update of the list.
        var verified: Pair<String, RunState>? = null
        repo.observeRuns(ws.id).collect { runs ->
            val found = runs.maxByOrNull { it.startedAt }
            latestRun = found
            val key = found?.let { it.id to it.state }
            if (found == null || found.state.isActive || key == verified) return@collect
            verified = key
            sbomSummary = ProvenanceVerifier.readAndVerify(job, ws.id, found.id).getOrNull()
                ?.let { (manifest, verification) -> BuildProvenanceGenerator.toMarkdownReport(manifest, verification) }
        }
    }

    val apkReport by produceState<String?>(null, apkPath, workspace?.id) {
        val path = apkPath ?: return@produceState
        val ws = workspace
        val job = runner
        val toLinux = archives
        value = if (ws == null || job == null || toLinux == null) {
            "Open a workspace to read $path (it is read inside WSL)."
        } else {
            runCatchingCancellable {
                val linuxPath = toLinux.linuxPathOf(path)
                val printed = job.read(ws.id) { sh(ApkCertInspector.INSPECT_SCRIPT, linuxPath) }
                describe(ApkCertInspector.parseReport(printed))
            }.getOrElse { "Could not read $path: ${it.message}" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        apkPath?.let { path ->
            GlassChip(label = "APK: $path", selected = true)
            (apkReport ?: "Reading its signers…").lines().forEach { PanelNote(it) }
        }
        val pair = props
        if (pair != null) {
            PropertyDiffViewer(
                diffResult = remember(pair) { ConfigDiffEngine.computeDiff(pair.first, pair.second) },
                snapshotName = "Stock firmware → ${workspace?.name ?: "work/"}",
            )
        } else {
            propsNote?.let { PanelNote(it) }
        }

        val run = latestRun
        if (run != null) {
            // No log in this window: choosing a stage (or a step's logs) shows that stage's details.
            var selectedStage by remember(run.id) { mutableStateOf<StageId?>(null) }
            InteractiveBuildTimeline(
                run = run,
                selectedStageId = selectedStage,
                onSelectStage = { selectedStage = it },
                onJumpToLogs = { stage, _ -> selectedStage = stage },
            )
            sbomSummary?.let { summary ->
                Text(
                    text = summary,
                    style = GlassTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun describe(report: ApkSignatureReport): String = when {
    !report.isSigned -> "Not signed: no v1, v2 or v3 signature."
    else -> buildString {
        appendLine("Signature schemes: ${report.schemes.joinToString()}")
        report.certificates.distinctBy { it.scheme to it.sha256 }.forEach {
            appendLine("${it.scheme} signer ${it.sha256}")
            appendLine("  ${it.subject}")
        }
    }.trimEnd()
}

@Composable
private fun PanelNote(text: String) {
    Text(text, style = GlassTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val STOCK_BUILD_PROP = "firmware/extracted/system/system/build.prop"
private const val WORK_BUILD_PROP = "work/system/system/build.prop"
private val SPARSE_MAGIC = byteArrayOf(0x3A, 0xFF.toByte(), 0x26, 0xED.toByte())
private const val NO_WORKSPACE = "Open a workspace to compare its build.prop with the stock firmware's."
