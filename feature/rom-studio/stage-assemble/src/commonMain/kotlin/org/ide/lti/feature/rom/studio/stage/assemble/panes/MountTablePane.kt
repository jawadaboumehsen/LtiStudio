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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.feature.rom.studio.stage.assemble.BootPaneState

/**
 * The mount table (`fstab`) of the work tree. Turning off data encryption removes the encryption flags of the
 * `/data` line from every fstab that mounts it.
 */
@Composable
public fun MountTablePane(
    settings: AssemblySettings,
    onChange: (AssemblySettings) -> Unit,
    modifier: Modifier = Modifier,
    boot: BootPaneState = BootPaneState(),
    onAddMountLines: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium)
            .testTag("MountTablePane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = "Mount table",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassToggle(
                checked = settings.disableDataEncryption,
                onCheckedChange = { onChange(settings.copy(disableDataEncryption = it)) },
                activeColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("DisableDataEncryptionToggle"),
            )
            Text(
                text = "Turn off data encryption",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontFamily = ideFontFamily(),
            )
        }
        Text(
            text = "Removes fileencryption, metadata_encryption, keydirectory and the older forceencrypt flags " +
                "from the /data line of every fstab in work/ (vendor/etc/fstab.*). The build stops if no fstab " +
                "mounts /data.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            fontFamily = ideFontFamily(),
        )
        Text(
            text = "Partitions built as ext4 or F2FS",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )
        Text(
            text = "The phone mounts each partition with a line of its fstab for that filesystem. Build & Sign adds " +
                "the missing lines to vendor/etc/fstab.* itself; the first-stage fstab is inside vendor_boot, so " +
                "the build stops until it has them. This adds an EROFS, an ext4 and an F2FS line for every dynamic " +
                "partition to it and saves vendor_boot as an override (stock lines stay first).",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            fontFamily = ideFontFamily(),
        )
        GlassButton(
            onClick = onAddMountLines,
            enabled = boot.busy == null,
            modifier = Modifier.testTag("AddMountLinesButton"),
        ) {
            Text(text = boot.busy ?: "Add mount lines to vendor_boot", fontSize = FontSize.BodySmall)
        }
        boot.message?.let {
            Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = FontSize.BodySmall)
        }
        boot.refusal?.let {
            Text(
                text = "${it.cause} ${it.nextStep}",
                color = GlassTheme.diagnosticColors.error,
                fontSize = FontSize.BodySmall,
            )
        }
        if (settings.disableDataEncryption) {
            Text(
                text = "Format data in recovery when you first flash this ROM: the phone cannot read the data the " +
                    "old encryption wrote and will not boot until it is formatted. Afterwards your files are not " +
                    "protected by the screen lock.",
                color = GlassTheme.diagnosticColors.warning,
                fontSize = FontSize.BodySmall,
                fontFamily = ideFontFamily(),
            )
        }
    }
}
