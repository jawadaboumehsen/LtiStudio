/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.acquire.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Top contextual header for Acquire subobject screens.
 * Displays title, subtitle, target device info, and primary Validate/Save action buttons.
 */
@Composable
public fun AcquireHeader(
    title: String,
    subtitle: String,
    targetSpec: String = "PQ84P01 / Development",
    targetRevision: String = "Target revision 4",
    additionalContext: String? = null,
    onValidate: () -> Unit = {},
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        // Line 1: Breadcrumb (left) and Target Spec (right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Text(
                    text = "ROM Setup",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.BodySmall,
                    fontFamily = GlassFontFamily.ide(),
                )
                Text(
                    text = "/",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.BodySmall,
                    fontFamily = GlassFontFamily.ide(),
                )
                Text(
                    text = "Acquire",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.BodySmall,
                    fontFamily = GlassFontFamily.ide(),
                )
                Text(
                    text = "/",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.BodySmall,
                    fontFamily = GlassFontFamily.ide(),
                )
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GlassFontFamily.ide(),
                )
            }

            Text(
                text = targetSpec,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GlassFontFamily.ide(),
            )
        }

        // Line 2: Title (left) and Revision + Action buttons (right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.TitleLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = GlassFontFamily.ide(),
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
                        fontFamily = GlassFontFamily.ide(),
                    )
                    if (additionalContext != null) {
                        Text(
                            text = additionalContext,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = FontSize.Micro,
                            fontFamily = GlassFontFamily.ide(),
                        )
                    }
                }

                GlassButton(
                    onClick = onValidate,
                    variant = GlassButtonVariant.Standard,
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

                GlassButton(
                    onClick = onSave,
                    variant = GlassButtonVariant.Primary,
                    shape = GlassShapes.ShellControl,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.ExtraSmall),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Save",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = FontSize.BodySmall,
                        )
                    }
                }
            }
        }

        // Line 3: Subtitle
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            fontFamily = GlassFontFamily.ide(),
        )
    }
}
