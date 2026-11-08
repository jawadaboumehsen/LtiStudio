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
import org.ide.lti.core.designsystem.component.display.IdeTable
import org.ide.lti.core.designsystem.component.display.IdeTableHeader
import org.ide.lti.core.designsystem.component.display.IdeTableHeaderCell
import org.ide.lti.core.designsystem.component.display.IdeTableRow
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
import org.ide.lti.core.domain.pipeline.toolkit.BuildWarningKind
import org.ide.lti.core.domain.pipeline.toolkit.CoverageRule
import org.ide.lti.core.domain.pipeline.toolkit.PackageKind
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.ui.GlassCodeEditor
import org.ide.lti.core.ui.toolkit.RefusalView

/** Per-partition `mkfs.erofs -z` choices; absent from the map means the stage-wide setting (FR-014). */
internal val PARTITION_COMPRESSION = listOf(
    "lz4" to "lz4",
    "lz4hc" to "lz4hc",
    "none" to "None",
)

/** Other `mkfs.erofs -z` compressors, offered only when the boot kernel reads them (`KernelFacts`). */
internal val KERNEL_COMPRESSION = listOf(
    "lzma" to "lzma",
    "deflate" to "deflate",
    "zstd" to "zstd",
)

/**
 * Packages built besides the flashable zip (FR-013): a fastboot images zip and a Samsung Odin AP. A dynamic
 * partition added as a standalone image is also inside super.img, which needs an explicit acceptance (FR-017).
 */
@Composable
internal fun ExtraPackagesPane(settings: BuildSettings, target: TargetDevice, onChange: (BuildSettings) -> Unit) {
    ToolCard("Extra packages", tag = "ExtraPackagesPane") {
        Note("Built next to the install package (Flashable members) in out/package/.")
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
        Note("An A/B OTA (full payload) is chosen in Flashable members, as the install package.")
        if (PackageKind.FullPackage.name in settings.extraPackages) {
            Note(
                "super.img and the boot images are always in the fastboot zip. A partition can also go in as its " +
                    "own image, and that image can be written as an Android sparse image (smaller; fastboot " +
                    "flashes it as it is).",
            )
            StandaloneTable(settings, target, onChange)
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

/** Each dynamic partition: in the fastboot zip as its own image, and if so whether sparse. */
@Composable
private fun StandaloneTable(settings: BuildSettings, target: TargetDevice, onChange: (BuildSettings) -> Unit) {
    IdeTable {
        IdeTableHeader {
            IdeTableHeaderCell("Partition", Modifier.weight(1f))
            IdeTableHeaderCell("Own image in the zip", Modifier.weight(1f))
            IdeTableHeaderCell("Sparse", Modifier.weight(1f))
        }
        target.dynamicPartitions.forEach { p ->
            val standalone = p in settings.standaloneImages
            IdeTableRow {
                Box(Modifier.weight(1f)) { Mono(p) }
                Box(Modifier.weight(1f)) {
                    GlassToggle(
                        checked = standalone,
                        onCheckedChange = { on ->
                            val next = if (on) settings.standaloneImages + p else settings.standaloneImages - p
                            // An image no longer standalone cannot stay sparse either.
                            onChange(settings.copy(standaloneImages = next, sparseImages = settings.sparseImages - p))
                        },
                        modifier = Modifier.testTag("Standalone_$p"),
                    )
                }
                Box(Modifier.weight(1f)) {
                    if (standalone) {
                        GlassToggle(
                            checked = p in settings.sparseImages,
                            onCheckedChange = { on ->
                                val next = if (on) settings.sparseImages + p else settings.sparseImages - p
                                onChange(settings.copy(sparseImages = next))
                            },
                            modifier = Modifier.testTag("SparseImage_$p"),
                        )
                    } else {
                        Note("—")
                    }
                }
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
                "not boot; the build refuses a tree with gaps. Adding entries labels a file as Android's build " +
                "would: your coverage rules, then the ROM's own SELinux rules and fs_config tables (else Android's " +
                "defaults), then its closest listed folder, in every build (the Patches stage does it last).",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            GlassButton(
                onClick = onCheck,
                enabled = state.busy == null,
            ) { Text("Check the work tree", fontSize = FontSize.BodySmall) }
        }
        state.busy?.let { Mono("$it…") }
        state.refusal?.let { RefusalView(refusal = it) }
        state.saveError?.let {
            RefusalView(refusal = Refusal(it, "Coverage settings", "Change the setting again to retry."))
        }
        state.result?.let { result ->
            val missing = (result.uncoveredFsConfig + result.uncoveredContexts).distinct().sorted()
            if (result.unbuildableNames.isNotEmpty()) {
                RefusalView(
                    refusal = Refusal(
                        cause = "${result.unbuildableNames.size} name(s) hold a space, which no permission or " +
                            "SELinux line can describe.",
                        item = "work/",
                        nextStep = "Rename or delete them in Assemble > Image browser (kept for every build).",
                    ),
                )
                result.unbuildableNames.take(MAX_SHOWN).forEach { Mono(it) }
            }
            if (missing.isEmpty() && result.unbuildableNames.isEmpty()) {
                GlassBanner(severity = GlassBannerSeverity.INFO, title = "Every file is covered.")
            } else if (missing.isNotEmpty()) {
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
        // Above the editor: below it, a long card pushes a mistake out of sight while it is being typed.
        CoverageRule.parse(
            rules,
        ).second.forEach { RefusalView(refusal = Refusal(it, "Coverage rules", "Correct the line.")) }
        GlassCodeEditor(
            content = rules,
            onContentChange = onChange,
            modifier = Modifier.fillMaxWidth().height(ComponentSize.ActionCardHeight).testTag("CoverageRulesEditor"),
        )
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
