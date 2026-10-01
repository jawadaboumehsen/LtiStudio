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
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.feature.setup.components.CopyableErrorText
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CopyableErrorTextTest {

    private class FakeClipboard : ClipboardManager {
        var copied: AnnotatedString? = null
        override fun getText(): AnnotatedString? = copied
        override fun setText(annotatedString: AnnotatedString) {
            copied = annotatedString
        }
    }

    @Test
    fun `the copy button puts the whole error on the clipboard and confirms it`() = runDesktopComposeUiTest {
        val error = "Connection to run '5a19c348' for 'build:android-tools:cmake' was lost: " +
            "Run '5a19c348' not found on remote server"
        val clipboard = FakeClipboard()
        setContent {
            LtiTheme {
                CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                    CopyableErrorText(error)
                }
            }
        }

        onNodeWithText(error).assertIsDisplayed()
        onNodeWithContentDescription("Copy error").performClick()

        assertEquals(error, clipboard.copied?.text)
        onNodeWithContentDescription("Copied").assertIsDisplayed()
    }
}
