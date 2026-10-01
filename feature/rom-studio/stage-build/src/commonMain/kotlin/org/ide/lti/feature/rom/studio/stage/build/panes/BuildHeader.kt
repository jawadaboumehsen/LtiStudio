/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.build.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.ideFontFamily

/**
 * Contextual top header for Build subobject panes.
 * Displays breadcrumbs, title, subtitle, target configuration context, and action buttons.
 */
@Suppress("LongParameterList")
@Composable
public fun BuildHeader(
    title: String,
    subtitle: String,
    targetSpec: String = "PQ84P01 / Development",
    targetRevision: String = "Target revision 4",
    additionalContext: String? = null,
    saveButtonLabel: String = "Save",
    saveButtonIcon: ImageVector = Icons.Default.Save,
    validateIsPrimary: Boolean = false,
    onValidate: () -> Unit = {},
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        BuildHeaderLeft(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.weight(1f),
        )

        BuildHeaderRight(
            targetSpec = targetSpec,
            targetRevision = targetRevision,
            additionalContext = additionalContext,
            saveButtonLabel = saveButtonLabel,
            saveButtonIcon = saveButtonIcon,
            validateIsPrimary = validateIsPrimary,
            onValidate = onValidate,
            onSave = onSave,
        )
    }
}

@Composable
private fun BuildHeaderLeft(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.Hairline),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                text = "ROM Setup",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "/",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "Build",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "/",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontWeight = FontWeight.Medium,
                fontFamily = ideFontFamily(),
            )
        }

        Spacer(Modifier.height(Spacing.ExtraSmall))

        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleLarge,
            fontWeight = FontWeight.Bold,
            fontFamily = ideFontFamily(),
        )

        Spacer(Modifier.height(Spacing.Hairline))

        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            fontFamily = ideFontFamily(),
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun BuildHeaderRight(
    targetSpec: String,
    targetRevision: String,
    additionalContext: String?,
    saveButtonLabel: String,
    saveButtonIcon: ImageVector,
    validateIsPrimary: Boolean,
    onValidate: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Text(
            text = targetSpec,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = targetRevision,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
                if (additionalContext != null) {
                    Text(
                        text = additionalContext,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = FontSize.Micro,
                        fontFamily = ideFontFamily(),
                    )
                }
            }

            val validateVariant = if (validateIsPrimary) {
                GlassButtonVariant.Primary
            } else {
                GlassButtonVariant.Standard
            }
            GlassButton(
                onClick = onValidate,
                variant = validateVariant,
                shape = GlassShapes.ShellControl,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.ExtraSmall),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Validate",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = FontSize.BodySmall,
                    )
                }
            }

            val saveVariant = if (validateIsPrimary) {
                GlassButtonVariant.Standard
            } else {
                GlassButtonVariant.Primary
            }
            GlassButton(
                onClick = onSave,
                variant = saveVariant,
                shape = GlassShapes.ShellControl,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Icon(
                        imageVector = saveButtonIcon,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.ExtraSmall),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = saveButtonLabel,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = FontSize.BodySmall,
                    )
                }
            }
        }
    }
}
