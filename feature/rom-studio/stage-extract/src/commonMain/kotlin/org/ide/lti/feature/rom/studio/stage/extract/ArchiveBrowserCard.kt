/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.extract

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.IdeTable
import org.ide.lti.core.designsystem.component.display.IdeTableHeader
import org.ide.lti.core.designsystem.component.display.IdeTableHeaderCell
import org.ide.lti.core.designsystem.component.display.IdeTableRow
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.toolkit.ArchiveMember
import org.ide.lti.core.ui.toolkit.RefusalView
import org.ide.lti.feature.rom.studio.api.formatBytes

/**
 * The firmware archive and the archives inside it: each member with its size, packed size and
 * packing method; "Open" goes into a member that is itself an archive; selected members are copied out.
 */
@Composable
internal fun ArchiveBrowserCard(browse: ArchiveBrowse, actions: ArchiveActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium)
            .testTag("ArchiveBrowserCard"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Inside the archive",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.TitleSmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
                modifier = Modifier.weight(1f),
            )
            if (browse.chain.isNotEmpty()) {
                GlassButton(
                    onClick = actions.up,
                    enabled = browse.busy == null,
                    variant = GlassButtonVariant.Secondary,
                ) {
                    Text("Up", fontSize = FontSize.BodySmall)
                }
            }
            GlassButton(onClick = actions.pickArchive, enabled = browse.busy == null) {
                Text("Look inside another file…", fontSize = FontSize.BodySmall)
            }
        }
        val archive = browse.archive
        if (archive == null) {
            Note("No archive yet: import one in Acquire, or look inside a file on this PC.")
            return@Column
        }
        // Where we are: the archive, then each member opened inside it.
        Note((listOf(archive.substringAfterLast('/').substringAfterLast('\\')) + browse.chain).joinToString("  ›  "))
        val listing = browse.listing
        listing?.let { l ->
            Note("${l.format.name} · ${l.members.count { !it.folder }} files. Tick members to copy them out.")
        }
        if (browse.selected.isNotEmpty()) {
            GlassButton(
                onClick = actions.copyOut,
                enabled = browse.busy == null,
                modifier = Modifier.testTag("ArchiveCopyOut"),
            ) {
                Text("Copy out ${browse.selected.size} selected", fontSize = FontSize.BodySmall)
            }
        }
        browse.busy?.let { Note("$it…") }
        browse.refusal?.let { RefusalView(refusal = it) }
        browse.message?.let { GlassBanner(severity = GlassBannerSeverity.INFO, title = it) }
        listing?.let { MemberTable(it.members.filterNot(ArchiveMember::folder), browse, actions) }
    }
}

@Composable
private fun MemberTable(members: List<ArchiveMember>, browse: ArchiveBrowse, actions: ArchiveActions) {
    IdeTable(modifier = Modifier.fillMaxWidth()) {
        IdeTableHeader {
            IdeTableHeaderCell(text = "", modifier = Modifier.width(GlassDimens.AcquireTableIconColWidth))
            IdeTableHeaderCell(text = "Member", modifier = Modifier.weight(PATH_WEIGHT))
            IdeTableHeaderCell(text = "Size", modifier = Modifier.weight(1f))
            IdeTableHeaderCell(text = "Packed", modifier = Modifier.weight(1f))
            IdeTableHeaderCell(text = "Method", modifier = Modifier.weight(1f))
            IdeTableHeaderCell(text = "", modifier = Modifier.width(GlassDimens.AcquireTableActionColWidth))
        }
        members.take(MAX_ROWS).forEach { m ->
            val picked = m.path in browse.selected
            IdeTableRow(isSelected = picked, onClick = { actions.toggle(m.path) }) {
                Checkbox(
                    checked = picked,
                    onCheckedChange = { actions.toggle(m.path) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.width(GlassDimens.AcquireTableIconColWidth),
                )
                Cell(m.path, Modifier.weight(PATH_WEIGHT))
                Cell(m.size?.let(::formatBytes) ?: "?", Modifier.weight(1f))
                Cell(m.packedSize?.let(::formatBytes) ?: "?", Modifier.weight(1f))
                Cell(m.method ?: "-", Modifier.weight(1f))
                // Any file may be an archive; the browser reads its first bytes before unpacking anything.
                GlassButton(
                    onClick = { actions.enter(m.path) },
                    enabled = browse.busy == null,
                    variant = GlassButtonVariant.Secondary,
                    modifier = Modifier.width(GlassDimens.AcquireTableActionColWidth).testTag("ArchiveOpen_${m.path}"),
                ) {
                    Text("Open", fontSize = FontSize.Micro)
                }
            }
        }
    }
    if (members.size > MAX_ROWS) Note("and ${members.size - MAX_ROWS} more")
}

@Composable
private fun Cell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = FontSize.BodySmall,
        fontFamily = codeFontFamily(),
        maxLines = 1,
        modifier = modifier,
    )
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

private const val PATH_WEIGHT = 3f
private const val MAX_ROWS = 200
