/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
internal fun runSettingsUiTest(width: Int, height: Int, block: suspend DesktopComposeUiTest.() -> Unit) =
    runDesktopComposeUiTest(width = width, height = height, testTimeout = 30.seconds) {
        // Render only the frames the test needs. Glass can request another draw during capture.
        mainClock.autoAdvance = false
        block()
    }

/**
 * Reveal a Settings control with one whole-pixel layout change. The semantics scroll action
 * animates over many glass frames, which is unnecessary when testing layout or capturing a still.
 */
@OptIn(ExperimentalTestApi::class)
internal fun DesktopComposeUiTest.scrollToSettingsNode(
    tag: String,
    scrollState: ScrollState,
): SemanticsNodeInteraction {
    val interaction = onNodeWithTag(tag)
    val node = interaction.fetchSemanticsNode()
    val scrollParent = checkNotNull(
        generateSequence(node.parent) { it.parent }
            .firstOrNull { hasScrollAction().matches(it) },
    ) {
        "No scroll container found for Settings control $tag"
    }
    val viewport = scrollParent.boundsInRoot
    val top = node.positionInRoot.y
    val height = node.size.height
    val bottom = top + height
    val delta = when {
        height > viewport.height || top < viewport.top -> top - viewport.top
        bottom > viewport.bottom -> bottom - viewport.bottom
        else -> 0f
    }
    if (delta != 0f) {
        val pixels = if (delta > 0f) ceil(delta) else floor(delta)
        runOnUiThread { scrollState.dispatchRawDelta(pixels) }
        mainClock.advanceTimeByFrame()
    }
    return interaction
}
