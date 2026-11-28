/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui.desktop

import androidx.compose.runtime.staticCompositionLocalOf
import org.ide.lti.core.domain.desktop.BackgroundSessionManager
import org.ide.lti.core.domain.desktop.WorkspaceDropCoordinator

/** Tool panels that can be detached from the main IDE window onto secondary monitors. */
enum class DetachablePanelId(val title: String) {
    LOGCAT_COPILOT("Live Logcat & Copilot"),
    PARTITION_EXPLORER("Partition Explorer"),
    DIFF_INSPECTOR("Diff & Provenance Inspector"),
}

/** Callback allowing shared feature shells to request opening or focusing a detached desktop window. */
fun interface DetachablePanelOpener {
    fun open(panel: DetachablePanelId, argument: String?)
}

val LocalBackgroundSessionManager = staticCompositionLocalOf<BackgroundSessionManager?> { null }
val LocalWorkspaceDropCoordinator = staticCompositionLocalOf<WorkspaceDropCoordinator?> { null }
val LocalDetachablePanelOpener = staticCompositionLocalOf<DetachablePanelOpener?> { null }
val LocalSessionDrawerController = staticCompositionLocalOf<(() -> Unit)?> { null }
