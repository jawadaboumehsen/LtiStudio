/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.debloat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.debloat.DebloatCandidate
import org.ide.lti.core.domain.debloat.DebloatSuggestions
import org.ide.lti.feature.rom.studio.api.formatBytes

/**
 * Apps the ROM itself says can go (`DebloatSuggestions`): nothing it declares relies on them, nothing warns, no boot
 * start; the largest first. Picking one goes through the same checks as a pick in Inventory.
 */
internal fun LazyListScope.suggestions(facts: DebloatFacts, picked: Set<String>, onToggle: (DebloatCandidate) -> Unit) {
    if (facts.apps.isEmpty()) return
    val suggested = DebloatSuggestions.of(facts.inventory, facts.apps, picked)
    val total = formatBytes(suggested.sumOf { it.candidate.sizeBytes })
    item {
        Hint(
            if (suggested.isEmpty()) {
                "No suggestions: every app left is relied on, warned about or starts at boot."
            } else {
                "Suggested by the ROM (${suggested.size}, $total): nothing the ROM declares relies on them. " +
                    "What they do at run time is not in any file."
            },
        )
    }
    items(suggested) { s ->
        Row(
            modifier = Modifier.fillMaxWidth().testTag("Suggestion_${s.packageId}"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Line("${s.label ?: s.packageId}  ${formatBytes(s.candidate.sizeBytes)}")
                Hint("${s.packageId} · ${s.reasons.joinToString("; ")}")
            }
            GlassButton(onClick = { onToggle(s.candidate) }, variant = GlassButtonVariant.Standard) {
                Text("Pick", fontSize = FontSize.BodySmall)
            }
        }
    }
}
