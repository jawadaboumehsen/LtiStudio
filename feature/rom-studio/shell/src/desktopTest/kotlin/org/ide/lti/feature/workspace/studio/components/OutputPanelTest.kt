/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.workbench.LogLevel
import org.ide.lti.core.domain.workbench.WorkbenchLog
import org.junit.Rule
import org.junit.Test

class OutputPanelTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun followsTheTailAtCapacityAndKeepsAnOlderRowInViewWhileReading() {
        val log = WorkbenchLog(now = { 0 })
        log.add(LogLevel.INF, (1..WorkbenchLog.MAX).joinToString("\n") { "Entry [$it]" })
        compose.setContent {
            LtiTheme(appTheme = AppTheme.Dark, effectsEnabled = false) { OutputPanel(log) }
        }
        compose.onNodeWithTag("OutputToggle").performClick()
        awaitLine("Entry [${WorkbenchLog.MAX}]")

        log.add(LogLevel.INF, "New tail")
        awaitLine("New tail")

        compose.onNodeWithTag("WorkbenchOutputLines").performScrollToIndex(80)
        val anchor = "Entry [${WorkbenchLog.MAX - 79}]"
        awaitLine(anchor)

        log.add(LogLevel.INF, "Next tail")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Next tail", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(logLine(anchor)).assertIsDisplayed()
    }

    private fun awaitLine(text: String) {
        val matcher = logLine(text)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(matcher).assertIsDisplayed()
    }

    private fun logLine(text: String) =
        hasAnyAncestor(hasTestTag("WorkbenchOutputLines")) and hasText(text, substring = true)
}
