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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.layout.IdeNavigatorItem
import org.ide.lti.core.designsystem.component.layout.IdeNavigatorPanel
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.feature.workspace.studio.DisplayValue
import org.ide.lti.feature.workspace.studio.TargetContextUi
import org.ide.lti.feature.workspace.studio.WorkspaceSection
import org.ide.lti.feature.workspace.studio.WorkspaceShellUiState

@Composable
public fun WorkspaceNavigator(
    uiState: WorkspaceShellUiState,
    onSelectSection: (WorkspaceSection) -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
) {
    val items = listOf(
        IdeNavigatorItem(
            id = WorkspaceSection.Overview.name,
            label = "Overview",
            icon = "dashboard",
        ),
        IdeNavigatorItem(
            id = WorkspaceSection.RomConfig.name,
            label = "ROM Configuration",
            icon = "settings",
        ),
        IdeNavigatorItem(
            id = WorkspaceSection.RunHistory.name,
            label = "Run History",
            icon = "history",
        ),
        IdeNavigatorItem(
            id = WorkspaceSection.Artifacts.name,
            label = "Artifacts",
            icon = "archive",
        ),
        IdeNavigatorItem(
            id = WorkspaceSection.TargetProfile.name,
            label = "Target Profile",
            icon = "phone",
        ),
    )

    val revisionSubtitle = when (val rev = uiState.targetContext?.revision) {
        is DisplayValue.Available -> rev.text
        is DisplayValue.Unavailable -> rev.reason
        null -> null
    }

    IdeNavigatorPanel(
        headerTitle = "WORKSPACE",
        headerSubtitle = revisionSubtitle,
        items = items,
        selectedItemId = uiState.selectedWorkspaceSection?.name,
        onSelectItem = { itemId ->
            val section = WorkspaceSection.entries.firstOrNull { it.name == itemId }
            if (section != null) {
                onSelectSection(section)
            }
        },
        modifier = modifier.testTag("WorkspaceNavigator"),
        colors = colors,
        footerContent = {
            WorkspaceNavigatorFooter(
                targetContext = uiState.targetContext,
                colors = colors,
            )
        },
    )
}

@Composable
private fun WorkspaceNavigatorFooter(
    targetContext: TargetContextUi?,
    colors: IdeShellColorScheme,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small)
            .testTag("WorkspaceNavigatorFooter"),
    ) {
        HorizontalDivider(
            thickness = IdeShellDimens.HairlineBorder,
            color = colors.border,
            modifier = Modifier.padding(bottom = Spacing.Small),
        )
        Text(
            text = "TARGET CONTEXT",
            color = colors.textDim,
            fontFamily = ideFontFamily(),
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

        val profileText = when (val p = targetContext?.profile) {
            is DisplayValue.Available -> p.text
            is DisplayValue.Unavailable -> p.reason
            null -> "Unavailable"
        }
        Text(
            text = "Profile: $profileText",
            color = colors.textMuted,
            fontFamily = codeFontFamily(),
        )
    }
}
