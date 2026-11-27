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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.feature.setup.components.CopyableErrorText
import org.junit.Test
import java.awt.datatransfer.DataFlavor
import kotlin.test.assertEquals

@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class CopyableErrorTextTest {

    private class FakeClipboard : Clipboard {
        var copied: ClipEntry? = null
        override suspend fun getClipEntry(): ClipEntry? = copied
        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            copied = clipEntry
        }
    }

    @Test
    fun `the copy button puts the whole error on the clipboard and confirms it`() = runDesktopComposeUiTest {
        val error = "Connection to run '5a19c348' for 'build:android-tools:cmake' was lost: " +
            "Run '5a19c348' not found on remote server"
        val clipboard = FakeClipboard()
        setContent {
            LtiTheme {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    CopyableErrorText(error)
                }
            }
        }

        onNodeWithText(error).assertIsDisplayed()
        onNodeWithContentDescription("Copy error").performClick()
        waitForIdle()

        val copiedText = clipboard.copied?.asAwtTransferable?.getTransferData(DataFlavor.stringFlavor) as? String
        assertEquals(error, copiedText)
        onNodeWithContentDescription("Copied").assertIsDisplayed()
    }
}
