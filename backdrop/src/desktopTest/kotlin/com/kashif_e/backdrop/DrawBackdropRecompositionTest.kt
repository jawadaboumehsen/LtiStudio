package com.kashif_e.backdrop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.highlight.Highlight
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Answers the question behind B2 (backdrop-refactor-plan-20260920.md §2, "rim/interaction split")
 * empirically instead of by inspection: does toggling a hover-driven `highlight` lambda - the
 * exact pattern `GlassButton.kt` uses (`highlight = { if (isHovered) Highlight.Ambient else
 * Highlight.Plain }`) - force [DrawBackdropNode] to recompute its effect graph (the expensive
 * part; see [DrawBackdropDiagnostics])?
 *
 * `highlight` is wired to a sibling `HighlightElement`, not a property of `DrawBackdropElement`
 * itself, so the two *should* already be decoupled. This test pins that down so B2 isn't
 * re-implemented against a problem the current engine doesn't have; the second test is a positive
 * control proving the counter it relies on actually detects a real invalidation.
 */
class DrawBackdropRecompositionTest {

    @AfterTest
    fun resetDiagnostics() {
        DrawBackdropDiagnostics.updateEffectsCallCount = 0
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun hoverDrivenHighlightDoesNotRetriggerEffectRecomputation() = runDesktopComposeUiTest(width = 160, height = 120) {
        var isHovered by mutableStateOf(false)

        setContent {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    drawRect(Color.Magenta, size = size * 0.5f)
                    drawRect(Color.Cyan, topLeft = Offset(size.width * 0.5f, size.height * 0.5f), size = size * 0.5f)
                }
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(80.dp, 60.dp)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(8.dp) },
                            effects = { blur(4f) },
                            highlight = { if (isHovered) Highlight.Ambient else Highlight.Plain },
                            onDrawSurface = { drawRect(Color.White.copy(alpha = 0.1f)) },
                        ),
                )
            }
        }
        waitForIdle()

        // Whatever the initial-attach count is (Compose may run the observed-reads block more
        // than once while layout/attach settle) is not the claim under test - only whether hover
        // toggling adds to it afterward.
        val afterInitialComposition = DrawBackdropDiagnostics.updateEffectsCallCount

        repeat(5) {
            isHovered = !isHovered
            waitForIdle()
        }

        assertEquals(
            afterInitialComposition,
            DrawBackdropDiagnostics.updateEffectsCallCount,
            "hover toggling the highlight lambda must not retrigger DrawBackdropNode's effect graph - " +
                "highlight is a sibling HighlightElement, not a DrawBackdropElement property",
        )
    }

    /**
     * Positive control for the test above: proves [DrawBackdropDiagnostics] actually detects a
     * real, legitimate effect-graph recompute (a changing blur radius), so the previous test's
     * "no change" result means what it claims rather than the counter being silently dead.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun changingEffectsContentDoesRetriggerEffectRecomputation() = runDesktopComposeUiTest(width = 160, height = 120) {
        var isActive by mutableStateOf(false)

        setContent {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.Magenta)) {}
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(80.dp, 60.dp)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(8.dp) },
                            effects = { blur(if (isActive) 8f else 4f) },
                            onDrawSurface = { drawRect(Color.White.copy(alpha = 0.1f)) },
                        ),
                )
            }
        }
        waitForIdle()
        val afterInitialComposition = DrawBackdropDiagnostics.updateEffectsCallCount

        isActive = true
        waitForIdle()

        assertTrue(
            DrawBackdropDiagnostics.updateEffectsCallCount > afterInitialComposition,
            "a real blur-radius change must retrigger the effect graph - if this fails, " +
                "DrawBackdropDiagnostics is not observing real invalidations and the sibling " +
                "test's \"no change\" result cannot be trusted",
        )
    }

    /**
     * Reproduces the concern behind Phase 2 of the Haze-adoption plan ("BackdropEffectScope.() ->
     * Unit makes semantic equality... difficult"): [GlassStyle.toEffects] (core:designsystem)
     * returns a fresh lambda literal on every call, even for an unchanged [GlassStyle], because
     * it is a plain (non-`@Composable`) function whose body is `{ ... }` - so every call allocates
     * a new closure. An inline `effects = { blur(radius) }` literal written directly inside a
     * composable's body does NOT reproduce this: the Compose compiler plugin memoizes lambdas
     * created inside `@Composable` scopes that close over only stable values (a `Float` is
     * trivially stable), silently reusing the same instance across recompositions as long as the
     * captured value is unchanged - which would make a test written that way pass regardless of
     * any engine fix. Routing the lambda's creation through [blurEffectsFor], an ordinary
     * top-level function shaped exactly like [GlassStyle.toEffects], sidesteps that compiler
     * optimization (it only instruments lambda literals lexically inside composable bodies) and
     * genuinely allocates a fresh closure on every call, even for an identical [blurRadius].
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun freshEffectsLambdaWithUnchangedValueRetriggersEffectRecomputation() = runDesktopComposeUiTest(width = 160, height = 120) {
        var unrelatedTrigger by mutableStateOf(0)

        setContent {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.Magenta)) {}
                // Reads unrelatedTrigger in the same scope as drawBackdrop below, so bumping it
                // recomposes this Box and re-calls blurEffectsFor(4f) as GlassStyle.toEffects()
                // would - with no relation to unrelatedTrigger's own value.
                @Suppress("UNUSED_EXPRESSION")
                unrelatedTrigger
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(80.dp, 60.dp)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(8.dp) },
                            effects = blurEffectsFor(4f),
                            onDrawSurface = { drawRect(Color.White.copy(alpha = 0.1f)) },
                        ),
                )
            }
        }
        waitForIdle()
        val afterInitialComposition = DrawBackdropDiagnostics.updateEffectsCallCount

        unrelatedTrigger++
        waitForIdle()

        assertTrue(
            DrawBackdropDiagnostics.updateEffectsCallCount > afterInitialComposition,
            "expected a fresh effects lambda (identical semantic value, new instance) to still " +
                "retrigger the effect graph via DrawBackdropElement's reference-based equals() - " +
                "if this fails, the bug this test targets no longer exists",
        )
    }

    /**
     * Same setup as [freshEffectsLambdaWithUnchangedValueRetriggersEffectRecomputation], except
     * the [blurEffectsFor] call is wrapped in `remember(blurRadius) { ... }` at the call site -
     * the same fix `Modifier.glass` could apply around `style.toEffects()` without any engine
     * change. `remember` returns the *same* lambda instance across recompositions as long as its
     * key (here, the blur radius standing in for a full `GlassStyle`) is `==` to last time, so
     * `DrawBackdropElement.equals()`'s reference comparison sees no change.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rememberedEffectsLambdaWithUnchangedKeyDoesNotRetriggerEffectRecomputation() = runDesktopComposeUiTest(width = 160, height = 120) {
        var unrelatedTrigger by mutableStateOf(0)

        setContent {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.Magenta)) {}
                @Suppress("UNUSED_EXPRESSION")
                unrelatedTrigger
                val effects = remember(4f) { blurEffectsFor(4f) }
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(80.dp, 60.dp)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(8.dp) },
                            effects = effects,
                            onDrawSurface = { drawRect(Color.White.copy(alpha = 0.1f)) },
                        ),
                )
            }
        }
        waitForIdle()
        val afterInitialComposition = DrawBackdropDiagnostics.updateEffectsCallCount

        unrelatedTrigger++
        waitForIdle()

        assertEquals(
            afterInitialComposition,
            DrawBackdropDiagnostics.updateEffectsCallCount,
            "remember(key) around the effects lambda should have kept the same instance across " +
                "the unrelated recomposition, avoiding the spurious effect-graph recompute the " +
                "sibling test proves happens without it",
        )
    }
}

/** Plain (non-@Composable) function shaped like [GlassStyle.toEffects] - a real function call
 * returning a lambda, not a lambda literal written inline in a composable body - so the Compose
 * compiler's lambda-memoization for composable-scope literals does not apply to it. */
private fun blurEffectsFor(radius: Float): BackdropEffectScope.() -> Unit = { blur(radius) }
