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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.ide.lti.core.model.workspace.Workspace

/**
 * Opt-in deterministic benchmark fixture driver configuration (T031).
 */
object SetupBenchmarkConfig {
    /**
     * Test-only override for benchmark active flag.
     * When null, delegates to [isSetupBenchmarkActive].
     */
    var overrideBenchmarkActive: Boolean? = null

    /**
     * Optional destination path for saving segment markers.
     * When null, defaults to project root / user dir setup-performance-markers.json.
     */
    var customMarkerFilePath: String? = null

    /**
     * Optional duration in ms for warmup (default: 10,000ms = 10s).
     */
    var warmupDurationMs: Long = 10_000L

    /**
     * Optional duration in ms for each segment (default: 10,000ms = 10s).
     */
    var segmentDurationMs: Long = 10_000L

    /**
     * True if benchmark mode is currently active.
     */
    fun isBenchmarkActive(): Boolean {
        overrideBenchmarkActive?.let { return it }
        return isSetupBenchmarkActive()
    }
}

/**
 * Expect platform hook to determine if LTI_SETUP_BENCHMARK environment variable is set to "1".
 */
expect fun isSetupBenchmarkActive(): Boolean

/**
 * Opt-in deterministic fixture driver hook invoked by SetupScreen (T031, SC-004).
 *
 * ONLY active when [SetupBenchmarkConfig.isBenchmarkActive] is true (i.e. LTI_SETUP_BENCHMARK=1).
 * Default startup remains completely unchanged when the variable is absent.
 *
 * When active:
 * - Preloads 100 recents, 100 tool fixtures, and 10,000 activity lines at presentation boundary.
 * - Warms the UI for 10 seconds.
 * - Executes a repeatable 30-second cycle:
 *   - 0-10s Workspaces scroll
 *   - 10-20s Tools scroll
 *   - 20-30s Environment activity scroll
 *   with continuous scrolling including direction reversals before list ends.
 * - Emits start/end monotonic timestamps per segment.
 * - Provides a keyboard-accessible "Start capture scenario" action.
 */
@Composable
expect fun SetupPerformanceScenarioHook(
    modifier: Modifier = Modifier,
    onOpenWorkspace: (Workspace) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onCreateWorkspace: () -> Unit = {},
    onOpenConfigurations: () -> Unit = {},
    onOpenManageTargets: () -> Unit = onOpenConfigurations,
    onOpenHelp: () -> Unit = {},
    viewModel: SetupViewModel? = null,
    defaultContent: @Composable () -> Unit,
)
