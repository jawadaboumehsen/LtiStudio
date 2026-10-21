/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.assemble.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AssemblySettings

/**
 * Whether the built ROM keeps system_ext as its own partition or merges it into system
 * (`work/system/system/system_ext`, with its sidecar entries re-prefixed). The modes are exactly the ones the
 * Assemble stage accepts.
 */
@Suppress("UnusedParameter")
@Composable
public fun SystemExtHandlingPane(
    settings: AssemblySettings,
    target: TargetDevice,
    onChange: (AssemblySettings) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = settings.systemExtMode.lowercase()
    val autoResult = if (target.hasStandaloneSystemExt) "kept separate" else "merged into system"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium)
            .testTag("SystemExtHandlingPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = "In the built ROM",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            MODES.forEach { (value, label) ->
                GlassChip(
                    label = label,
                    selected = mode == value,
                    onClick = { onChange(settings.copy(systemExtMode = value)) },
                )
            }
        }
        Text(
            text = when (mode) {
                "standalone" -> "system_ext stays its own partition (its own image in super)."
                "fold" -> "system_ext is merged into system; no system_ext image is built."
                else -> "Follows the device: it ${if (target.hasStandaloneSystemExt) "has" else "has no"} " +
                    "separate system_ext partition, so it is $autoResult."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            fontFamily = ideFontFamily(),
        )
        Text(
            text = "Changing this re-runs Assemble and every later stage on the next build.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )
    }
}

private val MODES = listOf(
    "auto" to "Follow the device (recommended)",
    "standalone" to "Keep separate",
    "fold" to "Merge into system",
)
