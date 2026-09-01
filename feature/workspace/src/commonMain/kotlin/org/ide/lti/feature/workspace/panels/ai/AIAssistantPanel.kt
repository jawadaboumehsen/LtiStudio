/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.GlassPanelContainer
import org.ide.lti.core.designsystem.component.GlassPanelHeader
import org.ide.lti.core.designsystem.component.GlassPanelHeaderAction
import org.ide.lti.core.designsystem.component.GlassPrimaryButton
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.ui.GlassChatInput
import org.ide.lti.core.ui.GlassMessageBubble
import org.ide.lti.core.ui.GlassSmallChip
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AIAssistantPanel(
    modifier: Modifier = Modifier,
    viewModel: AIAssistantViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    GlassPanelContainer(
        modifier = modifier,
        header = {
            GlassPanelHeader(
                title = "AI Assistant",
                actions = {
                    GlassPanelHeaderAction(
                        icon = AppIcon.Painted(AppIcons.ClearPainterResource),
                        contentDescription = "Clear chat",
                        onClick = { viewModel.clearHistory() },
                    )
                },
            )
        },
    ) {
        when (val state = uiState) {
            is AIAssistantUiState.Empty -> {
                EmptyAIChatView(
                    onSendMessage = { message ->
                        viewModel.sendMessage(message)
                    },
                )
            }

            is AIAssistantUiState.Chat -> {
                ChatView(
                    messages = state.messages,
                    isGenerating = state.isGenerating,
                    onSendMessage = { message ->
                        viewModel.sendMessage(message)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is AIAssistantUiState.Error -> {
                ErrorView(
                    message = state.message,
                    onRetry = { viewModel.retry() },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyAIChatView(
    onSendMessage: (String) -> Unit,
) {
    val glassColors = GlassTheme.colors
    val quickActions = remember {
        listOf(
            "Explain code",
            "Find bugs",
            "Generate tests",
            "Refactor",
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.Medium),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
            ) {
                Box(
                    modifier = Modifier
                        .size(ComponentSize.TopBarButtonSize)
                        .clip(CircleShape)
                        .background(glassColors.glassSurfaceElevated.copy(alpha = AlphaTokens.Faint))
                        .border(
                            StrokeWidth.Hairline,
                            glassColors.glassBorder,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Sparkles,
                        contentDescription = null,
                        tint = glassColors.glassAccent,
                        modifier = Modifier.size(IconSize.Medium),
                    )
                }

                Text(
                    text = "Ready to assist",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = glassColors.onGlass,
                )

                Text(
                    text = "Ask anything about your code",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassColors.onGlassMuted,
                )

                Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                    modifier = Modifier.padding(horizontal = Spacing.Small),
                ) {
                    quickActions.forEach { action ->
                        GlassSmallChip(
                            label = action,
                            onClick = { onSendMessage(action) },
                        )
                    }
                }
            }
        }

        GlassChatInput(
            onSendMessage = onSendMessage,
            enabled = true,
        )
    }
}

@Composable
private fun ChatView(
    messages: List<org.ide.lti.core.model.ai.Message>,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.padding(Spacing.Medium)) {
        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            items(messages, key = { it.id }) { message ->
                GlassMessageBubble(
                    content = message.content,
                    role = message.role,
                    timestamp = message.timestamp,
                )
                Spacer(modifier = Modifier.height(Spacing.SmallMedium))
            }

            if (isGenerating) {
                item {
                    Row(
                        modifier = Modifier.padding(vertical = Spacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            color = GlassTheme.colors.glassAccent,
                            modifier = Modifier
                                .padding(end = Spacing.Small)
                                .width(IconSize.Medium)
                                .height(IconSize.Medium),
                            strokeWidth = StrokeWidth.Focused,
                        )
                        Text(
                            text = "Generating...",
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassTheme.colors.onGlassSecondary,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.Small))
        HorizontalDivider(
            color = GlassTheme.colors.glassBorder.copy(alpha = AlphaTokens.Faint),
            thickness = StrokeWidth.Hairline,
        )
        Spacer(modifier = Modifier.height(Spacing.Small))

        // Input field
        GlassChatInput(
            onSendMessage = onSendMessage,
            enabled = !isGenerating,
        )
    }
}

@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(Spacing.Medium))
            GlassPrimaryButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
