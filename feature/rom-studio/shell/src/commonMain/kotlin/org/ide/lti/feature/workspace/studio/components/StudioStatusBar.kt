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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.layout.IdeStatusBar
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.feature.workspace.studio.ActivityUiState
import org.ide.lti.feature.workspace.studio.ConnectionUiState
import org.ide.lti.feature.workspace.studio.DisplayValue
import org.ide.lti.feature.workspace.studio.TargetContextUi
import org.ide.lti.feature.workspace.studio.WorkspaceShellUiState

@Composable
public fun StudioStatusBar(
    uiState: WorkspaceShellUiState,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
    onOpenProblems: (() -> Unit)? = null,
    onOpenActivity: (() -> Unit)? = null,
) {
    IdeStatusBar(
        modifier = modifier.testTag("StudioStatusBar"),
        colors = colors,
        leadingContent = {
            if (uiState.targetContext != null) {
                StatusBarWorkspaceTarget(targetContext = uiState.targetContext, colors = colors)
            }
            StatusBarProblems(
                problemCount = uiState.problemCount,
                colors = colors,
                onOpenProblems = onOpenProblems,
            )
            StatusBarActivity(
                activity = uiState.activity,
                colors = colors,
                onOpenActivity = onOpenActivity,
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                StatusBarConnection(connection = uiState.connection, colors = colors)
                Text(
                    text = "|",
                    color = colors.borderMuted,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "Design concept · Sample data",
                    color = colors.textDim,
                    fontFamily = ideFontFamily(),
                )
            }
        },
    )
}

@Composable
private fun StatusBarWorkspaceTarget(targetContext: TargetContextUi, colors: IdeShellColorScheme) {
    val targetText = when (val t = targetContext.targetId) {
        is DisplayValue.Available -> t.text
        is DisplayValue.Unavailable -> t.reason
    }
    val profileText = when (val p = targetContext.profile) {
        is DisplayValue.Available -> p.text
        is DisplayValue.Unavailable -> p.reason
    }
    val revText = when (val r = targetContext.revision) {
        is DisplayValue.Available -> r.text
        is DisplayValue.Unavailable -> r.reason
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        modifier = Modifier.testTag("StatusBar_WorkspaceTarget"),
    ) {
        Icon(
            imageVector = Icons.Default.Storage,
            contentDescription = null,
            modifier = Modifier.size(IconSize.Small),
            tint = colors.textDim,
        )
        Text(
            text = "Workspace: $targetText / $profileText • Target revision: $revText",
            color = colors.textMuted,
            fontFamily = ideFontFamily(),
            fontSize = FontSize.Micro,
        )
    }
    Text(
        text = "|",
        color = colors.borderMuted,
        fontFamily = ideFontFamily(),
        fontSize = FontSize.Micro,
    )
}

@Composable
private fun StatusBarProblems(problemCount: Int, colors: IdeShellColorScheme, onOpenProblems: (() -> Unit)?) {
    val problemsClickModifier = if (onOpenProblems != null) {
        Modifier
            .clickable(onClick = onOpenProblems)
            .semantics { role = Role.Button }
    } else {
        Modifier
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        modifier = Modifier
            .testTag("StatusBar_Problems")
            .then(problemsClickModifier),
    ) {
        if (problemCount > 0) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(IconSize.Small),
                tint = colors.warningAmber,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(IconSize.Small),
                tint = colors.accentGreen,
            )
        }
        Text(
            text = "Problems: $problemCount",
            color = if (problemCount > 0) colors.warningAmber else colors.textDim,
            fontFamily = codeFontFamily(),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StatusBarActivity(activity: ActivityUiState, colors: IdeShellColorScheme, onOpenActivity: (() -> Unit)?) {
    val activityClickModifier = if (onOpenActivity != null) {
        Modifier
            .clickable(onClick = onOpenActivity)
            .semantics { role = Role.Button }
    } else {
        Modifier
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        modifier = Modifier
            .testTag("StatusBar_Activity")
            .then(activityClickModifier),
    ) {
        Icon(
            imageVector = Icons.Default.Circle,
            contentDescription = null,
            modifier = Modifier.size(IconSize.Indicator),
            tint = colors.textDim,
        )
        val activityText = when (activity) {
            is ActivityUiState.Idle -> "idle"
            is ActivityUiState.Active -> activity.description
        }
        Text(
            text = "Activity: $activityText",
            color = colors.textDim,
            fontFamily = codeFontFamily(),
        )
    }
}

@Composable
private fun StatusBarConnection(connection: ConnectionUiState, colors: IdeShellColorScheme) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        modifier = Modifier.testTag("StatusBar_Connection"),
    ) {
        val (connColor, connText) = when (connection) {
            is ConnectionUiState.Connected -> colors.accentGreen to "Connected"
            is ConnectionUiState.Connecting -> colors.warningAmber to "Connecting..."
            is ConnectionUiState.Disconnected -> colors.textDim to "Disconnected"
        }
        Icon(
            imageVector = Icons.Default.Circle,
            contentDescription = null,
            modifier = Modifier.size(IconSize.Indicator),
            tint = connColor,
        )
        Text(
            text = connText,
            color = colors.textMuted,
            fontFamily = ideFontFamily(),
        )
    }
}
