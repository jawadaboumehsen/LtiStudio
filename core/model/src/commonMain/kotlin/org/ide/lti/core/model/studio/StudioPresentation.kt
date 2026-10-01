/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.studio

import kotlinx.serialization.Serializable
import org.ide.lti.core.model.run.StageId

@Serializable
public data class ScrollAnchor(val anchorId: String, val offset: Int = 0)

/**
 * Transient and persisted presentation state for ROM Setup Studio per workspace.
 *
 * Load-bearing architectural constraint: this is presentation state only. It is NEVER part of a
 * ConfigurationSnapshot, a stage cache key, or any digest; losing it must cost the user nothing but their
 * scroll position.
 *
 * [navigatorWidth] is clamped to 200..360 on read and on write.
 */
@Serializable
public data class StudioPresentation(
    val workspaceId: String,
    val hasOpened: Boolean = false,
    val editorStageId: StageId? = null,
    val editorObjectId: String? = null,
    val scrollAnchors: Map<String, ScrollAnchor> = emptyMap(),
    val expandedSections: Map<String, Set<String>> = emptyMap(),
    val navigatorWidth: Int = DEFAULT_NAVIGATOR_WIDTH,
    val lastFocusId: String? = null,
    val inOverview: Boolean = (editorStageId == null),
) {
    public companion object {
        public const val MIN_NAVIGATOR_WIDTH: Int = 200
        public const val MAX_NAVIGATOR_WIDTH: Int = 360
        public const val DEFAULT_NAVIGATOR_WIDTH: Int = 240
    }
}
