/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.publish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.TextFieldState
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.usecase.publish.PublishJournal
import org.ide.lti.core.domain.usecase.publish.PublishStepState
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.CollisionPolicy
import org.ide.lti.core.model.workspace.PublishProvider
import org.ide.lti.core.model.workspace.PublishSettings

@Suppress("LongParameterList", "UnusedParameter")
@Composable
public fun PublishEditor(
    settings: PublishSettings,
    target: TargetDevice,
    validationErrors: List<ValidationError> = emptyList(),
    onChange: (PublishSettings) -> Unit,
    dirtyPaths: Set<String> = emptySet(),
    selectedObjectId: String = "provider",
    state: PublishUiState = PublishUiState(),
    onRefresh: () -> Unit = {},
    onPublish: () -> Unit = {},
    onCancelPublish: () -> Unit = {},
    onValidate: () -> Unit = {},
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val errors = validationErrors.filter { it.fieldPath.startsWith("publish.") || !it.fieldPath.contains('.') }
    LaunchedEffect(settings) { onRefresh() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("PublishEditor"),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = GlassDimens.OverviewMaxContentWidth)
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            when (selectedObjectId.lowercase()) {
                "release-repository-and-tag", "release_repository_and_tag" -> item {
                    RepositoryCard(settings, dirtyPaths, onChange)
                }
                "split-assets", "split_assets" -> item {
                    SplitAssetsCard(settings, dirtyPaths, onChange)
                }
                "credentials" -> item {
                    CredentialsCard()
                }
                "ota-manifest", "ota_manifest" -> item {
                    OtaManifestCard(settings, dirtyPaths, onChange)
                }
                "retention" -> item {
                    RetentionCard(settings, dirtyPaths, onChange)
                }
                "verification" -> item {
                    VerificationCard(state)
                }
                "publish-review-and-recovery", "publish_review_and_recovery" -> item {
                    ReviewCard(state, onPublish, onCancelPublish)
                }
                else -> item {
                    ProviderCard(settings, dirtyPaths, onChange)
                }
            }
            item { Diagnostics(errors) }
        }
    }
}

@Composable
private fun ProviderCard(settings: PublishSettings, dirtyPaths: Set<String>, onChange: (PublishSettings) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Distribution Provider",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Select target release channel delivery platform:",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            PublishProvider.entries.forEach { provider ->
                GlassChip(
                    label = provider.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                    selected = settings.provider == provider,
                    onClick = { onChange(settings.copy(provider = provider)) },
                )
            }
        }
        if (settings.provider == PublishProvider.LOCAL_EXPORT) {
            GlassTextField(
                settings.localExportFolder.orEmpty(),
                { onChange(settings.copy(localExportFolder = it.ifBlank { null })) },
                label = "Export folder",
                state = TextFieldState.Helper("A Windows folder, e.g. D:\\ROM\\releases"),
                modifier = Modifier.fillMaxWidth().testTag("PublishExportFolder"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                CollisionPolicy.entries.forEach { policy ->
                    GlassChip(
                        label = "If it exists: " + policy.name.lowercase(),
                        selected = settings.collisionPolicy == policy,
                        onClick = { onChange(settings.copy(collisionPolicy = policy)) },
                    )
                }
            }
        }
        Meta("provider", dirtyPaths, "Provider", "Where the signed package and its OTA manifest go.")
    }
}

@Composable
private fun RepositoryCard(settings: PublishSettings, dirtyPaths: Set<String>, onChange: (PublishSettings) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Repository & Release Tag",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        GlassTextField(
            settings.repository.orEmpty(),
            { onChange(settings.copy(repository = it)) },
            label = "Target repository (owner/name)",
            state = TextFieldState.Helper("e.g., openMF/lti-device-releases"),
        )
        GlassTextField(
            settings.sourceTag.orEmpty(),
            { onChange(settings.copy(sourceTag = it.ifBlank { null })) },
            label = "Git release tag",
            state = TextFieldState.Helper("e.g., v1.0.0-candidate"),
        )
        Text(
            text = "Collision Policy",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            CollisionPolicy.entries.forEach { policy ->
                GlassChip(
                    label = policy.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = settings.collisionPolicy == policy,
                    onClick = { onChange(settings.copy(collisionPolicy = policy)) },
                )
            }
        }
        Meta("repository", dirtyPaths, "Repository", "Git tag and target release location.")
    }
}

@Composable
private fun SplitAssetsCard(settings: PublishSettings, dirtyPaths: Set<String>, onChange: (PublishSettings) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Split assets",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "GitHub takes files up to 2 GB: a bigger package goes up in parts (.zip.00, .zip.01, …).",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoundedNumberField(
            value = settings.partSizeBytes / MB,
            range = 1..MAX_PART_MB,
            label = "Part size (MB)",
            helper = "1 to 2000 MB. The default, 1900 MB, stays under GitHub's limit.",
        ) { onChange(settings.copy(partSizeBytes = it * MB)) }
        Meta(
            "partSizeBytes",
            dirtyPaths,
            "Split assets",
            "A split package cannot be an OTA download; devices need one file.",
        )
    }
}

@Composable
private fun CredentialsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "GitHub sign-in",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Publishing uses the GitHub CLI (gh) signed in inside WSL; the app stores no token or password. " +
                "Publish checks the sign-in first. To sign in, run this in a Windows terminal:",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "wsl -e bash -lc '~/LtiRomTools/bin/gh/current/gh auth login'",
            fontSize = FontSize.Micro,
            fontFamily = codeFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun OtaManifestCard(settings: PublishSettings, dirtyPaths: Set<String>, onChange: (PublishSettings) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "OTA manifest",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        GlassTextField(
            settings.manifestRepository.orEmpty(),
            { onChange(settings.copy(manifestRepository = it.ifBlank { null })) },
            label = "Manifest repository (owner/name)",
            state = TextFieldState.Helper("Where devices read update info. Empty: no manifest is published."),
        )
        GlassTextField(
            settings.manifestPath.orEmpty(),
            { onChange(settings.copy(manifestPath = it.ifBlank { null })) },
            label = "File in that repository",
            state = TextFieldState.Helper("e.g. PQ84P01.json"),
        )
        GlassTextField(
            settings.manifestBranch.orEmpty(),
            { onChange(settings.copy(manifestBranch = it.ifBlank { null })) },
            label = "Branch",
            state = TextFieldState.Helper("e.g. main or gh-pages"),
        )
        Meta(
            "manifestRepository",
            dirtyPaths,
            "OTA manifest",
            "Publish adds this release to it, keeping the newest per device.",
        )
    }
}

@Composable
private fun RetentionCard(settings: PublishSettings, dirtyPaths: Set<String>, onChange: (PublishSettings) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Retention Policy",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Retention removes only older per-device manifest entries, not ROM assets or local downloads.",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoundedNumberField(
            value = settings.retentionPerDevice.toLong(),
            range = 1..MAX_RETAINED,
            label = "Retained manifest entries per device",
            helper = "Older manifest entries pruned after newer stable publish.",
        ) { onChange(settings.copy(retentionPerDevice = it.toInt())) }
        Meta("retentionPerDevice", dirtyPaths, "Retention", "Prunes older manifest entries only.")
    }
}

/** The checks Publish runs before it starts: a built, signed release and a destination. */
@Composable
private fun VerificationCard(state: PublishUiState) {
    Card("Before publishing") {
        val preflight = state.preflight
        when {
            preflight == null -> Hint("Checking the last build…")
            preflight.problems.isEmpty() -> {
                Hint("Ready: the package matches its manifest and has a destination.")
                preflight.release?.let { Mono("${it.zip}  ${size(it.size)}  sha256 ${it.sha256}") }
                preflight.destination?.let { Mono("to $it") }
            }
            else -> preflight.problems.forEach { Problem(it) }
        }
    }
}

/** Publish, after a confirmation; then each step of the attempt (publishing again resumes where it stopped). */
@Composable
private fun ReviewCard(state: PublishUiState, onPublish: () -> Unit, onCancel: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    val preflight = state.preflight
    val ready = preflight != null && preflight.problems.isEmpty() && !state.publishing
    Card("Publish") {
        val journal = state.journal
        val resumes = journal != null && !journal.finished && journal.sha256 == preflight?.release?.sha256
        PublishButtons(state, ready, resumes, onPublish = { confirming = true }, onCancel = onCancel)
        if (state.publishing && journal != null) PublishProgress(journal, state.transfer, state.bytesPerSecond)
        if (resumes && !state.publishing) Hint("Publishing again resumes here; finished steps are not redone.")
        // The result first: a finished publish says where it went, above the steps that got it there.
        journal?.outcome?.takeIf { !state.publishing }?.let { Success(it) }
        journal?.let { JournalSteps(it, state.transfer?.stepId.takeIf { state.publishing }) }
    }
    val release = preflight?.release
    if (confirming && release != null) {
        ConfirmPublish("${release.zip} goes to ${preflight.destination}.", onDismiss = { confirming = false }) {
            confirming = false
            onPublish()
        }
    }
}

@Composable
private fun JournalSteps(journal: PublishJournal, runningId: String?) {
    journal.steps.forEach { step ->
        val mark = when {
            step.id == runningId -> "running"
            step.state == PublishStepState.DONE -> "done"
            step.state == PublishStepState.FAILED -> "failed"
            else -> "to do"
        }
        Mono("${step.label}: $mark")
        // A running step's detail is its last attempt's result; the progress above says how it goes now.
        step.detail?.takeIf { step.id != runningId }?.let {
            if (step.state == PublishStepState.FAILED) Problem(it) else Hint(it)
        }
    }
}

@Composable
private fun Success(text: String) {
    Text(
        text = text,
        fontSize = FontSize.BodySmall,
        fontWeight = FontWeight.SemiBold,
        fontFamily = ideFontFamily(),
        color = GlassTheme.diagnosticColors.success,
        modifier = Modifier.testTag("PublishOutcome"),
    )
}

@Composable
private fun ConfirmPublish(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Publish this release?",
        confirmButton = {
            GlassButton(onClick = onConfirm, variant = GlassButtonVariant.Primary) { Text("Publish") }
        },
        dismissButton = {
            GlassButton(onClick = onDismiss, variant = GlassButtonVariant.Standard) { Text("Cancel") }
        },
    ) {
        Text(
            text = text,
            fontSize = FontSize.BodySmall,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = title,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
internal fun Mono(text: String) {
    Text(
        text = text,
        fontSize = FontSize.Micro,
        fontFamily = codeFontFamily(),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
internal fun Hint(text: String) {
    Text(
        text = text,
        fontSize = FontSize.Micro,
        fontFamily = ideFontFamily(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Problem(text: String) {
    Text(
        text = text,
        fontSize = FontSize.Micro,
        fontFamily = ideFontFamily(),
        color = GlassTheme.diagnosticColors.error,
    )
}

@Composable
private fun Meta(path: String, dirty: Set<String>, source: String, help: String) {
    val isDirty = dirty.any { it.startsWith(path.substringBefore('/')) }
    Text(
        text = "$source${if (isDirty) " · Dirty" else ""} · $help",
        fontSize = FontSize.Micro,
        fontFamily = codeFontFamily(),
        color = if (isDirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Diagnostics(errors: List<ValidationError>) {
    if (errors.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
        errors.forEach {
            Text(
                text = "${it.severity}: ${it.message}",
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
                color = if (it.severity == Severity.ERROR) {
                    GlassTheme.diagnosticColors.error
                } else {
                    GlassTheme.diagnosticColors.warning
                },
            )
        }
    }
}

private const val MB = 1_000_000L
private const val MAX_PART_MB = 2_000L
private const val MAX_RETAINED = 100L

/** Keeps what is typed while it is out of [range] or empty, says so, and saves only a value inside it. */
@Composable
private fun BoundedNumberField(value: Long, range: LongRange, label: String, helper: String, onValid: (Long) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val valid = text.toLongOrNull()?.takeIf { it in range }
    GlassTextField(
        text,
        { typed ->
            text = typed
            typed.toLongOrNull()?.takeIf { it in range }?.let(onValid)
        },
        label = label,
        state = if (valid == null) {
            TextFieldState.Error("Not saved: enter a number from ${range.first} to ${range.last}.")
        } else {
            TextFieldState.Helper(helper)
        },
    )
}
