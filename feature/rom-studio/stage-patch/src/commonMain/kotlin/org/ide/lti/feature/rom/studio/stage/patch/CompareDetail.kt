/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.patch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.IdeHintText
import org.ide.lti.core.designsystem.component.display.IdeMonoText
import org.ide.lti.core.designsystem.component.display.IdeSectionCard
import org.ide.lti.core.designsystem.component.progress.GlassLoadingPlaceholder
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.pipeline.toolkit.ApkSignatureDiff
import org.ide.lti.core.domain.pipeline.toolkit.ApkSignatureReport
import org.ide.lti.core.domain.pipeline.toolkit.ArchiveDiff
import org.ide.lti.core.domain.pipeline.toolkit.CodePackageDiff
import org.ide.lti.core.domain.pipeline.toolkit.MethodChangeKind
import org.ide.lti.core.domain.pipeline.toolkit.MethodDifference
import org.ide.lti.core.domain.pipeline.toolkit.SmaliCompare
import org.ide.lti.core.domain.pipeline.toolkit.TreeChange
import org.ide.lti.core.domain.workbench.EntryComparison

/** The row opened in a tree comparison, looked at closely. */
@Composable
internal fun EntryCard(opened: TreeChange, entry: EntryComparison?, busy: Boolean) {
    IdeSectionCard(title = opened.path) {
        if (opened.details.isNotEmpty()) IdeHintText(opened.details.joinToString(" · "))
        when (entry) {
            null -> if (busy) GlassLoadingPlaceholder("Reading both sides…") else Unit
            is EntryComparison.NoContent -> IdeHintText(entry.reason)
            is EntryComparison.Text -> TextDiff(entry.lines)
            is EntryComparison.Archive -> Entries(entry.entries)
            is EntryComparison.Code -> CodeDiff(entry.code, entry.entries, entry.signatures)
            is EntryComparison.Binary -> {
                IdeMonoText("A ${entry.sizeA} bytes  sha256 ${entry.sha256A}")
                IdeMonoText("B ${entry.sizeB} bytes  sha256 ${entry.sha256B}")
            }
        }
    }
}

@Composable
private fun TextDiff(lines: List<String>?) {
    if (lines == null) {
        IdeHintText("Over 3000 lines a side: too long to diff here. Copy both out and use a diff tool.")
        return
    }
    if (lines.isEmpty()) IdeHintText("The lines are the same (only line endings or trailing bytes differ).")
    DiffLines(lines, Modifier.testTag("CompareTextDiff"))
}

/** `- old` / `+ new` lines, coloured. */
@Composable
private fun DiffLines(lines: List<String>, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxWidth().heightIn(max = ComponentSize.MaxPanelHeight)) {
        items(lines) { line -> IdeMonoText(line, color = lineColor(line)) }
    }
}

@Composable
private fun lineColor(line: String): Color = when {
    line.startsWith("+") -> GlassTheme.diagnosticColors.gitAdded
    line.startsWith("-") -> GlassTheme.diagnosticColors.gitDeleted
    else -> Color.Unspecified
}

@Composable
private fun Entries(d: ArchiveDiff) {
    if (d.identical) {
        IdeHintText("The same entries with the same CRC (the archive's own bytes differ: order, times, alignment).")
    }
    val lines = d.missingInB.map { "- $it" } + d.extraInB.map { "+ $it" } + d.modified.map { "~ $it" }
    if (lines.isNotEmpty()) {
        IdeHintText("Entries: ${d.missingInB.size} only in A, ${d.extraInB.size} only in B, ${d.modified.size} changed")
        DiffLines(lines)
    }
}

/**
 * Two packages' code: APK signing certificates (when present), their dex files, then the methods that really changed,
 * then those only renumbered; [entries] (the archives' entries) when two JARs or APKs were compared.
 */
@Composable
internal fun CodeDiff(code: CodePackageDiff, entries: ArchiveDiff?, signatures: ApkSignatureDiff? = null) {
    var showEntries by remember(code) { mutableStateOf(false) }
    var showRenumbered by remember(code) { mutableStateOf(false) }
    var openMethod by remember(code) { mutableStateOf<MethodDifference?>(null) }
    signatures?.let { ApkSignatureCard(it) }
    val real = code.realChanges
    val renumbered = code.methodDifferences.filter { it.renumberedOnly }
    CodeDexAndMethodSummary(code = code, entries = entries, real = real, renumberedCount = renumbered.size)
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = ComponentSize.MaxPanelHeight).testTag("CompareMethods")) {
        items(real) { m ->
            MethodRow(m, open = openMethod == m) { openMethod = if (openMethod == m) null else m }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        if (renumbered.isNotEmpty()) {
            GlassButton(onClick = { showRenumbered = !showRenumbered }, variant = GlassButtonVariant.Secondary) {
                Text(
                    "${if (showRenumbered) "Hide" else "Show"} renumbered (${renumbered.size})",
                    fontSize = FontSize.Micro,
                )
            }
        }
        if (entries != null) {
            GlassButton(onClick = { showEntries = !showEntries }, variant = GlassButtonVariant.Secondary) {
                Text("${if (showEntries) "Hide" else "Show"} archive entries", fontSize = FontSize.Micro)
            }
        }
    }
    if (showRenumbered) renumbered.take(MAX_SHOWN).forEach { IdeMonoText(signature(it)) }
    if (showEntries && entries != null) Entries(entries)
}

@Composable
private fun CodeDexAndMethodSummary(
    code: CodePackageDiff,
    entries: ArchiveDiff?,
    real: List<MethodDifference>,
    renumberedCount: Int,
) {
    // Two decoded folders carry no dex files to compare; two packages say which differ, or that none does.
    when {
        code.dexDifferences.isNotEmpty() -> IdeHintText("Dex: " + code.dexDifferences.joinToString(", "))
        entries != null -> IdeHintText("No dex file differs.")
    }
    val classNote = if (code.classDifferences.isEmpty()) {
        ""
    } else {
        "${code.classDifferences.size} class(es) only on one side."
    }
    IdeHintText(
        "${real.count { it.changeKind == MethodChangeKind.Modified }} modified, " +
            "${real.count { it.changeKind == MethodChangeKind.Added }} added, " +
            "${real.count { it.changeKind == MethodChangeKind.Removed }} removed method(s); " +
            "$renumberedCount differ only in the compiler's numbering. $classNote",
    )
    code.classDifferences.take(MAX_SHOWN).forEach {
        IdeMonoText(it, color = lineColor(if (it.startsWith("added")) "+" else "-"))
    }
}

@Composable
private fun MethodRow(m: MethodDifference, open: Boolean, onToggle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            IdeMonoText(
                m.changeKind.name.lowercase(),
                color = lineColor(
                    when (m.changeKind) {
                        MethodChangeKind.Added -> "+"
                        MethodChangeKind.Removed -> "-"
                        MethodChangeKind.Modified -> ""
                    },
                ),
            )
            Column(Modifier.weight(1f)) {
                IdeMonoText(signature(m))
                IdeHintText("instructions A ${m.instructionsA ?: "—"} · B ${m.instructionsB ?: "—"}")
            }
            if (m.lines.isNotEmpty()) {
                GlassButton(onClick = onToggle, variant = GlassButtonVariant.Secondary) {
                    Text(if (open) "Hide" else "Diff", fontSize = FontSize.Micro)
                }
            }
        }
        if (open) m.lines.forEach { IdeMonoText("    $it", color = lineColor(it)) }
    }
}

/** The method's full signature: `Lcom/a/B;->name(I)V`, or the class header entry. */
private fun signature(m: MethodDifference): String = if (m.methodName == SmaliCompare.CLASS_LEVEL) {
    "L${m.className}; ${m.methodName}"
} else {
    "L${m.className};->${m.methodName}"
}

@Composable
private fun ApkSignatureCard(diff: ApkSignatureDiff) {
    val color = when {
        !diff.right.isSigned -> GlassTheme.diagnosticColors.error
        diff.certificatesChanged -> GlassTheme.diagnosticColors.warning
        else -> GlassTheme.diagnosticColors.success
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
        IdeMonoText("APK Signatures: ${diff.summary}", color = color)
        SignatureSideLine("A", diff.left)
        SignatureSideLine("B", diff.right)
    }
}

@Composable
private fun SignatureSideLine(side: String, report: ApkSignatureReport) {
    if (!report.isSigned) {
        IdeHintText("$side: Unsigned")
        return
    }
    val schemes = report.schemes.joinToString("/").ifBlank { "?" }
    val certs = report.certificates.joinToString("; ") { cert ->
        val cn = cert.subject.ifBlank { "unknown subject" }
        "${cert.scheme}:${cert.sha256.take(SHORT_CERT)} ($cn)"
    }.ifBlank { "schemes=$schemes" }
    IdeMonoText("$side [$schemes]: $certs")
}

private const val SHORT_CERT = 12
private const val MAX_SHOWN = 200
