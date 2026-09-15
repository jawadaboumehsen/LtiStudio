/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.TextFieldLeadingSlot
import org.ide.lti.core.designsystem.component.inputs.TextFieldTrailingSlot
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.model.run.StageId

/**
 * Object Navigator for the current pipeline stage in ROM Setup Studio.
 *
 * Features:
 * - Breadcrumb navigation showing stage index and title with a quick link to Overview.
 * - Live search filtering stage subobjects by title and accessible label (Ctrl+F reachable).
 * - Per-object validation error badges distinguishable without color alone (icons and textual counts).
 * - [LazyColumn] rendering adhering to Constitution IV.
 * - Full keyboard accessibility (focusable, Enter / Space activatable).
 */
@Composable
public fun StageObjectNavigator(
    currentStage: StudioStageDestination,
    selectedObjectId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectObject: (StageId, String) -> Unit,
    onOpenOverview: () -> Unit,
    validationErrors: List<ValidationError>,
    searchFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors

    val filteredSubobjects = if (searchQuery.isBlank()) {
        currentStage.subobjects
    } else {
        currentStage.subobjects.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.accessibleLabel.contains(searchQuery, ignoreCase = true) ||
                it.id.contains(searchQuery, ignoreCase = true)
        }
    }

    CompositionLocalProvider(LocalContentColor provides glassColors.onGlass) {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
        ) {
            // Breadcrumb header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Stage ${currentStage.position}: ${currentStage.title}",
                        style = MaterialTheme.typography.titleSmall,
                        color = glassColors.onGlass,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassButton(
                    onClick = onOpenOverview,
                    variant = GlassButtonVariant.Standard,
                    shape = GlassShapes.Compact,
                    minWidth = Spacing.None,
                    minHeight = ComponentSize.TopBarButtonSize,
                    contentPadding = PaddingValues(
                        horizontal = Spacing.Compact,
                        vertical = Spacing.ExtraExtraSmall,
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = "Switch to ROM Setup Studio Overview"
                        role = Role.Button
                    },
                ) {
                    Text(
                        text = "Overview",
                        style = MaterialTheme.typography.labelSmall,
                        color = glassColors.onGlassSecondary,
                    )
                }
            }

            // Search text field
            GlassTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = "Search objects (Ctrl+F)...",
                leading = TextFieldLeadingSlot.Icon(AppIcons.Search),
                trailing = if (searchQuery.isNotEmpty()) {
                    TextFieldTrailingSlot.Clear { onSearchQueryChange("") }
                } else {
                    TextFieldTrailingSlot.None
                },
                focusRequester = searchFocusRequester,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(Spacing.Small))

            // Subobjects list (Constitution IV: always LazyColumn for variable-length lists)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                if (filteredSubobjects.isEmpty()) {
                    item(key = "empty-search-state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.Large),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No objects matching \"$searchQuery\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = glassColors.onGlassMuted,
                            )
                        }
                    }
                }

                items(
                    items = filteredSubobjects,
                    key = { it.id },
                ) { subobject ->
                    val isSelected = subobject.id == selectedObjectId
                    val matchingErrors = validationErrors.filter {
                        it.stageId == currentStage.stageId && it.objectId == subobject.id
                    }
                    val errorCount = matchingErrors.count { it.severity == Severity.ERROR }
                    val warningCount = matchingErrors.count { it.severity == Severity.WARNING }

                    StageObjectRow(
                        subobject = subobject,
                        isSelected = isSelected,
                        errorCount = errorCount,
                        warningCount = warningCount,
                        onClick = { onSelectObject(currentStage.stageId, subobject.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StageObjectRow(
    subobject: StudioSubobject,
    isSelected: Boolean,
    errorCount: Int,
    warningCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors
    val shape = GlassShapes.Compact

    val backgroundColor = when {
        isSelected -> glassColors.glassAccent.copy(alpha = AlphaTokens.Hover)
        else -> glassColors.glassSurfaceElevated.copy(alpha = AlphaTokens.UltraFaint)
    }

    val borderColor = when {
        isSelected -> glassColors.glassBorderHighlight.copy(alpha = AlphaTokens.Prominent)
        else -> glassColors.glassBorder.copy(alpha = AlphaTokens.Faint)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = borderColor,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Small)
            .semantics {
                contentDescription = subobject.accessibleLabel
                role = Role.Tab
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = subobject.title,
            style = if (isSelected) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            color = if (isSelected) glassColors.onGlass else glassColors.onGlassSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(Spacing.Small))

        // Status badge: distinguishable WITHOUT color alone (different shapes/text)
        when {
            errorCount > 0 -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                    modifier = Modifier
                        .clip(GlassShapes.Pill)
                        .background(glassColors.diagnostics.error.copy(alpha = AlphaTokens.Hover))
                        .padding(horizontal = Spacing.Compact, vertical = Spacing.ExtraExtraSmall),
                ) {
                    Icon(
                        painter = AppIcons.InfoPainterResource(),
                        contentDescription = "$errorCount validation errors",
                        modifier = Modifier.size(IconSize.Small),
                        tint = glassColors.diagnostics.error,
                    )
                    Text(
                        text = "$errorCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = glassColors.diagnostics.error,
                    )
                }
            }
            warningCount > 0 -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                    modifier = Modifier
                        .clip(GlassShapes.Pill)
                        .background(glassColors.diagnostics.warning.copy(alpha = AlphaTokens.Hover))
                        .padding(horizontal = Spacing.Compact, vertical = Spacing.ExtraExtraSmall),
                ) {
                    Icon(
                        painter = AppIcons.InfoPainterResource(),
                        contentDescription = "$warningCount validation warnings",
                        modifier = Modifier.size(IconSize.Small),
                        tint = glassColors.diagnostics.warning,
                    )
                    Text(
                        text = "$warningCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = glassColors.diagnostics.warning,
                    )
                }
            }
            else -> {
                Icon(
                    painter = AppIcons.CheckPainterResource(),
                    contentDescription = "Configuration valid",
                    modifier = Modifier.size(IconSize.Small),
                    tint = glassColors.diagnostics.success,
                )
            }
        }
    }
}
