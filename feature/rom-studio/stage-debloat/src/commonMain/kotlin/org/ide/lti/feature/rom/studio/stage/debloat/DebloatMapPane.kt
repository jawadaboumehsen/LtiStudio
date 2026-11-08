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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.debloat.AppMap
import org.ide.lti.core.domain.debloat.Loss
import org.ide.lti.core.domain.debloat.Reliance

/**
 * Debloat > App map: what breaks if an app goes. The apps that rely on it stand left with an arrow to it, coloured by
 * what they lose; what it relies on stands right. Only what the ROM's manifests declare is drawn.
 */
internal fun LazyListScope.appMap(facts: DebloatFacts) {
    item { AppMapCard(facts) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppMapCard(facts: DebloatFacts) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    MapCard {
        MapTitle("What breaks if this goes")
        Hint(
            "Pick an app. Every arrow is something a manifest of this ROM declares: a library an app needs, a " +
                "permission it asks for that only this app declares, an overlay that dresses it.",
        )
        GlassTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search apps by name or package",
            modifier = Modifier.fillMaxWidth().testTag("AppMapSearch"),
        )
        val matches = remember(facts.apps, query) {
            facts.apps.entries.filter { (_, app) ->
                query.isNotBlank() && (
                    app.packageId?.contains(query, ignoreCase = true) == true ||
                        app.label?.contains(query, ignoreCase = true) == true
                    )
            }.take(MATCHES)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            matches.forEach { (selector, app) ->
                GlassChip(
                    label = app.label ?: app.packageId ?: selector,
                    selected = selector == selected,
                    onClick = { selected = selector },
                    modifier = Modifier.testTag("AppMapPick_${app.packageId}"),
                )
            }
        }
        if (facts.apps.isEmpty()) Hint("The manifests are not read yet: refresh on Inventory.")
    }
    val map = selected?.let { remember(facts.apps, it) { facts.map(it) } }
    if (map != null) {
        MapCard {
            MapTitle(facts.apps[selected]?.label?.let { "$it (${map.packageId})" } ?: map.packageId)
            Hint(
                when (val worst = map.worst) {
                    null -> "Nothing in this ROM declares that it relies on it."
                    else -> "${map.reliedOnBy.size} app(s) rely on it; the worst: one ${worst.words}."
                },
            )
            RelianceMap(map)
            Legend()
            Reasons(map)
            Hint(
                "The map shows what the ROM declares. Apps also reach each other while the device runs (intents, " +
                    "bound services, content providers); no file in the firmware records that.",
            )
        }
    }
}

/** The apps left of the middle rely on the picked app; the picked app relies on those right of it. */
@Composable
private fun RelianceMap(map: AppMap) {
    val measurer = rememberTextMeasurer()
    val left = map.reliedOnBy.take(SHOWN)
    val right = map.reliesOn.take(SHOWN)
    val rows = maxOf(left.size, right.size, 1)
    val colors = lossColors()
    val text = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val style = TextStyle(fontSize = FontSize.Micro, fontFamily = codeFontFamily(), color = text)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(ComponentSize.ButtonMinHeight * rows)
            .testTag("AppMapCanvas"),
    ) {
        val row = size.height / rows
        val column = size.width / COLUMNS
        val center = Offset(size.width / 2, size.height / 2)
        fun label(name: String, at: Offset, color: Color) {
            val laid = measurer.measure(
                name,
                style.copy(color = color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = column.toInt()),
            )
            drawText(laid, topLeft = Offset(at.x - laid.size.width / 2, at.y - laid.size.height / 2))
        }
        left.forEachIndexed { i, r ->
            val at = Offset(column / 2, row * (i + HALF))
            arrow(Offset(column, at.y), Offset(center.x - column / 2, center.y), colors.getValue(r.loss))
            label(r.from, at, text)
        }
        right.forEachIndexed { i, r ->
            val at = Offset(size.width - column / 2, row * (i + HALF))
            arrow(Offset(center.x + column / 2, center.y), Offset(size.width - column, at.y), colors.getValue(r.loss))
            label(r.to, at, muted)
        }
        label(map.packageId, center, text)
    }
    val more = (map.reliedOnBy.size - left.size) + (map.reliesOn.size - right.size)
    if (more > 0) Hint("$more more not drawn; all are listed below.")
}

/** A line from [from] to [to] with a head at [to]. */
private fun DrawScope.arrow(from: Offset, to: Offset, color: Color) {
    val width = StrokeWidth.Focused.toPx()
    drawLine(color, from, to, strokeWidth = width)
    val dir = (to - from).let { it / it.getDistance().coerceAtLeast(1f) }
    val head = HEAD * width
    val side = Offset(-dir.y, dir.x) * (head / 2)
    val back = to - dir * head
    drawPath(
        Path().apply {
            moveTo(to.x, to.y)
            lineTo(back.x + side.x, back.y + side.y)
            lineTo(back.x - side.x, back.y - side.y)
            close()
        },
        color,
    )
}

@Composable
private fun lossColors(): Map<Loss, Color> = with(GlassTheme.diagnosticColors) {
    mapOf(Loss.STOPS to error, Loss.FEATURE to gitModified, Loss.PERMISSION to warning, Loss.SKIN to info)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val colors = lossColors()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        Loss.entries.forEach { loss ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Canvas(Modifier.height(Spacing.Small).padding(horizontal = Spacing.Small)) {
                    drawLine(
                        colors.getValue(loss),
                        Offset(-size.height * 2, center.y),
                        Offset(size.height * 2, center.y),
                        StrokeWidth.Focused.toPx(),
                    )
                }
                Text(loss.words, color = colors.getValue(loss), fontSize = FontSize.Micro, fontFamily = ideFontFamily())
            }
        }
    }
}

/** Every arrow in words, with the declarations behind it. */
@Composable
private fun Reasons(map: AppMap) {
    val colors = lossColors()
    fun line(r: Reliance, other: String) = "$other ${r.loss.words}: ${r.reasons.joinToString("; ")}"
    map.reliedOnBy.forEach { r -> Reason(line(r, r.from), colors.getValue(r.loss)) }
    if (map.reliesOn.isNotEmpty()) {
        Hint("It relies on:")
        map.reliesOn.forEach { r ->
            Reason(
                "${r.to}: without it, this app ${r.loss.words} (${r.reasons.joinToString("; ")})",
                colors.getValue(r.loss),
            )
        }
    }
}

@Composable
private fun Reason(text: String, color: Color) {
    Text(text, color = color, fontSize = FontSize.BodySmall, fontFamily = codeFontFamily())
}

@Composable
private fun MapCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium)
            .testTag("AppMapCard"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) { content() }
}

@Composable
private fun MapTitle(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = FontSize.TitleSmall,
        fontWeight = FontWeight.SemiBold,
        fontFamily = ideFontFamily(),
    )
}

private const val MATCHES = 12
private const val SHOWN = 24
// Labels left and right a quarter each, the app in the middle quarter, the arrows in between.
private const val COLUMNS = 4f
private const val HALF = 0.5f
private const val HEAD = 4f
