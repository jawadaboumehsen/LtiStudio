/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.acquire.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.TextFieldState
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.feature.rom.studio.stage.acquire.IntegrityFacts

/** The archive the build uses and whether its measured SHA-256 matches the expected one. */
@Composable
public fun IntegrityPane(
    settings: AcquisitionSettings,
    facts: IntegrityFacts,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    /** Sets (or clears, with null) the SHA-256 the archive must have. */
    onExpectedChange: (String?) -> Unit = {},
) {
    LaunchedEffect(settings.archiveRef, settings.mode) { onRefresh() }
    val expected = settings.expectedSha256?.takeIf { it.isNotBlank() }
        ?: settings.firmware.sha256?.takeIf { it.isNotBlank() }
    val measured = facts.measuredSha256
    val (title, detail, color) = when {
        measured == null -> Triple(
            "Not measured yet",
            "The SHA-256 is measured when the Acquire stage runs. Run the build through Acquire.",
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        expected == null -> Triple(
            "Measured, nothing to compare",
            "No expected SHA-256 is set (the vendor publishes none). Set one below to verify future runs.",
            GlassTheme.diagnosticColors.warning,
        )
        measured.equals(expected, ignoreCase = true) -> Triple(
            "Verified",
            "The archive matches the expected SHA-256.",
            GlassTheme.diagnosticColors.success,
        )
        else -> Triple(
            "Mismatch",
            "The archive does not match the expected SHA-256; the Acquire stage fails until they agree.",
            GlassTheme.diagnosticColors.error,
        )
    }
    Column(
        modifier = modifier.fillMaxWidth().testTag("IntegrityPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Card {
            Text(
                text = title,
                color = color,
                fontSize = FontSize.TitleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = ideFontFamily(),
                modifier = Modifier.testTag("IntegrityStatus"),
            )
            Text(
                text = detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.BodySmall,
                fontFamily = ideFontFamily(),
            )
        }
        Card {
            Detail("Archive", facts.archivePath ?: "not in the workspace yet")
            Detail("Size", facts.archiveSizeBytes?.let { "$it bytes" } ?: "—")
            Detail("Source URL", settings.firmware.acquisitionUrl ?: "none (imported archive)")
            Detail("Expected SHA-256", expected ?: "not set")
            Detail("Measured SHA-256", measured ?: "—", if (measured != null && expected != null) color else null)
            GlassButton(onClick = onRefresh, variant = GlassButtonVariant.Standard, shape = GlassShapes.ShellControl) {
                Text("Refresh", color = MaterialTheme.colorScheme.onSurface, fontSize = FontSize.BodySmall)
            }
        }
        ExpectedCard(settings.expectedSha256.orEmpty(), measured, onExpectedChange)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) { content() }
}

@Composable
private fun Detail(label: String, value: String, valueColor: Color? = null) {
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

/** The SHA-256 the archive must have: typed, or the measured one adopted; the build stops on a mismatch. */
@Composable
private fun ExpectedCard(current: String, measured: String?, onExpectedChange: (String?) -> Unit) {
    var text by remember(current) { mutableStateOf(current) }
    val valid = text.isEmpty() || SHA256.matches(text.trim())
    Card {
        Text(
            text = "Expected SHA-256",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )
        GlassTextField(
            value = text,
            onValueChange = { value ->
                text = value
                val trimmed = value.trim().lowercase()
                when {
                    trimmed.isEmpty() -> onExpectedChange(null)
                    SHA256.matches(trimmed) -> onExpectedChange(trimmed)
                }
            },
            placeholder = "64 hex characters; empty = don't check",
            state = if (valid) TextFieldState.Default else TextFieldState.Error("Not a SHA-256 (64 hex characters)."),
            modifier = Modifier.fillMaxWidth().testTag("ExpectedSha256"),
        )
        if (measured != null && measured != current) {
            GlassButton(
                onClick = { onExpectedChange(measured) },
                variant = GlassButtonVariant.Standard,
                shape = GlassShapes.ShellControl,
                modifier = Modifier.testTag("UseMeasuredSha"),
            ) {
                Text("Use the measured one", color = MaterialTheme.colorScheme.onSurface, fontSize = FontSize.BodySmall)
            }
        }
    }
}

private val SHA256 = Regex("[0-9a-fA-F]{64}")
