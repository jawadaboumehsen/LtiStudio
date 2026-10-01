/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.theme.SetupTokens
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolCategory
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolScope
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceType
import java.io.File

/**
 * Desktop platform implementation checking the LTI_SETUP_BENCHMARK environment variable.
 */
actual fun isSetupBenchmarkActive(): Boolean {
    return System.getenv("LTI_SETUP_BENCHMARK") == "1"
}

/**
 * Monotonic timestamp and relative second markers for each segment in the capture scenario.
 */
data class BenchmarkSegmentMarker(
    val name: String,
    val startSeconds: Double,
    val endSeconds: Double,
    val startNanos: Long,
    val endNanos: Long,
)

/**
 * Full scenario marker recording written to JSON for setup-performance.ps1 ingestion.
 */
data class BenchmarkMarkersPayload(
    val scenarioStartNanos: Long,
    val scenarioEndNanos: Long,
    val captureStart: Double,
    val captureEnd: Double,
    val segments: List<BenchmarkSegmentMarker>,
)

/**
 * Opt-in deterministic benchmark fixture driver actual hook (T031, SC-004).
 */
@Composable
actual fun SetupPerformanceScenarioHook(
    modifier: Modifier,
    onOpenWorkspace: (Workspace) -> Unit,
    onOpenSettings: () -> Unit,
    onCreateWorkspace: () -> Unit,
    onOpenConfigurations: () -> Unit,
    onOpenManageTargets: () -> Unit,
    onOpenHelp: () -> Unit,
    viewModel: SetupViewModel?,
    defaultContent: @Composable () -> Unit,
) {
    if (!SetupBenchmarkConfig.isBenchmarkActive()) {
        defaultContent()
    } else {
        SetupPerformanceScenarioDriver(
            modifier = modifier,
        )
    }
}

/**
 * Benchmark scenario driver component running synthetic fixtures and automated scroll cycle.
 */
@Composable
fun SetupPerformanceScenarioDriver(
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    // 1. Synthetic Fixture Data Preloaded at Presentation Boundary
    val benchmarkRecents = remember { createBenchmarkRecents() }
    val benchmarkTools = remember { createBenchmarkTools() }
    val benchmarkLogs = remember { createBenchmarkLogs(12_000) }

    var currentTab by remember { mutableStateOf(SetupTab.PROJECTS) }
    var selectedCockpitTab by remember { mutableStateOf("console") }
    var selectedWorkspaceId by remember { mutableStateOf<String?>(benchmarkRecents.firstOrNull()?.workspaceId) }
    var activeOperation by remember { mutableStateOf<SetupOperation?>(null) }

    val workspacesListState = rememberLazyListState()
    val toolsScrollState = rememberScrollState()
    val activityListState = rememberLazyListState()
    val activityState = remember { SetupActivityState(scope = coroutineScope, maxBufferSize = 10_000) }

    LaunchedEffect(Unit) {
        activityState.setLogs(benchmarkLogs)
    }

    // 2. Scenario Lifecycle and State
    var warmupRemainingSeconds by remember { mutableStateOf((SetupBenchmarkConfig.warmupDurationMs / 1000).toInt()) }
    var isRunningScenario by remember { mutableStateOf(false) }
    var activeSegmentName by remember { mutableStateOf<String?>(null) }
    var isCompleted by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Preloaded 100 recents, 100 tools, 12k logs (capped at 10k)") }

    // Warmup countdown
    LaunchedEffect(Unit) {
        while (warmupRemainingSeconds > 0 && !isRunningScenario) {
            delay(1000)
            warmupRemainingSeconds--
        }
    }

    // Scenario execution function
    val startScenario: () -> Unit = {
        if (!isRunningScenario) {
            isRunningScenario = true
            isCompleted = false
            coroutineScope.launch {
                println("[SetupBenchmark] Starting 30-second capture scenario...")
                val scenarioStartNanos = System.nanoTime()
                val segmentDuration = SetupBenchmarkConfig.segmentDurationMs
                val markers = mutableListOf<BenchmarkSegmentMarker>()

                // Segment 1: Workspaces Scroll (0 - 10s)
                currentTab = SetupTab.PROJECTS
                activeSegmentName = "Workspaces"
                statusMessage = "Segment 1/3: Scrolling Workspaces (0-10s)"
                val seg1StartNanos = System.nanoTime()
                println("[SetupBenchmark] Segment 'Workspaces' started at monotonic nanos: $seg1StartNanos")

                val seg1Job = launch {
                    var direction = 1f
                    while (isActive) {
                        workspacesListState.scrollBy(15f * direction)
                        val firstVisible = workspacesListState.firstVisibleItemIndex
                        if (firstVisible >= 75) {
                            direction = -1f
                        } else if (firstVisible == 0 && workspacesListState.firstVisibleItemScrollOffset == 0) {
                            direction = 1f
                        }
                        delay(16)
                    }
                }
                delay(segmentDuration)
                seg1Job.cancel()
                val seg1EndNanos = System.nanoTime()
                println("[SetupBenchmark] Segment 'Workspaces' ended at monotonic nanos: $seg1EndNanos")
                markers.add(
                    BenchmarkSegmentMarker(
                        name = "Workspaces",
                        startSeconds = 0.0,
                        endSeconds = 10.0,
                        startNanos = seg1StartNanos,
                        endNanos = seg1EndNanos,
                    ),
                )

                // Segment 2: Tools Scroll (10 - 20s)
                currentTab = SetupTab.TOOLS
                activeSegmentName = "Tools"
                statusMessage = "Segment 2/3: Scrolling Tools (10-20s)"
                val seg2StartNanos = System.nanoTime()
                println("[SetupBenchmark] Segment 'Tools' started at monotonic nanos: $seg2StartNanos")

                val seg2Job = launch {
                    var direction = 1f
                    while (isActive) {
                        toolsScrollState.scrollBy(12f * direction)
                        val maxScroll = toolsScrollState.maxValue
                        val currentVal = toolsScrollState.value
                        if (maxScroll > 0 && currentVal >= maxScroll - 40) {
                            direction = -1f
                        } else if (currentVal <= 10) {
                            direction = 1f
                        }
                        delay(16)
                    }
                }
                delay(segmentDuration)
                seg2Job.cancel()
                val seg2EndNanos = System.nanoTime()
                println("[SetupBenchmark] Segment 'Tools' ended at monotonic nanos: $seg2EndNanos")
                markers.add(
                    BenchmarkSegmentMarker(
                        name = "Tools",
                        startSeconds = 10.0,
                        endSeconds = 20.0,
                        startNanos = seg2StartNanos,
                        endNanos = seg2EndNanos,
                    ),
                )

                // Segment 3: Environment Activity Scroll & Preview Modal (20 - 30s)
                currentTab = SetupTab.ENVIRONMENT
                selectedCockpitTab = "console"
                activeSegmentName = "Environment"
                statusMessage = "Segment 3/3: Scrolling Environment Activity (20-30s)"
                val seg3StartNanos = System.nanoTime()
                println("[SetupBenchmark] Segment 'Environment' started at monotonic nanos: $seg3StartNanos")

                val seg3Job = launch {
                    var direction = 1f
                    var streamCounter = 12_000
                    var tick = 0
                    while (isActive) {
                        activityListState.scrollBy(20f * direction)
                        val firstVisible = activityListState.firstVisibleItemIndex
                        if (firstVisible >= 9500) {
                            direction = -1f
                        } else if (firstVisible == 0 && activityListState.firstVisibleItemScrollOffset == 0) {
                            direction = 1f
                        }
                        // Periodically feed additional lines to exercise bounded steady-cap retention while scrolling
                        tick++
                        if (tick % 60 == 0) {
                            activityState.appendLines(
                                List(25) {
                                    val padded = (streamCounter++).toString().padStart(5, '0')
                                    "[$padded] [STREAM] Live benchmark event while scrolling"
                                },
                            )
                        }
                        delay(16)
                    }
                }

                // Modal Preview open/close cycle inside Segment 3 (open at 60%, hold for 25% of segment duration)
                val modalOpenDelay = (segmentDuration * 0.6).toLong()
                val modalDisplayDuration = (segmentDuration * 0.25).toLong()
                val remainingSegmentDuration = segmentDuration - modalOpenDelay - modalDisplayDuration

                delay(modalOpenDelay)
                activeOperation = createBenchmarkPreviewOperation()
                statusMessage = "Segment 3/3: Exercising Provisioning Preview Modal"
                delay(modalDisplayDuration)
                activeOperation = null
                statusMessage = "Segment 3/3: Resuming Environment Activity Scroll"
                if (remainingSegmentDuration > 0) {
                    delay(remainingSegmentDuration)
                }

                seg3Job.cancel()
                val seg3EndNanos = System.nanoTime()
                println("[SetupBenchmark] Segment 'Environment' ended at monotonic nanos: $seg3EndNanos")
                markers.add(
                    BenchmarkSegmentMarker(
                        name = "Environment",
                        startSeconds = 20.0,
                        endSeconds = 30.0,
                        startNanos = seg3StartNanos,
                        endNanos = seg3EndNanos,
                    ),
                )

                val scenarioEndNanos = System.nanoTime()
                isRunningScenario = false
                isCompleted = true
                activeSegmentName = null
                statusMessage = "Scenario completed. Markers recorded."

                // Save markers to file
                val payload = BenchmarkMarkersPayload(
                    scenarioStartNanos = scenarioStartNanos,
                    scenarioEndNanos = scenarioEndNanos,
                    captureStart = 0.0,
                    captureEnd = 30.0,
                    segments = markers,
                )
                saveMarkersToFile(payload)
            }
        }
    }

    // Compose synthetic UI state and actions
    val syntheticUiState = SetupUiState(
        toolchainSetupState = ToolchainSetupState(
            toolsMatrix = benchmarkTools,
            logs = benchmarkLogs,
        ),
        recentProjects = benchmarkRecents,
        filteredProjects = benchmarkRecents,
        searchQuery = "",
        selectedWorkspaceId = selectedWorkspaceId,
        selectedCockpitTab = selectedCockpitTab,
        toolSearchQuery = "",
        toolSelection = ToolSelection(),
        filteredToolsMatrix = benchmarkTools,
        isAutoDoctorEnabled = true,
        selectedTab = currentTab,
        selectedDestination = SetupDestination.fromId(currentTab.id),
        environmentNotice = null,
        activeOperation = activeOperation,
    )

    val toggleModalPreview: () -> Unit = {
        activeOperation = if (activeOperation == null) {
            createBenchmarkPreviewOperation()
        } else {
            null
        }
    }

    val syntheticUiActions = SetupUiActions(
        onSearchQueryChange = {},
        onRemoveRecentProject = {},
        onOpenFolder = {},
        onRecentProjectClick = {},
        onSelectWorkspace = { selectedWorkspaceId = it },
        onCheckEnvironment = {},
        onRetrySetupStep = {},
        onProvisionAvbKey = {},
        onSelectTarget = {},
        onAutoRemediateDoctor = {},
        onSelectCockpitTab = { selectedCockpitTab = it },
        onToolSearchQueryChange = {},
        onSelectToolCategory = {},
        onTestTool = {},
        onRecompileTool = {},
        onNavigateToEnvironment = { currentTab = SetupTab.ENVIRONMENT },
        onOpenSettings = {},
        onSelectTab = { currentTab = it },
        onSelectDestination = { dest ->
            when (dest) {
                SetupDestination.WORKSPACES -> currentTab = SetupTab.PROJECTS
                SetupDestination.ENVIRONMENT -> currentTab = SetupTab.ENVIRONMENT
                SetupDestination.TOOLS -> currentTab = SetupTab.TOOLS
                SetupDestination.RECOVERY -> currentTab = SetupTab.RECOVERY
            }
        },
        onDismissEnvironmentNotice = {},
        onSetAutoDoctorEnabled = {},
        onResetToolchainCache = {},
        onCreateWorkspace = {},
        onOpenManageTargets = {},
        onOpenHelp = {},
        onPreviewSetup = {
            activeOperation = createBenchmarkPreviewOperation()
        },
        onConfirmOperation = {
            activeOperation = null
        },
        onDismissPreview = {
            activeOperation = null
        },
        onRetryStage = {},
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    event.isCtrlPressed &&
                    event.isShiftPressed
                ) {
                    when (event.key) {
                        Key.B -> {
                            startScenario()
                            true
                        }
                        Key.P -> {
                            toggleModalPreview()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        SetupScreenContent(
            state = syntheticUiState,
            actions = syntheticUiActions,
            activityState = activityState,
            workspacesListState = workspacesListState,
            toolsScrollState = toolsScrollState,
            activityListState = activityListState,
        )

        // Benchmark HUD Banner Overlay
        GlassCard(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = Spacing.ExtraLarge, end = Spacing.Large),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.SmallMedium),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassChip(
                        label = "BENCHMARK HARNESS",
                        selected = true,
                    )
                    if (warmupRemainingSeconds > 0 && !isRunningScenario && !isCompleted) {
                        Text(
                            text = "Warmup: ${warmupRemainingSeconds}s",
                            fontSize = SetupTokens.TextMetadata,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (isRunningScenario) {
                        Text(
                            text = "Running: ${activeSegmentName ?: ""}",
                            fontSize = SetupTokens.TextMetadata,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (isCompleted) {
                        Text(
                            text = "Completed (30s)",
                            fontSize = SetupTokens.TextMetadata,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Text(
                    text = statusMessage,
                    fontSize = SetupTokens.TextMetadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassPrimaryButton(
                        onClick = startScenario,
                        enabled = !isRunningScenario,
                        modifier = Modifier.semantics {
                            contentDescription = "Start capture scenario"
                        },
                    ) {
                        Text(
                            text = if (isRunningScenario) "Scenario Running..." else "Start capture scenario",
                            fontSize = SetupTokens.TextBody,
                        )
                    }

                    Text(
                        text = "Ctrl+Shift+B",
                        fontSize = SetupTokens.TextMetadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Generates 100 synthetic recents for benchmarking without mutating real database.
 */
fun createBenchmarkRecents(): List<RecentProject> {
    val baseInstant = Instant.fromEpochMilliseconds(1726185600000L) // Fixed timestamp for determinism
    return List(100) { i ->
        val id = "bench-workspace-${i + 1}"
        val device = when (i % 4) {
            0 -> "Pixel 8 Pro (husky)"
            1 -> "Pixel 7a (lynx)"
            2 -> "Generic System Image (arm64)"
            else -> "AOSP Automotive (car_x86_64)"
        }
        RecentProject(
            workspaceId = id,
            name = "ROM Workspace ${i + 1}",
            path = "C:/AndroidRoms/workspace_${i + 1}",
            lastOpened = Instant.fromEpochMilliseconds(baseInstant.toEpochMilliseconds() - i * 3600_000L),
            type = WorkspaceType.LOCAL,
            targetDisplayName = device,
        )
    }
}

/**
 * Generates 100 synthetic tool items across 6 categories for benchmarking.
 */
fun createBenchmarkTools(): List<ToolComponentItem> {
    val categories = ToolCategory.values()
    return List(100) { i ->
        val category = categories[i % categories.size]
        val toolId = "tool_${i + 1}"
        ToolComponentItem(
            id = toolId,
            name = "Android ROM Tool ${i + 1}",
            category = category,
            binaryName = "$toolId.bin",
            scope = ToolScope.WSL2,
            version = "2.${i % 10}.0",
            path = "/usr/local/bin/$toolId.bin",
            status = if (i % 12 == 3) StepStatus.FAILED else StepStatus.SUCCESS,
            isCore = i < 20,
            capabilities = listOf("inspect", "unpack", "repack"),
            lastTested = "2026-09-12 10:00:00",
        )
    }
}

/**
 * Generates synthetic log lines for bounded activity viewport benchmarking (default: 12,000 to exercise steady cap).
 */
fun createBenchmarkLogs(count: Int = 12_000): List<String> {
    return List(count) { i ->
        val padded = i.toString().padStart(5, '0')
        when (i % 5) {
            0 -> "[$padded] [INFO] Subsystem kernel readiness validated on WSL2 instance"
            1 -> "[$padded] [BUILD] Compiling native binary partition-tools-$i target: [OK]"
            2 -> "[$padded] [WARNING] Minor headroom delta detected on /dev/sdb, 14200MB available"
            3 -> "[$padded] [STAGE] Pre-flight system doctor verified 24 diagnostic rules"
            else -> "[$padded] [SUCCESS] Completed step execution with zero exit code"
        }
    }
}

/**
 * Creates a synthetic confirmable SetupOperation preview for performance benchmarking.
 */
fun createBenchmarkPreviewOperation(): SetupOperation {
    return SetupOperation(
        operationId = "bench-op-preview-01",
        kind = SetupOperationKind.FULL_SETUP,
        plannedChanges = PlannedSetupChanges(
            packagesToInstall = listOf("cmake", "ninja-build", "ccache"),
            submodulesToSync = listOf("android-tools", "erofs-utils"),
            toolsToCompile = listOf("adb", "fastboot", "mkfs.erofs"),
            toolsToPublish = listOf("adb", "fastboot", "mkfs.erofs"),
            requiresElevation = true,
            elevationReason = "Package installation and udev rule deployment",
            autoDoctorRemediation = true,
        ),
        executionState = SetupOperationState.PREVIEW,
    )
}

/**
 * Writes the captured segment markers to a deterministic JSON file.
 */
fun saveMarkersToFile(payload: BenchmarkMarkersPayload) {
    val filePath = SetupBenchmarkConfig.customMarkerFilePath
        ?: File(System.getProperty("user.dir"), "setup-performance-markers.json").absolutePath
    val file = File(filePath)
    val parent = file.parentFile
    if (parent != null && !parent.exists()) {
        parent.mkdirs()
    }

    val segmentsJson = payload.segments.joinToString(",\n") { seg ->
        """    {
      "name": "${seg.name}",
      "start": ${seg.startSeconds},
      "end": ${seg.endSeconds},
      "startNanos": ${seg.startNanos},
      "endNanos": ${seg.endNanos}
    }"""
    }

    val jsonContent = """{
  "scenarioStartNanos": ${payload.scenarioStartNanos},
  "scenarioEndNanos": ${payload.scenarioEndNanos},
  "captureStart": ${payload.captureStart},
  "captureEnd": ${payload.captureEnd},
  "segments": [
$segmentsJson
  ]
}"""

    file.writeText(jsonContent)
    println("[SetupBenchmark] Marker file successfully written to: ${file.absolutePath}")
}
