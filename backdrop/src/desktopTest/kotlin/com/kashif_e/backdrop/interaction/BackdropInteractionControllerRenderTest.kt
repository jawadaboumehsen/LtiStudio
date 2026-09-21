package com.kashif_e.backdrop.interaction

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.DrawBackdropDiagnostics
import com.kashif_e.backdrop.OpticalSizeValue
import com.kashif_e.backdrop.ResolvedGlassEffect
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves [BackdropInteractionController] is a real, working port of Haze's
 * `GlassInteractionController`: a declared hover response actually retriggers the rendered
 * optics (observed through [DrawBackdropDiagnostics], the same counter
 * `DrawBackdropRecompositionTest` uses) when driven purely through a Compose
 * `InteractionSource` - with the composition-supplied [ResolvedGlassEffect] held completely
 * unchanged across the whole test, proving the retrigger comes from the interaction controller's
 * own `invalidateDraw` path, not [com.kashif_e.backdrop.GlassEffectDirtyFields].
 */
class BackdropInteractionControllerRenderTest {

    @AfterTest
    fun resetDiagnostics() {
        DrawBackdropDiagnostics.updateEffectsCallCount = 0
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun hoverResponseRetriggersEffectRecomputationThroughInteractionSourceAlone() =
        runDesktopComposeUiTest(width = 160, height = 120) {
            var hovered by mutableStateOf(false)

            setContent {
                val backdrop = rememberLayerBackdrop()
                val interactionSource = remember { MutableInteractionSource() }
                val hoverInteraction = remember { HoverInteraction.Enter() }

                LaunchedEffect(hovered) {
                    if (hovered) {
                        interactionSource.emit(hoverInteraction)
                    } else {
                        interactionSource.emit(HoverInteraction.Exit(hoverInteraction))
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.Magenta)) {}
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(80.dp, 60.dp)
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedCornerShape(8.dp) },
                                resolvedEffect = ResolvedGlassEffect(blur = OpticalSizeValue.Fixed(Dp(4f))),
                                interaction = backdropInteraction(hovered = { blurMultiplier(1.5f) }),
                                interactionSource = interactionSource,
                            ),
                    )
                }
            }
            waitForIdle()
            val afterInitialComposition = DrawBackdropDiagnostics.updateEffectsCallCount

            hovered = true
            waitForIdle()

            assertTrue(
                DrawBackdropDiagnostics.updateEffectsCallCount > afterInitialComposition,
                "a hover response driven purely through InteractionSource must retrigger the " +
                    "effect graph via BackdropInteractionController's own invalidateDraw path - " +
                    "if this fails, the controller isn't wired into DrawBackdropNode.draw()",
            )
        }

    /**
     * Positive-decoupling control: an *undeclared* interaction state (press, with no `pressed {}`
     * response declared) must not retrigger anything - proves [BackdropInteractionController]
     * doesn't spuriously invalidate when nothing it owns actually changed.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun undeclaredInteractionStateDoesNotRetriggerEffectRecomputation() =
        runDesktopComposeUiTest(width = 160, height = 120) {
            var pressed by mutableStateOf(false)

            setContent {
                val backdrop = rememberLayerBackdrop()
                val interactionSource = remember { MutableInteractionSource() }
                val pressInteraction = remember { androidx.compose.foundation.interaction.PressInteraction.Press(androidx.compose.ui.geometry.Offset.Zero) }

                LaunchedEffect(pressed) {
                    if (pressed) {
                        interactionSource.emit(pressInteraction)
                    } else {
                        interactionSource.emit(androidx.compose.foundation.interaction.PressInteraction.Release(pressInteraction))
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.Magenta)) {}
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(80.dp, 60.dp)
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedCornerShape(8.dp) },
                                resolvedEffect = ResolvedGlassEffect(blur = OpticalSizeValue.Fixed(Dp(4f))),
                                interaction = backdropInteraction(hovered = { blurMultiplier(1.5f) }),
                                interactionSource = interactionSource,
                            ),
                    )
                }
            }
            waitForIdle()
            val afterInitialComposition = DrawBackdropDiagnostics.updateEffectsCallCount

            pressed = true
            waitForIdle()

            assertTrue(
                DrawBackdropDiagnostics.updateEffectsCallCount == afterInitialComposition,
                "pressing with no declared `pressed {}` response must not retrigger the effect " +
                    "graph - got ${DrawBackdropDiagnostics.updateEffectsCallCount}, " +
                    "expected $afterInitialComposition",
            )
        }
}
