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
import androidx.compose.foundation.layout.height
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
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.toolkit.BuildFilesystems
import org.ide.lti.core.domain.pipeline.toolkit.BuildWarningKind
import org.ide.lti.core.domain.pipeline.toolkit.CoverageRule
import org.ide.lti.core.domain.pipeline.toolkit.PackageKind
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.workspace.WorkspaceFile
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.ui.GlassCodeEditor
import org.ide.lti.core.ui.toolkit.RefusalView

/** Per-partition `mkfs.erofs -z` choices; absent from the map means the stage-wide setting (FR-014). */
internal val PARTITION_COMPRESSION = listOf(
    "lz4" to "Fast (lz4)",
    "lz4hc,12" to "Smallest (lz4hc 12)",
    "none" to "None",
)

/** One row per dynamic partition: the stage-wide compression, or its own (FR-014). */
@Composable
internal fun PartitionCompressionPane(
    settings: BuildSettings,
    target: TargetDevice,
    onChange: (BuildSettings) -> Unit,
) {
    ToolCard("Per-partition compression", tag = "PartitionCompressionPane") {
        Note(
            "Each choice is passed to mkfs.erofs for that partition and recorded in the build's cache key. " +
                "Deduplication stores identical data once (vendor shrinks about a quarter) but makes each image " +
                "take minutes to build; automatic turns it on when the boot kernel is 6.1 or newer.",
        )
        target.dynamicPartitions.forEach { p ->
            val own = settings.partitionCompression[p]
            val fs = BuildFilesystems.of(settings, p)
            if (fs != BuildFilesystems.EROFS) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    Mono(p)
                    Note("built as $fs: not compressed")
                }
                return@forEach
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Mono(p)
                GlassChip(
                    label = "Stage setting (${settings.compression})",
                    selected = own == null,
                    onClick = { onChange(settings.copy(partitionCompression = settings.partitionCompression - p)) },
                )
                PARTITION_COMPRESSION.forEach { (value, label) ->
                    GlassChip(
                        label = label,
                        selected = own == value,
                        onClick = {
                            onChange(
                                settings.copy(partitionCompression = settings.partitionCompression + (p to value)),
                            )
                        },
                    )
                }
            }
            DedupeRow(settings, p, onChange)
        }
    }
}

/**
 * [p]'s EROFS deduplication (identical data stored once; vendor about a quarter smaller, the build much slower):
 * automatic follows the boot kernel (on for 6.1 or newer), or forced on or off.
 */
@Composable
private fun DedupeRow(settings: BuildSettings, p: String, onChange: (BuildSettings) -> Unit) {
    val own = settings.partitionDedupe[p]
    fun set(value: Boolean?) {
        val next = if (value == null) settings.partitionDedupe - p else settings.partitionDedupe + (p to value)
        onChange(settings.copy(partitionDedupe = next))
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = Spacing.Large).testTag("Dedupe_$p"),
    ) {
        Note("Deduplicate")
        GlassChip(label = "Automatic (kernel 6.1+)", selected = own == null, onClick = { set(null) })
        GlassChip(label = "On", selected = own == true, onClick = { set(true) })
        GlassChip(label = "Off", selected = own == false, onClick = { set(false) })
    }
}

/**
 * The filesystem each dynamic partition is built as: the stage's choice, or
 * EROFS, ext4 or F2FS for that partition.
 */
@Composable
internal fun PartitionFilesystemPane(
    settings: BuildSettings,
    target: TargetDevice,
    onChange: (BuildSettings) -> Unit,
    facts: BuildFacts = BuildFacts(),
) {
    fun sizes(files: List<WorkspaceFile>) =
        files.associate { it.path.substringAfterLast('/').removeSuffix(".img") to it.sizeBytes }
    val stock = sizes(facts.stockImages)
    val built = sizes(facts.images)
    ToolCard("Per-partition filesystem", tag = "PartitionFilesystemPane") {
        Note(
            "EROFS is smaller and is what stock uses; ext4 suits a GSI or a partition you want writable later; " +
                "F2FS is built read-only (the kernel must mount F2FS). " +
                "The phone needs an fstab line for the filesystem: the build adds it to vendor/etc/fstab.*, and " +
                "Assemble > Mount table adds it to the first-stage fstab in vendor_boot.",
        )
        target.dynamicPartitions.forEach { p ->
            val own = settings.partitionFilesystem[p]
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Mono(p)
                GlassChip(
                    label = "Stage setting (${settings.filesystem})",
                    selected = own == null,
                    onClick = { onChange(settings.copy(partitionFilesystem = settings.partitionFilesystem - p)) },
                )
                BuildFilesystems.SUPPORTED.forEach { fs ->
                    GlassChip(
                        label = fs,
                        selected = own == fs,
                        onClick = {
                            onChange(
                                settings.copy(partitionFilesystem = settings.partitionFilesystem + (p to fs)),
                            )
                        },
                    )
                }
            }
            // ext4 and F2FS can be built at a fixed size; EROFS is always its content's size.
            if (BuildFilesystems.of(settings, p) != BuildFilesystems.EROFS) {
                PartitionSizeRow(settings, p, stock[p], built[p], onChange)
            }
            // Only an ext4 image can be written to later; it is otherwise built just large enough.
            if (BuildFilesystems.of(settings, p) == BuildFilesystems.EXT4 && p !in settings.partitionSizeBytes) {
                NumberField("Free space in $p, MiB (empty = none)", settings.partitionFreeMib[p]) { mib ->
                    onChange(
                        settings.copy(
                            partitionFreeMib = if (mib == null || mib == 0) {
                                settings.partitionFreeMib - p
                            } else {
                                settings.partitionFreeMib + (p to mib)
                            },
                        ),
                    )
                }
            }
        }
    }
}

/**
 * [p]'s image size: automatic (its content plus a margin) or a fixed number of MiB, with the stock and the last
 * built image sizes to go by. A fixed size the content does not fit stops the build (`PARTITION_TOO_SMALL`).
 */
@Composable
private fun PartitionSizeRow(
    settings: BuildSettings,
    p: String,
    stockBytes: Long?,
    builtBytes: Long?,
    onChange: (BuildSettings) -> Unit,
) {
    fun set(bytes: Long?) = onChange(
        settings.copy(
            partitionSizeBytes = if (bytes == null) {
                settings.partitionSizeBytes - p
            } else {
                settings.partitionSizeBytes + (p to bytes)
            },
            // A fixed size replaces free space: the size says it all.
            partitionFreeMib = if (bytes == null) settings.partitionFreeMib else settings.partitionFreeMib - p,
        ),
    )
    // KiB, not MiB: a stock image is a whole number of 4 KiB blocks and small ones (odm) are under 1 MiB.
    NumberField("Size of $p, KiB (empty = automatic)", settings.partitionSizeBytes[p]?.let { (it / KIB).toInt() }) {
        set(it?.takeIf { kib -> kib > 0 }?.let { kib -> kib * KIB })
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag("PartitionSize_$p"),
    ) {
        Note(
            listOfNotNull(
                stockBytes?.let { "stock image ${it / KIB} KiB" },
                builtBytes?.let { "last built ${it / KIB} KiB" },
            ).ifEmpty { listOf("no image measured yet") }.joinToString(" · "),
        )
        // The stock image is a whole number of 4 KiB blocks: it can be used as is.
        stockBytes?.let { bytes ->
            GlassButton(onClick = { set(bytes - bytes % settings.blockSize) }) {
                Text("Use the stock size", fontSize = FontSize.Micro)
            }
        }
    }
}

private const val KIB = 1024L

/**
 * Packages built besides the flashable zip (FR-013): a fastboot images zip and a Samsung Odin AP. A dynamic
 * partition added as a standalone image is also inside super.img, which needs an explicit acceptance (FR-017).
 */
@Composable
internal fun ExtraPackagesPane(settings: BuildSettings, target: TargetDevice, onChange: (BuildSettings) -> Unit) {
    ToolCard("Extra packages", tag = "ExtraPackagesPane") {
        Note("The flashable zip (block-based OTA) is always built; these are built next to it in out/package/.")
        EXTRA_PACKAGES.forEach { (kind, text) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassToggle(
                    checked = kind.name in settings.extraPackages,
                    onCheckedChange = { on ->
                        val next = if (on) settings.extraPackages + kind.name else settings.extraPackages - kind.name
                        onChange(settings.copy(extraPackages = next))
                    },
                )
                Mono(text)
            }
        }
        Note(
            "Seamless (A/B payload) OTA is not offered: it needs delta_generator from AOSP update_engine, which no " +
                "tool group installs.",
        )
        if (PackageKind.FullPackage.name in settings.extraPackages) {
            Note("Standalone images in the fastboot zip (besides super.img and the boot images):")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                target.dynamicPartitions.forEach { p ->
                    GlassChip(
                        label = p,
                        selected = p in settings.standaloneImages,
                        onClick = {
                            val next = if (p in
                                settings.standaloneImages
                            ) {
                                settings.standaloneImages - p
                            } else {
                                settings.standaloneImages +
                                    p
                            }
                            // An image no longer standalone cannot stay sparse either.
                            onChange(settings.copy(standaloneImages = next, sparseImages = settings.sparseImages - p))
                        },
                    )
                }
            }
            if (settings.standaloneImages.isNotEmpty()) {
                Note("Write as Android sparse images (smaller; fastboot flashes them as they are):")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    settings.standaloneImages.forEach { p ->
                        val sparse = p in settings.sparseImages
                        GlassChip(
                            label = "$p sparse",
                            selected = sparse,
                            onClick = {
                                val next = if (sparse) settings.sparseImages - p else settings.sparseImages + p
                                onChange(settings.copy(sparseImages = next))
                            },
                            modifier = Modifier.testTag("SparseImage_$p"),
                        )
                    }
                }
            }
            val overlap = settings.standaloneImages.filter { it in target.dynamicPartitions }
            if (overlap.isNotEmpty()) {
                val accepted = BuildWarningKind.SuperOverlap.name in settings.acknowledgedWarnings
                GlassBanner(
                    severity = if (accepted) GlassBannerSeverity.INFO else GlassBannerSeverity.WARNING,
                    title = "${overlap.joinToString()} are also inside super.img",
                    description = "Flashing both writes the partition twice; the build stops until this is accepted.",
                )
                GlassChip(
                    label = "I accept: flash them separately",
                    selected = accepted,
                    onClick = {
                        val name = BuildWarningKind.SuperOverlap.name
                        val next = if (accepted) {
                            settings.acknowledgedWarnings - name
                        } else {
                            settings.acknowledgedWarnings +
                                name
                        }
                        onChange(settings.copy(acknowledgedWarnings = next))
                    },
                )
            }
        }
    }
}

/** The build's link check (`BuildChecks.linkSteps`) and accepting what it finds. */
@Composable
internal fun LinkCheckPane(settings: BuildSettings, onChange: (BuildSettings) -> Unit) {
    ToolCard("Symbolic links", tag = "LinkCheckPane") {
        Note(
            "Every build resolves the links of the assembled tree as the phone would. A link that points at " +
                "nothing although it works in the stock firmware (an app or library removed, a file moved) stops " +
                "the build, and the build log lists them. Links into /apex, /data and other run-time folders " +
                "are not checked.",
        )
        val name = BuildWarningKind.BrokenLinks.name
        val accepted = name in settings.acknowledgedWarnings
        GlassChip(
            label = "Build even if links are broken",
            selected = accepted,
            onClick = {
                val next = if (accepted) settings.acknowledgedWarnings - name else settings.acknowledgedWarnings + name
                onChange(settings.copy(acknowledgedWarnings = next))
            },
        )
    }
}

/** The build's APK signature check (`BuildChecks.apkSignatureSteps`) and accepting what it finds. */
@Composable
internal fun ApkSignaturePane(settings: BuildSettings, onChange: (BuildSettings) -> Unit) {
    ToolCard("APK signatures", tag = "ApkSignaturePane") {
        Note(
            "Every build checks that each APK of the assembled tree is signed (a v2/v3 signing block or a v1 " +
                "signature). Android does not install an unsigned app, so one dropped in by a mod or a script " +
                "stops the build, and the build log names it.",
        )
        val name = BuildWarningKind.UnsignedApks.name
        val accepted = name in settings.acknowledgedWarnings
        GlassChip(
            label = "Build even with unsigned APKs",
            selected = accepted,
            onClick = {
                val next = if (accepted) settings.acknowledgedWarnings - name else settings.acknowledgedWarnings + name
                onChange(settings.copy(acknowledgedWarnings = next))
            },
        )
    }
}

/** Files of the work tree without an fs_config or file_contexts entry, and adding them (FR-015, FR-016). */
@Composable
internal fun CoveragePane(state: CoverageState, onCheck: () -> Unit, onFix: () -> Unit) {
    ToolCard("Permission and label coverage", tag = "CoveragePane") {
        Note(
            "Every file of the assembled tree needs an fs_config line and a file_contexts line, or the image does " +
                "not boot; the build refuses a tree with gaps. Adding entries gives a file the owner, mode and " +
                "label of its closest listed folder, in every build (the Patches stage does it last).",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            GlassButton(
                onClick = onCheck,
                enabled = state.busy == null,
            ) { Text("Check the work tree", fontSize = FontSize.BodySmall) }
        }
        state.busy?.let { Mono("$it…") }
        state.refusal?.let { RefusalView(refusal = it) }
        state.result?.let { result ->
            val missing = (result.uncoveredFsConfig + result.uncoveredContexts).distinct().sorted()
            if (missing.isEmpty()) {
                GlassBanner(severity = GlassBannerSeverity.INFO, title = "Every file is covered.")
            } else {
                RefusalView(
                    refusal = Refusal(
                        cause = "${missing.size} file(s) have no permission or SELinux label entry.",
                        item = "work/configs",
                        nextStep = if (state.fillInEveryBuild) {
                            "Every build now adds them; run the build again."
                        } else {
                            "Add the missing entries in every build, or remove the files."
                        },
                    ),
                    actionLabel = if (state.fillInEveryBuild) null else "Add entries in every build",
                    onAction = onFix.takeUnless { state.fillInEveryBuild },
                )
                missing.take(MAX_SHOWN).forEach { Mono(it) }
                if (missing.size > MAX_SHOWN) Note("…and ${missing.size - MAX_SHOWN} more")
            }
        }
    }
}

/** The user's coverage rules: owner, mode and label by path, before "like its folder". */
@Composable
internal fun CoverageRulesPane(rules: String, onChange: (String) -> Unit) {
    ToolCard("Coverage rules", tag = "CoverageRulesPane") {
        Note(
            "One rule per line: <regex> <uid> <gid> <mode> <label>, the regex matched against the whole device " +
                "path. A file without sidecar lines gets the first rule that matches, else its folder's lines. " +
                "Example: /system/app/MyApp(/.*)? 0 0 0644 u:object_r:system_file:s0",
        )
        GlassCodeEditor(
            content = rules,
            onContentChange = onChange,
            modifier = Modifier.fillMaxWidth().height(ComponentSize.ActionCardHeight).testTag("CoverageRulesEditor"),
        )
        CoverageRule.parse(rules).second.forEach { Mono(it) }
    }
}

private val EXTRA_PACKAGES = listOf(
    PackageKind.FullPackage to "Fastboot images zip (super.img + boot images)",
    PackageKind.Odin to "Samsung Odin AP (.tar.md5 with super.img.lz4 and the boot images)",
)
private const val MAX_SHOWN = 200

@Composable
private fun ToolCard(title: String, tag: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium)
            .testTag(tag),
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
private fun Note(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.BodySmall,
        fontFamily = ideFontFamily(),
    )
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
