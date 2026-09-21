package com.kashif_e.backdrop.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Node-owned hover/press/focus optics controller - mirrors Haze's `GlassInteractionController`,
 * narrowed to a single Compose [InteractionSource] input. `:backdrop` is desktop-only and every
 * real caller already routes hover/press through `Modifier.clickable`/`hoverable`'s
 * [InteractionSource], so Haze's separate raw-`PointerInputChange` channel (built for platforms
 * and call sites without one) isn't needed here.
 *
 * Owns four independent [Animatable] channels (see [BackdropInteractionScope]) and exposes a
 * cached [renderState] snapshot. State changes call only [invalidateDraw] (cheap, draw-phase-only)
 * - never `DrawBackdropNode`'s `GlassEffectDirtyFields` path, which is driven exclusively by
 * composition-supplied `ResolvedGlassEffect` changes. Attached once per `DrawBackdropNode` via
 * its `onAttach`/`onDetach` lifecycle.
 */
internal class BackdropInteractionController(
    private val scope: CoroutineScope,
    private val invalidateDraw: () -> Unit,
) {
    private val blurMultiplier = Animatable(1f)
    private val lensMultiplier = Animatable(1f)
    private val brightnessDelta = Animatable(0f)
    private val scaleAnimatable = Animatable(1f)

    private var response = BackdropInteractionResponse()
    private var animationSpec: AnimationSpec<Float> = DefaultAnimationSpec
    private var interactionSource: InteractionSource? = null
    private var sourceJob: Job? = null
    private val presses = mutableSetOf<PressInteraction.Press>()
    private val hovers = mutableSetOf<HoverInteraction.Enter>()
    private val focuses = mutableSetOf<FocusInteraction.Focus>()
    private var disposed = false
    private var renderStateSnapshot = BackdropInteractionRenderState()

    /** Cached unless a channel's animated value actually moved since the last read. */
    val renderState: BackdropInteractionRenderState
        get() {
            val blur = blurMultiplier.value
            val lens = lensMultiplier.value
            val brightness = brightnessDelta.value
            val scaleValue = scaleAnimatable.value
            return renderStateSnapshot.takeIf {
                it.blurMultiplier == blur &&
                    it.lensMultiplier == lens &&
                    it.brightnessDelta == brightness &&
                    it.scale == scaleValue
            } ?: BackdropInteractionRenderState(blur, lens, brightness, scaleValue)
                .also { renderStateSnapshot = it }
        }

    fun updateResponse(response: BackdropInteractionResponse, animationSpec: AnimationSpec<Float> = DefaultAnimationSpec) {
        if (disposed || (response == this.response && animationSpec == this.animationSpec)) return
        this.response = response
        this.animationSpec = animationSpec
        retarget()
    }

    fun updateInteractionSource(source: InteractionSource?) {
        if (disposed || source === interactionSource) return
        sourceJob?.cancel()
        interactionSource = source
        presses.clear()
        hovers.clear()
        focuses.clear()
        retarget()
        if (source != null) {
            sourceJob = scope.launch {
                source.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> presses += interaction
                        is PressInteraction.Release -> presses -= interaction.press
                        is PressInteraction.Cancel -> presses -= interaction.press
                        is HoverInteraction.Enter -> hovers += interaction
                        is HoverInteraction.Exit -> hovers -= interaction.enter
                        is FocusInteraction.Focus -> focuses += interaction
                        is FocusInteraction.Unfocus -> focuses -= interaction.focus
                    }
                    retarget()
                }
            }
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        sourceJob?.cancel()
        sourceJob = null
        interactionSource = null
        presses.clear()
        hovers.clear()
        focuses.clear()
    }

    /** Pressed > hovered > focused when more than one is simultaneously active - matches
     * `resolveGlassInteractionTargets`' last-applied-wins order in Haze. */
    private fun retarget() {
        val active = when {
            presses.isNotEmpty() -> response.pressed
            hovers.isNotEmpty() -> response.hovered
            focuses.isNotEmpty() -> response.focused
            else -> null
        }
        animateTo(blurMultiplier, active?.blurMultiplier ?: 1f)
        animateTo(lensMultiplier, active?.lensMultiplier ?: 1f)
        animateTo(brightnessDelta, active?.brightnessDelta ?: 0f)
        animateTo(scaleAnimatable, active?.scale ?: 1f)
    }

    private fun animateTo(animatable: Animatable<Float, AnimationVector1D>, target: Float) {
        if (animatable.targetValue == target) return
        scope.launch {
            animatable.animateTo(target, animationSpec) { invalidateDraw() }
        }
    }
}

/** A [BackdropInteractionController]'s current animated values, applied at draw time. */
internal data class BackdropInteractionRenderState(
    val blurMultiplier: Float = 1f,
    val lensMultiplier: Float = 1f,
    val brightnessDelta: Float = 0f,
    val scale: Float = 1f,
)

private val DefaultAnimationSpec: AnimationSpec<Float> = spring(stiffness = 300f)
