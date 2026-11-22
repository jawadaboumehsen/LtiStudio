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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.workbench.CompressionGoal
import org.ide.lti.core.domain.workbench.CompressionStep
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.feature.rom.studio.api.formatBytes

/** A partition's own compression choices before a goal changed them, to put back. */
public data class CompressionBefore(val compression: String?, val dedupe: Boolean?, val clusterKib: Int?)

/** What the compression half of the size goal shows: the measuring, its steps and what Undo puts back. */
public data class CompressionGoalState(
    val measuring: Boolean = false,
    /** The partition being tried now, as "system (1 of 7)". */
    val trying: String? = null,
    val steps: List<CompressionStep>? = null,
    val error: String? = null,
    /** Partitions this goal changed, with what they had: "Undo" puts it back. */
    val changed: Map<String, CompressionBefore> = emptyMap(),
)

/** Measures what compression would save ([CompressionGoal]) and applies or undoes the chosen steps. */
public class CompressionGoalViewModel(
    private val workspaceId: String,
    private val goal: CompressionGoal? = null,
    private val store: WorkspaceSettingsStore? = null,
) : ViewModel() {
    private val saves = Mutex()
    private val _state = MutableStateFlow(CompressionGoalState())
    public val state: StateFlow<CompressionGoalState> = _state.asStateFlow()

    /** Trials compression until [shortBytes] are covered; ignored while one is running. */
    public fun measure(shortBytes: Long) {
        val measurer = goal ?: return
        if (_state.value.measuring) return
        _state.update { it.copy(measuring = true, error = null) }
        viewModelScope.launch {
            try {
                val steps = measurer.measure(workspaceId, shortBytes) { p -> _state.update { it.copy(trying = p) } }
                _state.update { it.copy(steps = steps) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RefusalException) {
                _state.update { it.copy(error = "${e.refusal.cause} ${e.refusal.nextStep}") }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                _state.update { it.copy(error = e.message ?: e::class.simpleName) }
            } finally {
                _state.update { it.copy(measuring = false, trying = null) }
            }
        }
    }

    /** Saves [steps]' options as their partitions' own compression choices, remembering what was there. */
    public fun use(steps: List<CompressionStep>) {
        val settings = store ?: return
        viewModelScope.launch {
            saves.withLock {
                var before = emptyMap<String, CompressionBefore>()
                settings.update(workspaceId) { snapshot ->
                    val build = snapshot.build
                    before = steps.associate { s ->
                        s.partition to CompressionBefore(
                            build.partitionCompression[s.partition],
                            build.partitionDedupe[s.partition],
                            build.partitionClusterKib[s.partition],
                        )
                    }
                    snapshot.copy(build = steps.fold(build) { b, s -> s.option.applyTo(b, s.partition) })
                }.onSuccess { _state.update { it.copy(changed = before + it.changed) } }
                    .onFailure { e ->
                        _state.update { it.copy(error = e.message ?: "The settings could not be saved") }
                    }
            }
        }
    }

    /** Puts back what [use] changed, partition by partition (other build settings edited since stay). */
    public fun undo() {
        val settings = store ?: return
        viewModelScope.launch {
            saves.withLock {
                val changed = _state.value.changed
                settings.update(workspaceId) { snapshot ->
                    snapshot.copy(build = changed.entries.fold(snapshot.build) { b, (p, was) -> restore(b, p, was) })
                }.onSuccess { _state.update { it.copy(changed = emptyMap()) } }
                    .onFailure { e ->
                        _state.update { it.copy(error = e.message ?: "The settings could not be saved") }
                    }
            }
        }
    }

    /** Settings [b] with partition [p]'s own compression entries put back to [was]. */
    private fun restore(b: BuildSettings, p: String, was: CompressionBefore): BuildSettings = b.copy(
        partitionCompression =
        was.compression?.let { b.partitionCompression + (p to it) } ?: (b.partitionCompression - p),
        partitionDedupe = was.dedupe?.let { b.partitionDedupe + (p to it) } ?: (b.partitionDedupe - p),
        partitionClusterKib = was.clusterKib?.let { b.partitionClusterKib + (p to it) } ?: (b.partitionClusterKib - p),
    )
}

/** The size goal's compression half, shown when the apps leave it [shortBytes] short. */
@Composable
internal fun CompressionGoalCard(vm: CompressionGoalViewModel, shortBytes: Long) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.testTag("CompressionGoal"),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        GlassButton(
            onClick = { vm.measure(shortBytes) },
            loading = state.measuring,
            variant = GlassButtonVariant.Standard,
            modifier = Modifier.testTag("CompressionGoalMeasure"),
        ) {
            Text(
                when {
                    state.trying != null -> "Trying ${state.trying}â€¦"
                    state.measuring -> "Building trial imagesâ€¦"
                    else -> "Measure compression for the rest"
                },
                fontSize = FontSize.BodySmall,
            )
        }
        Hint(
            "Builds the largest partitions first, a few ways each, and stops once the rest is covered. A trial is a " +
                "full build of that partition: system with deduplication takes ten minutes or more.",
        )
        state.error?.let { Hint(it) }
        val steps = state.steps ?: return@Column
        if (steps.isEmpty()) {
            Hint("No partition builds smaller another way the boot kernel reads.")
            return@Column
        }
        val chosen = CompressionGoal.pick(steps, shortBytes)
        val saved = chosen.sumOf { it.savingBytes }
        Hint(
            "Measured: ${formatBytes(saved)} saved by ${chosen.size} partition(s)" +
                (if (saved < shortBytes) ", still ${formatBytes(shortBytes - saved)} short" else "") +
                ". Sizes are trial builds of today's work tree, without the AVB hashtree.",
        )
        chosen.forEach { s ->
            val was = s.nowSeconds
            val now = s.seconds
            val time = if (was != null && now != null) ", build ${was.toInt()} s -> ${now.toInt()} s" else ""
            Line("- ${s.partition}: ${s.option.label}, ${formatBytes(s.nowBytes)} -> ${formatBytes(s.bytes)}$time")
        }
        Hint("Undo puts each partition's own compression settings back.")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            GlassButton(
                onClick = { vm.use(chosen) },
                variant = GlassButtonVariant.Standard,
                modifier = Modifier.testTag("CompressionGoalUse"),
            ) { Text("Use these ${chosen.size}", fontSize = FontSize.BodySmall) }
            if (state.changed.isNotEmpty()) {
                GlassButton(
                    onClick = vm::undo,
                    variant = GlassButtonVariant.Text,
                    modifier = Modifier.testTag("CompressionGoalUndo"),
                ) { Text("Undo", fontSize = FontSize.BodySmall) }
            }
        }
    }
}
