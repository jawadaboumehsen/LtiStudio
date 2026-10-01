/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassPanelContainer
import org.ide.lti.core.designsystem.component.display.GlassPanelHeader
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.testing.screenshot.GoldenImageAssert
import org.junit.Test

/**
 * Visual screenshot regression tests for theme-aware syntax highlighting.
 */
class GlassSyntaxScreenshotTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassSyntaxHighlightingDark() = runDesktopComposeUiTest(width = 540, height = 320) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center,
                ) {
                    SyntaxHighlightedCodeCard()
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_syntax_highlighting_dark", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassSyntaxHighlightingLight() = runDesktopComposeUiTest(width = 540, height = 320) {
        setContent {
            LtiTheme(appTheme = AppTheme.Light) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center,
                ) {
                    SyntaxHighlightedCodeCard()
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_syntax_highlighting_light", image)
    }

    @Composable
    private fun SyntaxHighlightedCodeCard() {
        val syntax = GlassTheme.syntaxColors

        GlassCard(
            modifier = Modifier.fillMaxSize(),
            contentPadding = Spacing.None,
        ) {
            GlassPanelContainer(
                header = {
                    GlassPanelHeader(
                        title = "SyntaxHighlightSample.kt",
                        icon = AppIcons.File,
                    )
                },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.SmallMedium),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val lines = listOf(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = syntax.annotation)) { append("@Composable") }
                            },
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = syntax.keyword)) { append("fun ") }
                                withStyle(SpanStyle(color = syntax.function)) { append("formatPrice") }
                                withStyle(SpanStyle(color = syntax.plain)) { append("(") }
                                withStyle(SpanStyle(color = syntax.plain)) { append("amount: ") }
                                withStyle(SpanStyle(color = syntax.type)) { append("Double") }
                                withStyle(SpanStyle(color = syntax.plain)) { append("): ") }
                                withStyle(SpanStyle(color = syntax.type)) { append("String") }
                                withStyle(SpanStyle(color = syntax.plain)) { append(" {") }
                            },
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = syntax.comment)) {
                                    append("    // Calculate tax and return formatted string")
                                }
                            },
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = syntax.plain)) { append("    ") }
                                withStyle(SpanStyle(color = syntax.keyword)) { append("val ") }
                                withStyle(SpanStyle(color = syntax.plain)) { append("tax = amount * ") }
                                withStyle(SpanStyle(color = syntax.number)) { append("0.15") }
                            },
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = syntax.plain)) { append("    ") }
                                withStyle(SpanStyle(color = syntax.keyword)) { append("return ") }
                                withStyle(SpanStyle(color = syntax.string)) { append("\"Total: \${amount + tax}\"") }
                            },
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = syntax.plain)) { append("}") }
                            },
                        )

                        lines.forEachIndexed { index, line ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.Hairline),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                            ) {
                                Text(
                                    text = "${index + 1}".padStart(2, ' '),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = AlphaTokens.Half,
                                        ),
                                    ),
                                )
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.5.sp,
                                        color = syntax.plain,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
