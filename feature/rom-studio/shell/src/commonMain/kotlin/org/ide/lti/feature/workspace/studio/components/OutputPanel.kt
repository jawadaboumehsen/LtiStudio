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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.workbench.LogLevel
import org.ide.lti.core.domain.workbench.LogLine
import org.ide.lti.core.domain.workbench.WorkbenchLog

/**
 * The workbench jobs' output under the Studio's content: one line with the latest entry and a count per level, or,
 * opened, every line, filtered by level, with Copy and Clear.
 */
@Composable
internal fun OutputPanel(log: WorkbenchLog, modifier: Modifier = Modifier) {
    val lines by log.lines.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf<LogLevel?>(null) }
    val counts = remember(lines) { lines.groupingBy { it.level }.eachCount() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.ExtraSmall)
            .testTag("OutputPanel"),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.testTag("OutputToggle"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = (if (open) "▾ " else "▸ ") + "Output",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = lines.lastOrNull()?.let { "${it.level}  ${it.text}" } ?: "No workbench job has run yet.",
                color = lines.lastOrNull()?.let { levelColor(it.level) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = codeFontFamily(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            LogLevel.entries.forEach { level ->
                Text("${counts[level] ?: 0} $level", color = levelColor(level), fontSize = FontSize.Micro)
            }
        }
        if (open) OpenOutput(log, lines, filter, counts) { filter = it }
    }
}

@Composable
private fun OpenOutput(
    log: WorkbenchLog,
    lines: List<LogLine>,
    filter: LogLevel?,
    counts: Map<LogLevel, Int>,
    onFilter: (LogLevel?) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val shown = remember(lines, filter) { if (filter == null) lines else lines.filter { it.level == filter } }
    val list = rememberLazyListState()
    // New lines keep the end in view.
    LaunchedEffect(shown.size) { if (shown.isNotEmpty()) list.scrollToItem(shown.lastIndex) }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small), verticalAlignment = Alignment.CenterVertically) {
        GlassChip(label = "All ${lines.size}", selected = filter == null, onClick = { onFilter(null) })
        LogLevel.entries.forEach { level ->
            GlassChip(
                label = "$level ${counts[level] ?: 0}",
                selected = filter == level,
                onClick = { onFilter(level) },
                modifier = Modifier.testTag("OutputFilter_$level"),
            )
        }
        GlassButton(
            onClick = { clipboard.setText(AnnotatedString(shown.joinToString("\n") { line(it) })) },
            variant = GlassButtonVariant.Text,
        ) { Text("Copy", fontSize = FontSize.BodySmall) }
        GlassButton(
            onClick = log::clear,
            variant = GlassButtonVariant.Text,
        ) { Text("Clear", fontSize = FontSize.BodySmall) }
    }
    LazyColumn(state = list, modifier = Modifier.fillMaxWidth().height(ComponentSize.MinPanelHeight * 2)) {
        items(shown) { entry ->
            Text(
                text = line(entry),
                color = levelColor(entry.level),
                fontSize = FontSize.Micro,
                fontFamily = codeFontFamily(),
            )
        }
    }
}

private fun line(entry: LogLine): String = "${entry.localTime} ${entry.level} ${entry.text}"

@Composable
private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.OK -> GlassTheme.diagnosticColors.success
    LogLevel.INF -> MaterialTheme.colorScheme.onSurfaceVariant
    LogLevel.WRN -> GlassTheme.diagnosticColors.warning
    LogLevel.ERR -> GlassTheme.diagnosticColors.error
}
