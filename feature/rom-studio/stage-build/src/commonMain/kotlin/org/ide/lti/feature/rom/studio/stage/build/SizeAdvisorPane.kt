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
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.pipeline.toolkit.BuildFilesystems
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.pipeline.toolkit.SuperBudget
import org.ide.lti.core.domain.workbench.BootWorkbench
import org.ide.lti.core.domain.workbench.SizeAdvisor
import org.ide.lti.core.domain.workbench.TrialOption
import org.ide.lti.core.domain.workbench.TrialResult
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.BuildSettings
import kotlin.math.roundToInt

public data class SizeAdvisorState(
    val partition: String? = null,
    val results: List<TrialResult> = emptyList(),
    /** The room [partition] has in the super group with the others as they are. */
    val room: Long? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

/** Builds one partition a few ways and keeps the sizes and times (`SizeAdvisor`). */
public class SizeAdvisorViewModel(
    private val workspaceId: String,
    private val advisor: SizeAdvisor? = null,
    private val boot: BootWorkbench? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(SizeAdvisorState())
    public val state: StateFlow<SizeAdvisorState> = _state.asStateFlow()

    public fun trial(p: String, settings: BuildSettings, room: Long?, decoders: List<String>) {
        val tools = advisor ?: return
        if (_state.value.busy) return
        _state.value = SizeAdvisorState(partition = p, room = room, busy = true)
        viewModelScope.launch {
            try {
                val kernel = runCatching { boot?.kernelFacts(workspaceId)?.version }.getOrNull()
                val results = tools.trial(workspaceId, p, SizeAdvisor.options(settings, p, kernel, decoders))
                _state.update { it.copy(results = results) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RefusalException) {
                _state.update { it.copy(error = "${e.refusal.cause} ${e.refusal.nextStep}") }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                _state.update { it.copy(error = e.message ?: e::class.simpleName) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}

/** The super group budget Build & Sign shows: built sizes, else stock ones as the estimate. */
internal fun superBudget(target: TargetDevice, settings: BuildSettings, facts: BuildFacts): SuperBudget =
    SuperBudget.of(
        partitions = target.dynamicPartitions,
        filesystems = target.dynamicPartitions.associateWith { BuildFilesystems.of(settings, it) },
        built = facts.images.associate { it.path.removeSuffix(".img") to it.sizeBytes },
        stock = facts.stockImages.associate { it.path.removeSuffix(".img") to it.sizeBytes },
        groupBytes = target.superGroupBytes,
    )

/**
 * Size advisor: per EROFS partition, try it a few ways (as set now, a 64 KiB cluster, deduplicated, lzma when the
 * kernel reads them) and use the one the advice picks: the fastest that fits the room left, else the smallest.
 */
@Composable
internal fun SizeAdvisorCard(
    vm: SizeAdvisorViewModel,
    target: TargetDevice,
    settings: BuildSettings,
    facts: BuildFacts,
    onUse: (String, TrialOption) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val budget = superBudget(target, settings, facts)
    Card("Size advisor", tag = "SizeAdvisorCard") {
        Hint(
            "Builds one partition a few ways in workbench/trial (and deletes it), then says which to use: the " +
                "fastest that fits the room left in the super group, else the smallest. Dedupe takes minutes.",
        )
        // A kept partition is packaged from its stock image: its settings are not used.
        val tried = budget.rows
            .filter { it.filesystem == BuildFilesystems.EROFS && it.partition !in settings.keepStock }
        tried.forEach { row ->
            val room = budget.groupBytes - (budget.usedBytes - (row.bytes ?: 0L))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Detail(row.partition, "room ${mb(room)}")
                GlassButton(
                    onClick = { vm.trial(row.partition, settings, room, facts.erofsCompressions.orEmpty()) },
                    enabled = !state.busy,
                    variant = GlassButtonVariant.Standard,
                    modifier = Modifier.testTag("SizeTrial_${row.partition}"),
                ) {
                    Text(
                        if (state.busy && state.partition == row.partition) "Trying…" else "Try other settings",
                        fontSize = FontSize.BodySmall,
                    )
                }
            }
        }
        state.error?.let { Text(it, color = GlassTheme.diagnosticColors.error, fontSize = FontSize.BodySmall) }
        state.partition?.takeIf { state.results.isNotEmpty() }?.let { p -> TrialResults(p, state, onUse) }
    }
}

/** One partition's trials: size and time of each way, the advised one marked, each with "Use this". */
@Composable
private fun TrialResults(p: String, state: SizeAdvisorState, onUse: (String, TrialOption) -> Unit) {
    val best = SizeAdvisor.recommend(state.results, state.room)
    val room = state.room ?: Long.MAX_VALUE
    Hint("$p, room ${state.room?.let(::mb) ?: "unknown"}:")
    state.results.forEach { r ->
        val bytes = r.bytes
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Detail(
                r.option.label + if (r == best) " (advised)" else "",
                bytes?.let {
                    "${mb(
                        SizeAdvisor.signedBytes(it),
                    )} signed, in ${r.seconds?.let(::seconds) ?: "?"}"
                } ?: "failed: ${r.error}",
                GlassTheme.diagnosticColors.error.takeIf { bytes == null || SizeAdvisor.signedBytes(bytes) > room },
            )
            if (bytes != null) {
                GlassButton(onClick = { onUse(p, r.option) }, variant = GlassButtonVariant.Text) {
                    Text("Use this", fontSize = FontSize.BodySmall)
                }
            }
        }
    }
}

/** "0.4 s" under ten seconds (trials of small partitions take a fraction of one), else whole seconds. */
private fun seconds(s: Double): String = when {
    s < TENTH -> "< 0.1 s"
    s < TEN -> "${(s * TEN).roundToInt() / TEN} s"
    else -> "${s.roundToInt()} s"
}

private const val TEN = 10.0
private const val TENTH = 0.1
