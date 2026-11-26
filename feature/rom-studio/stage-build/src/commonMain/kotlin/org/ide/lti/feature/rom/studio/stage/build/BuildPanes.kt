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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.IdeDetailRow
import org.ide.lti.core.designsystem.component.display.IdeHintText
import org.ide.lti.core.designsystem.component.display.IdeMonoText
import org.ide.lti.core.designsystem.component.display.IdeSectionCard
import org.ide.lti.core.designsystem.component.display.IdeStatusBadge
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.designsystem.component.display.IdeTable
import org.ide.lti.core.designsystem.component.display.IdeTableHeader
import org.ide.lti.core.designsystem.component.display.IdeTableHeaderCell
import org.ide.lti.core.designsystem.component.display.IdeTableRow
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.component.inputs.TextFieldState
import org.ide.lti.core.designsystem.component.progress.GlassProgressBar
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.configuration.StageSettingsValidator
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage
import org.ide.lti.core.domain.pipeline.toolkit.BuildFilesystems
import org.ide.lti.core.domain.pipeline.toolkit.Superblock
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.model.workspace.PackageType
import org.ide.lti.feature.rom.studio.api.formatBytes

/** How each dynamic partition image is built (mkfs.erofs). */
@Composable
internal fun CompressionPane(settings: BuildSettings, onChange: (BuildSettings) -> Unit) {
    IdeSectionCard(
        modifier = Modifier.testTag("CompressionPane").padding(bottom = Spacing.Medium),
        title = "Image compression",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            IdeHintText("Filesystem")
            BuildFilesystems.SUPPORTED.forEach { fs ->
                GlassChip(
                    label = fs,
                    selected = settings.filesystem.equals(fs, ignoreCase = true),
                    onClick = { onChange(settings.copy(filesystem = fs)) },
                )
            }
        }
        IdeHintText("Compression and level apply to EROFS images; ext4 images are not compressed.")
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
            IdeHintText("Block size")
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
        NumberField("Package brotli level, 0-11 (higher: smaller zip, slower build)", settings.brotliLevel) {
            onChange(settings.copy(brotliLevel = it ?: BuildSettings().brotliLevel))
        }
    }
}

private const val PERCENT = 100L

/** The images the last build produced, per dynamic partition of the device. */
@Composable
internal fun ImagesPane(target: TargetDevice, settings: BuildSettings, facts: BuildFacts) {
    IdeSectionCard(
        modifier = Modifier.testTag("ImagesPane").padding(bottom = Spacing.Medium),
        title = "Partition images",
    ) {
        IdeHintText(
            "Built from work/<partition>/ into out/images/, then given an AVB hashtree footer. Files is what the " +
                "partition holds; Saved compares the image (before its footer) with it.",
        )
        IdeTable {
            IdeTableHeader { IMAGE_COLUMNS.forEach { (t, w) -> IdeTableHeaderCell(t, Modifier.weight(w)) } }
            target.dynamicPartitions.forEach { p ->
                val built = facts.images.firstOrNull { it.path == "$p.img" }
                val pair = facts.contentAndImage[p]
                IdeTableRow(modifier = Modifier.testTag("ImageRow_$p")) {
                    Cell(0) { IdeMonoText(p) }
                    // What the built image is, as its superblock says; the setting until it is built.
                    val fs = facts.superblocks[p]?.built?.fs?.name?.lowercase()
                    Cell(1) { IdeHintText(fs ?: BuildFilesystems.of(settings, p)) }
                    Cell(2) { IdeMonoText(pair?.first?.let(::mb) ?: "—") }
                    Cell(3) { IdeMonoText(built?.let { mb(it.sizeBytes) } ?: "—") }
                    Cell(4) {
                        val saved = pair?.let { (content, image) ->
                            if (content > 0) "${(content - image) * PERCENT / content}%" else null
                        }
                        IdeMonoText(saved ?: "—")
                    }
                    Cell(5) {
                        if (built != null) {
                            IdeStatusBadge("Built", IdeStatusSeverity.Ready)
                        } else {
                            IdeStatusBadge("Not built", IdeStatusSeverity.Neutral)
                        }
                    }
                }
            }
        }
        ReadBack(target, facts)
        SuperFill(target, facts)
    }
}

/** Each built image as its superblock says, after the build mounted it and compared it with work/. */
@Composable
private fun ReadBack(target: TargetDevice, facts: BuildFacts) {
    val readBack = target.dynamicPartitions.mapNotNull { p -> facts.superblocks[p]?.built?.let { p to it } }
    if (readBack.isNotEmpty()) {
        IdeHintText(
            "Read back from each built image's superblock. The build also mounted every image and compared its " +
                "files, owners, modes and labels with work/ before packaging it.",
        )
        readBack.forEach { (p, sb) -> IdeMonoText(readBackLine(p, sb)) }
    }
}

/** How much of the super group the built images take. */
@Composable
private fun SuperFill(target: TargetDevice, facts: BuildFacts) {
    val total = target.dynamicPartitions.sumOf { p ->
        facts.images.firstOrNull { it.path == "$p.img" }?.sizeBytes
            ?: 0L
    }
    if (total > 0 && target.superGroupBytes > 0) {
        IdeDetailRow(
            "Images in total",
            "${mb(total)} of ${mb(target.superGroupBytes)} (${target.superGroupName})",
        )
        GlassProgressBar(
            progress = (total.toFloat() / target.superGroupBytes).coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth(),
            contentDescription = "Images fill ${total * PERCENT / target.superGroupBytes}% of the super group",
        )
    }
}

@Composable
private fun RowScope.Cell(column: Int, content: @Composable () -> Unit) {
    Box(modifier = Modifier.weight(IMAGE_COLUMNS[column].second)) { content() }
}

/** One built image in a line: filesystem, size, label, UUID, how full (ext4 says), features. */
internal fun readBackLine(p: String, sb: Superblock): String {
    val f = sb.fields.toMap()
    val used = f["Block count"]?.toLongOrNull()?.let { count ->
        f["Free blocks"]?.toLongOrNull()?.let { free -> "${(count - free) * PERCENT / count}% used" }
    }
    return listOfNotNull(
        "$p: ${sb.fs.name.lowercase()}",
        mb(sb.sizeBytes),
        f["Filesystem volume name"]?.takeUnless { it == "<none>" }?.let { "label $it" },
        f["Filesystem UUID"]?.let { "UUID $it" },
        used,
        (f["Filesystem features"] ?: f["superblock features"])?.let { "features $it" },
    ).joinToString(" · ")
}

private val IMAGE_COLUMNS = listOf(
    "Partition" to 1.2f,
    "Filesystem" to 1f,
    "Files" to 1.2f,
    "Image" to 1.2f,
    "Saved" to 0.8f,
    "Status" to 1f,
)

/**
 * The super partition the package flashes, from the device, and how the partitions fill its group:
 * the last build's images, or the stock ones as the estimate for a partition not built yet.
 */
@Composable
internal fun PartitionLayoutPane(target: TargetDevice, settings: BuildSettings, facts: BuildFacts) {
    val budget = superBudget(target, settings, facts)
    val error = GlassTheme.diagnosticColors.error
    IdeSectionCard(
        modifier = Modifier.testTag("PartitionLayoutPane").padding(bottom = Spacing.Medium),
        title = "Super partition",
    ) {
        IdeDetailRow("Super size", mb(target.superPartitionBytes))
        IdeDetailRow("Group", "${target.superGroupName}, ${mb(target.superGroupBytes)}")
        IdeDetailRow("Metadata slots", target.superMetadataSlots.toString())
        IdeDetailRow("Virtual A/B", if (target.virtualAb) "yes" else "no")
    }
    IdeSectionCard(
        modifier = Modifier.testTag("SuperBudgetPane").padding(bottom = Spacing.Medium),
        title = "Space in the super group",
    ) {
        budget.rows.forEach { row ->
            val bytes = row.bytes
            val size = when {
                bytes == null -> "extract or build first"
                row.estimated -> "${mb(bytes)} (stock)"
                else -> mb(bytes)
            }
            IdeDetailRow("${row.partition} (${row.filesystem})", size)
        }
        IdeDetailRow(
            "Used",
            "${mb(budget.usedBytes)} of ${mb(budget.groupBytes)}",
            valueColor = error.takeUnless { budget.fits },
        )
        IdeDetailRow(
            if (budget.fits) "Free" else "Over by",
            mb(if (budget.fits) budget.freeBytes else -budget.freeBytes),
            valueColor = error.takeUnless { budget.fits },
        )
        if (budget.estimated) {
            IdeHintText(
                "A (stock) size is the firmware's image: the build's can differ, more so when the partition is " +
                    "built as another filesystem or with other compression. Build & Sign stops if the images do " +
                    "not fit.",
            )
        }
    }
    IdeSectionCard(
        modifier = Modifier.padding(bottom = Spacing.Medium),
        title = "Boot chain (flashed directly)",
    ) {
        target.packagePolicy.flashableBootPartitions.forEach { b ->
            IdeDetailRow("$b.img", mb(BuildFlashableZipStage.bootPartitionBytes(target, b)))
        }
        IdeHintText("vbmeta is never packaged: flash the stock vbmeta once with verification disabled.")
    }
}

/** Which install package the build writes, and what each one is for. */
@Composable
private fun PackageTypeCard(settings: BuildSettings, target: TargetDevice, onChange: (BuildSettings) -> Unit) {
    IdeSectionCard(
        modifier = Modifier.testTag("PackageTypeCard").padding(bottom = Spacing.Medium),
        title = "Install package",
    ) {
        IdeHintText("What the build writes to out/package/. Both are signed with the platform key.")
        SelectField(
            selected = settings.packageType,
            options = listOf(
                SelectOption(
                    PackageType.FLASHABLE,
                    "Flashable zip",
                    "A custom recovery flashes it to the running slot",
                ),
                SelectOption(
                    PackageType.AB_OTA,
                    "A/B OTA",
                    "update_engine installs it to the other slot: stock recovery, adb sideload or an updater app",
                ),
                SelectOption(PackageType.BOTH, "Both", "A flashable zip and an A/B OTA from the same images"),
            ),
            onSelect = { onChange(settings.copy(packageType = it)) },
            tag = "PackageType",
            modifier = Modifier.widthIn(max = ComponentSize.CommandPaletteMaxWidth),
        )
        if (settings.packageType.abOta) {
            IdeHintText(
                "The A/B OTA is a partial update of the ROM's partitions (${target.dynamicPartitions.size} dynamic, " +
                    "${target.packagePolicy.flashableBootPartitions.joinToString()}): the phone keeps its own " +
                    "firmware, copied from the running slot. The ROM's otacerts.zip must hold the platform " +
                    "certificate, or the phone refuses the package.",
            )
        }
    }
}

/** What goes into the flashable zip: the planned members, or the real ones of the last package. */
@Composable
internal fun FlashableMembersPane(
    target: TargetDevice,
    facts: BuildFacts,
    settings: BuildSettings = BuildSettings(),
    onChange: (BuildSettings) -> Unit = {},
) {
    PackageTypeCard(settings, target, onChange)
    val title = if (facts.zipMembers.isEmpty()) "Planned members" else "Members of the last package"
    IdeSectionCard(
        modifier = Modifier.testTag("FlashableMembersPane").padding(bottom = Spacing.Medium),
        title = title,
    ) {
        val members = facts.zipMembers.ifEmpty {
            val flashable = target.dynamicPartitions.flatMap {
                listOf("$it.new.dat.br", "$it.transfer.list", "$it.patch.dat")
            } + target.packagePolicy.flashableBootPartitions.map { "$it.img" } + listOf(
                "dynamic_partitions_op_list",
                "META-INF/com/google/android/update-binary",
                "META-INF/com/google/android/updater-script",
                "META-INF/com/android/metadata",
            )
            val ota = listOf("META-INF/com/android/metadata", "payload.bin", "payload_properties.txt")
            (if (settings.packageType.flashable) flashable else emptyList()) +
                if (settings.packageType.abOta) ota.map { "A/B OTA: $it" } else emptyList()
        }
        members.forEach { IdeMonoText(it) }
        if ((facts.currentPackage?.sizeBytes ?: 0L) > ZIP32_LIMIT) {
            GlassBanner(
                severity = GlassBannerSeverity.INFO,
                title = "Over 4 GiB: zip64",
                description = "The package carries a zip64 directory. A current recovery reads it; a very old one " +
                    "may not.",
            )
        }
    }
}

private const val ZIP32_LIMIT = 0xFFFFFFFFL

/** The AVB key that signs the images and the boot chain. */
@Composable
internal fun AvbKeysPane(settings: BuildSettings, facts: BuildFacts, onChange: (BuildSettings) -> Unit) {
    IdeSectionCard(
        modifier = Modifier.testTag("AvbKeysPane").padding(bottom = Spacing.Medium),
        title = "AVB signing",
    ) {
        IdeDetailRow("Private key", settings.signing.avbKeyRef)
        IdeDetailRow("Public key SHA-1", facts.avbPublicKeySha1 ?: "measured when Build & Sign runs")
        ToggleRow(
            "Re-sign the boot chain with this key",
            settings.signing.signImages,
        ) { onChange(settings.copy(signing = settings.signing.copy(signImages = it))) }
        IdeHintText(
            "Partition images always get a hashtree footer signed with this key. With re-signing off, the boot " +
                "images go in with the footer Assemble left them.",
        )
    }
}

/** The certificate that signs the zip (signapk). */
@Composable
internal fun PackageSigningPane(settings: BuildSettings, facts: BuildFacts, onChange: (BuildSettings) -> Unit) {
    IdeSectionCard(
        modifier = Modifier.testTag("PackageSigningPane").padding(bottom = Spacing.Medium),
        title = "Package signing",
    ) {
        ToggleRow("Sign the flashable zip", settings.signing.signPackage) {
            onChange(settings.copy(signing = settings.signing.copy(signPackage = it)))
        }
        IdeDetailRow("Platform key", settings.signing.platformKeyRef)
        IdeDetailRow("Certificate SHA-1", facts.platformCertSha1 ?: "measured when Build & Sign runs")
        if (!settings.signing.signPackage) {
            IdeHintText("An unsigned package is marked UNSIGNED and cannot be published.")
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
    IdeSectionCard(
        modifier = Modifier.testTag("OutputNamingPane").padding(bottom = Spacing.Medium),
        title = "Package name",
    ) {
        GlassTextField(
            value = settings.filenameTemplate,
            onValueChange = { onChange(settings.copy(filenameTemplate = it)) },
            state = StageSettingsValidator.validateBuild(settings).errors
                .firstOrNull { it.fieldPath == "filenameTemplate" }
                ?.let { TextFieldState.Error(it.message) } ?: TextFieldState.Default,
            modifier = Modifier.fillMaxWidth(),
        )
        IdeHintText("Placeholders: {version} (ROM version), {date} (the run's date, YYYYMMDD), {device}.")
        IdeDetailRow(
            "Next build",
            BuildFlashableZipStage.renderName(settings.filenameTemplate, facts.romVersion, "YYYYMMDD", target.id),
        )
        facts.packages.forEach { IdeDetailRow("Built", "${it.path}  (${mb(it.sizeBytes)})") }
        facts.zipSha256?.let { IdeDetailRow("Last SHA-256", it) }
    }
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
internal fun NumberField(label: String, value: Int?, onChange: (Int?) -> Unit) {
    IdeHintText(label)
    GlassTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { text -> if (text.isEmpty()) onChange(null) else text.toIntOrNull()?.let(onChange) },
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun mb(bytes: Long): String = formatBytes(bytes)

private val COMPRESSORS = listOf("lz4hc", "lz4")
private val BLOCK_SIZES = listOf(4096)
