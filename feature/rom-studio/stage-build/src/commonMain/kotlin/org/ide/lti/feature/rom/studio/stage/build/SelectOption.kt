/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.build

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenu
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenuItem
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.GlassIcon
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.ideFontFamily

/** One choice of a [SelectField]: what it stores, how it reads, and a line saying what it does. */
internal data class SelectOption<T>(val value: T, val label: String, val detail: String? = null)

/**
 * A compact select: the chosen option's label and a chevron; a click opens the options, each with its
 * explanation. Disabled, it shows [disabledText] instead (why the choice does not apply).
 */
@Composable
internal fun <T> SelectField(
    selected: T,
    options: List<SelectOption<T>>,
    onSelect: (T) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    disabledText: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == selected }
    val enabled = disabledText == null
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.Medium)
                .glassOutlineBorder(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    width = GlassDimens.HairlineBorder,
                    shape = GlassShapes.Medium,
                )
                .alpha(if (enabled) 1f else AlphaTokens.Disabled)
                .then(if (enabled) Modifier.clickable { open = true } else Modifier)
                .semantics {
                    role = Role.DropdownList
                    contentDescription = current?.label ?: disabledText.orEmpty()
                }
                .padding(horizontal = Spacing.Small, vertical = Spacing.Compact)
                .testTag(tag),
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (enabled) current?.label.orEmpty() else disabledText.orEmpty(),
                color = MaterialTheme.colorScheme.run { if (enabled) onSurface else onSurfaceVariant },
                fontSize = FontSize.BodySmall,
                fontFamily = ideFontFamily(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (enabled) GlassIcon(icon = AppIcons.ChevronDown, modifier = Modifier.size(IconSize.Small))
        }
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                GlassDropdownMenuItem(
                    text = option.label,
                    subtitle = option.detail,
                    isSelected = option.value == selected,
                    onClick = {
                        open = false
                        onSelect(option.value)
                    },
                    modifier = Modifier.testTag("$tag:${option.label}"),
                )
            }
        }
    }
}
