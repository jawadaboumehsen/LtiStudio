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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.layout.IdeNavigatorItem
import org.ide.lti.core.designsystem.component.layout.IdeNavigatorPanel
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
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
    iconsOnly: Boolean = false,
) {
    val items = listOf(
        IdeNavigatorItem(
            id = WorkspaceSection.Overview.name,
            label = "Overview",
            icon = "dashboard",
        ),
        IdeNavigatorItem(
            id = WorkspaceSection.RomConfig.name,
            label = "ROM configuration",
            icon = "settings",
        ),
        IdeNavigatorItem(
            id = WorkspaceSection.RunHistory.name,
            label = "Run history",
            icon = "history",
        ),
        IdeNavigatorItem(
            id = WorkspaceSection.Artifacts.name,
            label = "Artifacts",
            icon = "archive",
        ),
        IdeNavigatorItem(
            id = WorkspaceSection.TargetProfile.name,
            label = "Target profile",
            icon = "phone",
        ),
    )

    val revisionSubtitle = when (val rev = uiState.targetContext?.revision) {
        is DisplayValue.Available ->
            rev.text
                .replace("Target ", "")
                .replace("rev-", "v")
                .replace("rev ", "v")
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
        footerContent = {
            WorkspaceNavigatorFooter(
                targetContext = uiState.targetContext,
            )
        },
        iconsOnly = iconsOnly,
    )
}

@Composable
internal fun WorkspaceNavigatorFooter(targetContext: TargetContextUi?, modifier: Modifier = Modifier) {
    val profileText = when (val p = targetContext?.profile) {
        is DisplayValue.Available -> p.text
        is DisplayValue.Unavailable -> p.reason
        null -> "5.15.137-android14"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Small, vertical = Spacing.Small)
            .testTag("WorkspaceNavigatorFooter"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.ShellControl)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    width = GlassDimens.HairlineBorder,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = GlassShapes.ShellControl,
                )
                .padding(Spacing.Small),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Active Kernel Spec",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = FontSize.Micro,
                        fontFamily = ideFontFamily(),
                        fontWeight = FontWeight.Medium,
                    )
                    Box(
                        modifier = Modifier
                            .size(IconSize.Indicator)
                            .clip(GlassShapes.Circle)
                            .background(GlassTheme.diagnosticColors.success),
                    )
                }

                Text(
                    text = if (profileText.contains("android") || profileText.contains(".")) {
                        profileText
                    } else {
                        "5.15.137-android14"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = codeFontFamily(),
                )

                HorizontalDivider(
                    thickness = GlassDimens.HairlineBorder,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = Spacing.Hairline),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "GKI compliant",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = FontSize.Micro,
                        fontFamily = ideFontFamily(),
                    )
                    Text(
                        text = "Yes",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = FontSize.Micro,
                        fontFamily = ideFontFamily(),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
