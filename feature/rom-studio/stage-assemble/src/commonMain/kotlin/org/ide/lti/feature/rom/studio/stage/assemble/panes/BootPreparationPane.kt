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
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AssemblySettings

/**
 * How the boot-chain images are prepared: copied from the extracted firmware into `work/kernel/`, with
 * their stock AVB footer erased or kept. They are re-signed with the workspace AVB key in Build & Sign.
 */
@Suppress("UnusedParameter")
@Composable
public fun BootPreparationPane(
    settings: AssemblySettings,
    target: TargetDevice,
    onChange: (AssemblySettings) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("BootPreparationPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Card("Boot-chain images") {
            Hint("Copied from the extracted firmware to work/kernel/ (from the device's boot partitions).")
            target.bootPartitions.forEach { partition ->
                Text(
                    text = "$partition.img",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.BodySmall,
                    fontFamily = codeFontFamily(),
                )
            }
        }
        Card("Stock AVB footer") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                FOOTER_POLICIES.forEach { (value, label) ->
                    GlassChip(
                        label = label,
                        selected = settings.bootFooterPolicy.equals(value, ignoreCase = true),
                        onClick = { onChange(settings.copy(bootFooterPolicy = value)) },
                    )
                }
            }
            Hint(
                if (settings.bootFooterPolicy.equals("erase", ignoreCase = true)) {
                    "The vendor footer is removed (avbtool erase_footer); Build & Sign adds a new one signed " +
                        "with the workspace AVB key. Recommended."
                } else {
                    "The vendor footer is kept as extracted. Build & Sign still re-signs the images."
                },
            )
        }
    }
}

private val FOOTER_POLICIES = listOf("erase" to "Erase (recommended)", "preserve" to "Preserve")

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.BodySmall,
        fontFamily = ideFontFamily(),
    )
}
