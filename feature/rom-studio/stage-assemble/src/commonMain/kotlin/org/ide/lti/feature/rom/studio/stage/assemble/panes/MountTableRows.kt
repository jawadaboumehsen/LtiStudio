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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.IdeTable
import org.ide.lti.core.designsystem.component.display.IdeTableHeader
import org.ide.lti.core.designsystem.component.display.IdeTableHeaderCell
import org.ide.lti.core.designsystem.component.display.IdeTableRow
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.text.FstabEditor
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.core.model.workspace.FstabFlagRemoval

/**
 * Every mount table as rows: per mount point and filesystem, its fs_mgr flags, with the verity and encryption ones
 * called out and removable one at a time. A first-stage flag is removed from vendor_boot at once (an override); a
 * work-tree flag is removed in every build (`AssemblySettings.fstabFlagRemovals`), and can be put back.
 */
@Composable
internal fun MountTableRows(
    tables: Map<String, String>?,
    settings: AssemblySettings,
    onChange: (AssemblySettings) -> Unit,
    idle: Boolean,
    onRead: () -> Unit,
    onRemoveFirstStage: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small), modifier = Modifier.testTag("MountTableRows")) {
        GlassButton(onClick = onRead, enabled = idle, modifier = Modifier.testTag("ReadMountTablesButton")) {
            Text(if (tables == null) "Read the mount tables" else "Read again", fontSize = FontSize.BodySmall)
        }
        // Only files with a flag to act on: PQ84P01 also has 60 swap-only fstab.enableswap_* files.
        val (shown, plain) = tables.orEmpty().entries.partition { (_, text) ->
            FstabEditor.rows(text).any { row -> row.flags.any(FstabEditor::isRemovable) }
        }
        if (plain.isNotEmpty()) {
            Text(
                "${plain.size} other fstab file(s) have no verity or encryption flag.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.BodySmall,
                fontFamily = ideFontFamily(),
            )
        }
        shown.forEach { (path, text) ->
            val firstStage = !path.startsWith("work/")
            Text(
                text = path.substringAfterLast("ramdisk/").let { name ->
                    if (firstStage) "First stage, in vendor_boot: $name" else "$path, in every build"
                },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            FstabTable(FstabEditor.rows(text), path) { row, flag ->
                if (firstStage) {
                    FlagChip(flag, removed = false, enabled = idle) { onRemoveFirstStage(row.mountPoint, flag) }
                } else {
                    val removal = FstabFlagRemoval(row.mountPoint, flag.substringBefore('='))
                    val removed = removal in settings.fstabFlagRemovals
                    FlagChip(flag, removed, enabled = true) {
                        val list = settings.fstabFlagRemovals
                        onChange(settings.copy(fstabFlagRemovals = if (removed) list - removal else list + removal))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FstabTable(
    rows: List<FstabEditor.Row>,
    path: String,
    removable: @Composable (FstabEditor.Row, String) -> Unit,
) {
    IdeTable {
        IdeTableHeader {
            IdeTableHeaderCell("Mount point", Modifier.weight(POINT_WEIGHT))
            IdeTableHeaderCell("Filesystem", Modifier.weight(TYPE_WEIGHT))
            IdeTableHeaderCell("fs_mgr flags", Modifier.weight(1f))
        }
        rows.forEach { row ->
            val tag = "FstabRow_${path.substringAfterLast('/')}_${row.mountPoint}_${row.type}"
            IdeTableRow(modifier = Modifier.testTag(tag)) {
                Box(Modifier.weight(POINT_WEIGHT)) { Code(row.mountPoint) }
                Box(Modifier.weight(TYPE_WEIGHT)) { Code(row.type) }
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    row.flags.forEach { flag ->
                        if (FstabEditor.isRemovable(flag)) removable(row, flag) else Code(flag)
                    }
                }
            }
        }
    }
}

/** A verity or encryption flag: clicking removes it, or (removed in every build) puts it back. */
@Composable
private fun FlagChip(flag: String, removed: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val kind = if (FstabEditor.isIntegrity(flag)) "verity" else "encryption"
    GlassChip(
        label = if (removed) "$flag: removed ($kind), put back" else "$flag ($kind) ✕",
        selected = removed,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.testTag("FstabFlag_$flag"),
    )
}

@Composable
private fun Code(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.BodySmall,
        fontFamily = codeFontFamily(),
    )
}

private const val POINT_WEIGHT = 0.6f
private const val TYPE_WEIGHT = 0.3f
