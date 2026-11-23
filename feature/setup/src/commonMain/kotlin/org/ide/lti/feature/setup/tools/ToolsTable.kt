/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.layout.glassContainer
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.setup.ToolGroupId
import kotlin.jvm.JvmName

@JvmName("ToolsTableWithId")
@Composable
fun ToolsTable(
    rows: List<ToolRowPresentation>,
    selectedGroupId: ToolGroupId?,
    onSelectGroup: (ToolGroupId) -> Unit,
    modifier: Modifier = Modifier,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onOpenInspector: () -> Unit = {},
    onCloseInspector: () -> Unit = {},
) {
    ToolsTable(
        rows = rows,
        selectedGroupId = selectedGroupId?.value,
        onSelectGroup = onSelectGroup,
        modifier = modifier,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onOpenInspector = onOpenInspector,
        onCloseInspector = onCloseInspector,
    )
}

@Composable
fun ToolsTable(
    rows: List<ToolRowPresentation>,
    selectedGroupId: String?,
    onSelectGroup: (ToolGroupId) -> Unit,
    modifier: Modifier = Modifier,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onOpenInspector: () -> Unit = {},
    onCloseInspector: () -> Unit = {},
) {
    val lazyListState = rememberLazyListState()
    LazyColumn(
        state = lazyListState,
        modifier =
        modifier
            .fillMaxWidth()
            .heightIn(max = ComponentSize.MaxPanelHeight)
            .glassContainer(scrollState = lazyListState),
        contentPadding = PaddingValues(bottom = Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        items(
            items = rows,
            key = { it.groupId },
            contentType = { "ToolGroupRow" },
        ) { row ->
            val isSelected = row.groupId == selectedGroupId
            ToolGroupRow(
                row = row,
                isSelected = isSelected,
                onClick = { onSelectGroup(ToolGroupId(row.groupId)) },
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onOpenInspector = onOpenInspector,
                onCloseInspector = onCloseInspector,
            )
        }
    }
}
