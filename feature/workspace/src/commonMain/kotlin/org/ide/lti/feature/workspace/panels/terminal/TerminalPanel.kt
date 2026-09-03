/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.cli.TerminalLine
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.display.GlassPanelContainer
import org.ide.lti.core.designsystem.component.display.GlassPanelHeader
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.TextFieldLeadingSlot
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TerminalPanel(
    modifier: Modifier = Modifier,
    viewModel: TerminalViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    val monospaceStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = codeFontFamily(),
    )

    LaunchedEffect(uiState.lines.size) {
        if (uiState.lines.isNotEmpty()) {
            listState.animateScrollToItem(uiState.lines.size)
        }
    }

    GlassPanelContainer(
        modifier = modifier,
        header = {
            GlassPanelHeader(title = "Terminal")
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        ) {
            TerminalConsoleContent(
                uiState = uiState,
                listState = listState,
                monospaceStyle = monospaceStyle,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            TerminalInputRow(
                currentInput = uiState.currentInput,
                isRunning = uiState.isRunning,
                monospaceStyle = monospaceStyle,
                onInputChange = viewModel::onInputChange,
                onSubmit = viewModel::onSubmit,
                onCancelRunning = viewModel::onCancelRunning,
                onHistoryUp = viewModel::onHistoryUp,
                onHistoryDown = viewModel::onHistoryDown,
            )
        }
    }
}

@Composable
private fun TerminalConsoleContent(
    uiState: TerminalUiState,
    listState: LazyListState,
    monospaceStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        item(key = "terminal_status_header") {
            TerminalStatusHeader(
                isRunning = uiState.isRunning,
                cwd = uiState.cwd,
            )
        }

        itemsIndexed(
            items = uiState.lines,
            key = { index, _ -> "line_$index" },
        ) { _, line ->
            TerminalLineView(
                line = line,
                monospaceStyle = monospaceStyle,
            )
        }
    }
}

@Composable
private fun TerminalStatusHeader(
    isRunning: Boolean,
    cwd: String,
) {
    val glassColors = GlassTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        modifier = Modifier.padding(bottom = Spacing.ExtraSmall),
    ) {
        Box(
            modifier = Modifier
                .size(ComponentSize.StatusDotSize)
                .clip(GlassShapes.Capsule)
                .background(
                    if (isRunning) glassColors.syntax.keyword else glassColors.glassAccent,
                ),
        )
        Text(
            text = if (isRunning) "running..." else "ready ($cwd)",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = codeFontFamily()),
            color = glassColors.onGlassMuted,
        )
    }
}

@Composable
private fun TerminalLineView(
    line: TerminalLine,
    monospaceStyle: TextStyle,
) {
    val glassColors = GlassTheme.colors
    when (line) {
        is TerminalLine.Command -> {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = "${line.cwd} $",
                    style = monospaceStyle.copy(fontWeight = FontWeight.Bold),
                    color = glassColors.glassAccent,
                )
                Text(
                    text = line.text,
                    style = monospaceStyle,
                    color = glassColors.onGlass,
                )
            }
        }
        is TerminalLine.Output -> {
            Text(
                text = line.text,
                style = monospaceStyle,
                color = glassColors.onGlassSecondary,
            )
        }
        is TerminalLine.Error -> {
            Text(
                text = line.text,
                style = monospaceStyle,
                color = glassColors.syntax.string,
            )
        }
    }
}

@Composable
private fun TerminalInputRow(
    currentInput: String,
    isRunning: Boolean,
    monospaceStyle: TextStyle,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancelRunning: () -> Unit,
    onHistoryUp: () -> Unit,
    onHistoryDown: () -> Unit,
) {
    val glassColors = GlassTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        GlassTextField(
            value = currentInput,
            onValueChange = onInputChange,
            textStyle = monospaceStyle,
            modifier = Modifier
                .weight(1f)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.Enter -> {
                                onSubmit()
                                true
                            }
                            Key.DirectionUp -> {
                                onHistoryUp()
                                true
                            }
                            Key.DirectionDown -> {
                                onHistoryDown()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                },
            placeholder = "Type a command...",
            leading = TextFieldLeadingSlot.Custom {
                Text(
                    text = "$",
                    style = monospaceStyle.copy(fontWeight = FontWeight.Bold),
                    color = glassColors.glassAccent,
                )
            },
        )

        if (isRunning) {
            GlassIconButton(
                onClick = onCancelRunning,
                size = ComponentSize.IconButtonSize,
            ) {
                Icon(
                    painter = AppIcons.ClosePainterResource(),
                    contentDescription = "Cancel running command",
                    modifier = Modifier.size(IconSize.Medium),
                    tint = glassColors.syntax.string,
                )
            }
        }
    }
}
