/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.core.ui.command

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.TextFieldLeadingSlot
import org.ide.lti.core.designsystem.component.inputs.TextFieldTrailingSlot
import org.ide.lti.core.designsystem.component.layout.GlassRole
import org.ide.lti.core.designsystem.component.layout.glassContainer
import org.ide.lti.core.designsystem.component.layout.glassSurface
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.command.CommandPaletteRegistry
import org.ide.lti.core.domain.command.StudioCommand

/**
 * Universal Intent Command Palette modal overlay for LtiRom Studio (Ctrl+Shift+P / Cmd+K).
 * Provides fuzzy intent search, keyboard navigation, categorized suggestions, and instant action dispatch.
 */
@Composable
fun CommandPaletteModal(
    isOpen: Boolean,
    registry: CommandPaletteRegistry,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isOpen) return

    var query by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val filteredCommands = remember(query, registry) { registry.search(query) }

    LaunchedEffect(filteredCommands.size) {
        if (selectedIndex >= filteredCommands.size) {
            selectedIndex = maxOf(0, filteredCommands.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                handleCommandPaletteKeyEvent(
                    event = event,
                    commands = filteredCommands,
                    selectedIndex = selectedIndex,
                    onDismiss = onDismiss,
                    onUpdateIndex = { selectedIndex = it },
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        // Scrim backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = AlphaTokens.Scrim))
                .clickable { onDismiss() },
        )

        // Floating Modal Container
        Column(
            modifier = Modifier
                .padding(top = Spacing.ExtraExtraLarge * 2)
                .fillMaxWidth(AlphaTokens.Elevated * 1.5f)
                .glassSurface(role = GlassRole.TextCard, shape = GlassShapes.HazeLarge)
                .padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            GlassTextField(
                value = query,
                onValueChange = {
                    query = it
                    selectedIndex = 0
                },
                placeholder = "Type a command or intent (e.g. 'Flash', 'Extract', 'Copilot', 'Settings')...",
                modifier = Modifier.fillMaxWidth(),
                focusRequester = focusRequester,
                leading = TextFieldLeadingSlot.Icon(icon = AppIcon.Painted(AppIcons.SearchPainterResource)),
                trailing = if (query.isNotEmpty()) {
                    TextFieldTrailingSlot.Clear {
                        query = ""
                        selectedIndex = 0
                    }
                } else {
                    TextFieldTrailingSlot.None
                },
            )

            if (filteredCommands.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.Large),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No matching commands found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = Spacing.ExtraExtraLarge * 12)
                        .glassContainer(scrollState = listState),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                ) {
                    itemsIndexed(filteredCommands, key = { _, item -> item.id }) { index, command ->
                        CommandItem(
                            command = command,
                            isSelected = index == selectedIndex,
                            onClick = {
                                onDismiss()
                                command.action()
                            },
                        )
                    }
                }
            }

            CommandPaletteFooter(commandCount = filteredCommands.size)
        }
    }
}

@Composable
private fun CommandItem(command: StudioCommand, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = AlphaTokens.Hover)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = AlphaTokens.UltraFaint)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.Small)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = command.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Box(
                    modifier = Modifier
                        .clip(GlassShapes.Pill)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AlphaTokens.Muted))
                        .padding(horizontal = Spacing.Compact, vertical = Spacing.ExtraExtraSmall),
                ) {
                    Text(
                        text = command.category.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = command.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val shortcut = command.shortcut
        if (shortcut != null) {
            Box(
                modifier = Modifier
                    .clip(GlassShapes.ExtraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = AlphaTokens.Half))
                    .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
            ) {
                Text(
                    text = shortcut,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = codeFontFamily(),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun CommandPaletteFooter(commandCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.ExtraSmall, start = Spacing.Small, end = Spacing.Small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$commandCount available commands",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "↑↓ to navigate • ↵ to select • esc to close",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun handleCommandPaletteKeyEvent(
    event: KeyEvent,
    commands: List<StudioCommand>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onUpdateIndex: (Int) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.Escape -> {
            onDismiss()
            true
        }
        Key.DirectionDown -> {
            if (commands.isNotEmpty()) {
                onUpdateIndex((selectedIndex + 1) % commands.size)
            }
            true
        }
        Key.DirectionUp -> {
            if (commands.isNotEmpty()) {
                onUpdateIndex(if (selectedIndex <= 0) commands.size - 1 else selectedIndex - 1)
            }
            true
        }
        Key.Enter -> {
            if (commands.isNotEmpty() && selectedIndex in commands.indices) {
                val cmd = commands[selectedIndex]
                onDismiss()
                cmd.action()
            }
            true
        }
        else -> false
    }
}
