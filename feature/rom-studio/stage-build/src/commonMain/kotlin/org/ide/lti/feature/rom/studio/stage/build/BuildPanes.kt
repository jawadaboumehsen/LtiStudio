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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.component.inputs.TextFieldState
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.configuration.StageSettingsValidator
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.feature.rom.studio.api.formatBytes

/** How each dynamic partition image is built (mkfs.erofs). */
@Composable
internal fun CompressionPane(settings: BuildSettings, onChange: (BuildSettings) -> Unit) {
    Card("Image compression", tag = "CompressionPane") {
        Detail("Filesystem", "${settings.filesystem} (from the device)")
        // The level is one value: older settings kept it in the compression string ("lz4hc,9"), which the build
        // still reads; shown and edited here as the Level, it is saved in `level` and the string keeps the algorithm.
        val algorithm = settings.compression.substringBefore(',')
        val level = settings.level ?: settings.compression.substringAfter(',', "").toIntOrNull()
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            COMPRESSORS.forEach { candidate ->
                GlassChip(
                    label = candidate,
                    selected = algorithm == candidate,
                    onClick = { onChange(settings.copy(compression = candidate, level = level)) },
                )
            }
        }
        NumberField("Level (empty = the tool's default)", level) {
            onChange(settings.copy(compression = algorithm, level = it))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            Hint("Block size")
            BLOCK_SIZES.forEach { size ->
                GlassChip(
                    label = size.toString(),
                    selected = settings.blockSize == size,
                    onClick = { onChange(settings.copy(blockSize = size)) },
                )
            }
        }
        NumberField("Super alignment in bytes (empty = default)", settings.alignment) {
            onChange(settings.copy(alignment = it))
        }
    }
}

/** The images the last build produced, per dynamic partition of the device. */
@Composable
internal fun ImagesPane(target: TargetDevice, facts: BuildFacts) {
    Card("Partition images", tag = "ImagesPane") {
        Hint("Built from work/<partition>/ into out/images/, then given an AVB hashtree footer.")
        target.dynamicPartitions.forEach { p ->
            val built = facts.images.firstOrNull { it.path == "$p.img" }
            Detail("$p.img", built?.let { mb(it.sizeBytes) } ?: "not built yet")
        }
    }
}

/** The super partition the package flashes, from the device, with the last build's image sizes. */
@Composable
internal fun PartitionLayoutPane(target: TargetDevice, facts: BuildFacts) {
    val used = target.dynamicPartitions.sumOf { p -> facts.images.firstOrNull { it.path == "$p.img" }?.sizeBytes ?: 0L }
    Card("Super partition", tag = "PartitionLayoutPane") {
        Detail("Super size", mb(target.superPartitionBytes))
        Detail("Group", "${target.superGroupName}, ${mb(target.superGroupBytes)}")
        Detail("Metadata slots", target.superMetadataSlots.toString())
        Detail("Virtual A/B", if (target.virtualAb) "yes" else "no")
        Detail(
            "Images in the group",
            if (used == 0L) "build first" else "${mb(used)} of ${mb(target.superGroupBytes)}",
            if (used > target.superGroupBytes) GlassTheme.diagnosticColors.error else null,
        )
    }
    Card("Boot chain (flashed directly)") {
        target.packagePolicy.flashableBootPartitions.forEach { b ->
            Detail("$b.img", mb(BuildFlashableZipStage.bootPartitionBytes(target, b)))
        }
        Hint("vbmeta is never packaged: flash the stock vbmeta once with verification disabled.")
    }
}

/** What goes into the flashable zip: the planned members, or the real ones of the last package. */
@Composable
internal fun FlashableMembersPane(target: TargetDevice, facts: BuildFacts) {
    val title = if (facts.zipMembers.isEmpty()) "Planned members" else "Members of the last package"
    Card(title, tag = "FlashableMembersPane") {
        val members = facts.zipMembers.ifEmpty {
            target.dynamicPartitions.flatMap { listOf("$it.new.dat.br", "$it.transfer.list", "$it.patch.dat") } +
                target.packagePolicy.flashableBootPartitions.map { "$it.img" } +
                listOf(
                    "dynamic_partitions_op_list",
                    "META-INF/com/google/android/update-binary",
                    "META-INF/com/google/android/updater-script",
                    "META-INF/com/android/metadata",
                )
        }
        members.forEach { Mono(it) }
    }
}

/** The AVB key that signs the images and the boot chain. */
@Composable
internal fun AvbKeysPane(settings: BuildSettings, facts: BuildFacts, onChange: (BuildSettings) -> Unit) {
    Card("AVB signing", tag = "AvbKeysPane") {
        Detail("Private key", settings.signing.avbKeyRef)
        Detail("Public key SHA-1", facts.avbPublicKeySha1 ?: "measured when Build & Sign runs")
        ToggleRow(
            "Re-sign the boot chain with this key",
            settings.signing.signImages,
        ) { onChange(settings.copy(signing = settings.signing.copy(signImages = it))) }
        Hint(
            "Partition images always get a hashtree footer signed with this key. With re-signing off, the boot " +
                "images go in with the footer Assemble left them.",
        )
    }
}

/** The certificate that signs the zip (signapk). */
@Composable
internal fun PackageSigningPane(settings: BuildSettings, facts: BuildFacts, onChange: (BuildSettings) -> Unit) {
    Card("Package signing", tag = "PackageSigningPane") {
        ToggleRow("Sign the flashable zip", settings.signing.signPackage) {
            onChange(settings.copy(signing = settings.signing.copy(signPackage = it)))
        }
        Detail("Platform key", settings.signing.platformKeyRef)
        Detail("Certificate SHA-1", facts.platformCertSha1 ?: "measured when Build & Sign runs")
        if (!settings.signing.signPackage) {
            Hint("An unsigned package is marked UNSIGNED and cannot be published.")
        }
    }
}

/** The package's file name, with the values the next build fills in. */
@Composable
internal fun OutputNamingPane(
    settings: BuildSettings,
    target: TargetDevice,
    facts: BuildFacts,
    onChange: (BuildSettings) -> Unit,
) {
    Card("Package name", tag = "OutputNamingPane") {
        GlassTextField(
            value = settings.filenameTemplate,
            onValueChange = { onChange(settings.copy(filenameTemplate = it)) },
            state = StageSettingsValidator.validateBuild(settings).errors
                .firstOrNull { it.fieldPath == "filenameTemplate" }
                ?.let { TextFieldState.Error(it.message) } ?: TextFieldState.Default,
            modifier = Modifier.fillMaxWidth(),
        )
        Hint("Placeholders: {version} (ROM version), {date} (the run's date, YYYYMMDD), {device}.")
        Detail(
            "Next build",
            BuildFlashableZipStage.renderName(settings.filenameTemplate, facts.romVersion, "YYYYMMDD", target.id),
        )
        facts.packages.forEach { Detail("Built", "${it.path}  (${mb(it.sizeBytes)})") }
        facts.zipSha256?.let { Detail("Last SHA-256", it) }
    }
}

@Composable
private fun Card(title: String, tag: String? = null, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(bottom = Spacing.Medium)
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
private fun Detail(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            fontFamily = ideFontFamily(),
            modifier = Modifier.width(GlassDimens.AcquirePolicyLabelWidth),
        )
        Text(
            text = value,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontFamily = codeFontFamily(),
        )
    }
}

@Composable
private fun Mono(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = FontSize.BodySmall,
        fontFamily = codeFontFamily(),
    )
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

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small), verticalAlignment = Alignment.CenterVertically) {
        GlassToggle(checked = checked, onCheckedChange = onChange, activeColor = MaterialTheme.colorScheme.primary)
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontFamily = ideFontFamily(),
        )
    }
}

@Composable
private fun NumberField(label: String, value: Int?, onChange: (Int?) -> Unit) {
    Hint(label)
    GlassTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { text -> if (text.isEmpty()) onChange(null) else text.toIntOrNull()?.let(onChange) },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun mb(bytes: Long): String = formatBytes(bytes)

private val COMPRESSORS = listOf("lz4hc", "lz4")
private val BLOCK_SIZES = listOf(4096)
