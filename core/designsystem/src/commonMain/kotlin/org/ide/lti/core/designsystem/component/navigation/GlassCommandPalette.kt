/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * VS Code-style searchable command palette. Filters [commands] by title/subtitle as the user
 * types, grouped by [CommandCategory]; executing an item invokes [CommandItem.onExecute] and
 * dismisses the palette.
 */
@Composable
public fun GlassCommandPalette(isOpen: Boolean, onDismissRequest: () -> Unit, commands: List<CommandItem>) {
    if (!isOpen) return

    var query by remember { mutableStateOf("") }
    LaunchedEffect(isOpen) { query = "" }

    val filtered = remember(query, commands) {
        if (query.isBlank()) {
            commands
        } else {
            commands.filter {
                it.title.contains(query, ignoreCase = true) || it.subtitle.contains(query, ignoreCase = true)
            }
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.category } }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        GlassCard(
            modifier = Modifier
                .heightIn(max = ComponentSize.DialogDefaultHeight)
                .padding(Spacing.ExtraLarge),
            contentPadding = Spacing.Small,
        ) {
            Column {
                GlassTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Type a command...",
                    modifier = Modifier.fillMaxWidth().padding(Spacing.Small),
                )
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    grouped.forEach { (category, items) ->
                        item(key = "header_${category.name}") {
                            Text(
                                text = category.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.ExtraSmall),
                            )
                        }
                        items(items, key = { it.id }) { command ->
                            CommandRow(command = command, onExecute = {
                                command.onExecute()
                                onDismissRequest()
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandRow(command: CommandItem, onExecute: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onExecute,
            )
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
    ) {
        when (command.icon) {
            is AppIcon.Vector -> Icon(
                imageVector = command.icon.imageVector,
                contentDescription = null,
                modifier = Modifier.size(IconSize.Medium),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is AppIcon.Painted -> Icon(
                painter = command.icon.painter(),
                contentDescription = null,
                modifier = Modifier.size(IconSize.Medium),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = command.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = command.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (command.shortcut != null) {
            Text(
                text = command.shortcut,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
