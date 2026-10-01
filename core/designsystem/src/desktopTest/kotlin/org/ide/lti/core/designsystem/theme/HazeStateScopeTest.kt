/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.LocalGlassAccessibilitySettings
import dev.chrisbanes.haze.hazeSource
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HazeStateScopeTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun nestedSubtreeReusesParentHazeState() = runDesktopComposeUiTest {
        var rootState: HazeState? = null
        var nestedState: HazeState? = null

        setContent {
            LtiTheme(appTheme = AppTheme.Blue) {
                rootState = GlassTheme.hazeState
                // Nested LtiTheme simulating SettingsScreen or nested feature subtree
                LtiTheme(appTheme = AppTheme.Dark) {
                    nestedState = GlassTheme.hazeState
                    Text("Nested Subtree Content")
                }
            }
        }

        onNodeWithText("Nested Subtree Content").assertExists()
        assertNotNull(rootState, "Root HazeState must not be null")
        assertNotNull(nestedState, "Nested HazeState must not be null")
        assertSame(
            rootState,
            nestedState,
            "Nested LtiTheme must inherit existing LocalHazeState from the root composition",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun standaloneRootsCreateDistinctHazeStates() = runDesktopComposeUiTest {
        var stateA: HazeState? = null
        var stateB: HazeState? = null

        setContent {
            Box {
                LtiTheme(appTheme = AppTheme.Blue) {
                    stateA = GlassTheme.hazeState
                }
                LtiTheme(appTheme = AppTheme.Blue) {
                    stateB = GlassTheme.hazeState
                }
            }
        }

        assertNotNull(stateA, "First root HazeState must not be null")
        assertNotNull(stateB, "Second root HazeState must not be null")
        assertNotSame(
            stateA,
            stateB,
            "Independent root LtiThemes must each allocate an isolated HazeState",
        )
    }

    /**
     * Verifies that a [GlassDialog] launched from within an [LtiTheme] subtree shares the same
     * [HazeState] instance as the root composition.
     *
     * Note: Because [GlassDialog] wraps Compose's [Dialog], its content lambda composes in a
     * separate OS-level popup window. On Desktop, cross-window pixel capture is not supported by
     * the test harness (runDesktopComposeUiTest.onRoot reaches only the root window's semantics
     * tree), so pixel rendering cannot be asserted here. This test verifies the state-sharing
     * contract ensuring that the dialog composition inherits the root [HazeState].
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun crossWindowDialogBoundaryContractMaintainsSharedHazeState() = runDesktopComposeUiTest {
        var rootState: HazeState? = null
        var dialogState: HazeState? = null

        setContent {
            LtiTheme(appTheme = AppTheme.Blue) {
                rootState = GlassTheme.hazeState
                Box(Modifier.fillMaxSize()) {
                    // GlassDialog content lambda runs during setContent composition —
                    // dialogState is populated synchronously before waitForIdle returns.
                    GlassDialog(
                        onDismissRequest = {},
                        confirmButton = { Text("OK") },
                        title = "Dialog Boundary",
                    ) {
                        dialogState = GlassTheme.hazeState
                    }
                }
            }
        }

        waitForIdle()

        // State-sharing contract: dialog inherits the ambient HazeState from its LtiTheme ancestor.
        assertNotNull(rootState, "Root HazeState must be non-null after composition")
        assertNotNull(dialogState, "Dialog HazeState must be non-null — GlassDialog must inherit LocalHazeState")
        assertSame(
            rootState,
            dialogState,
            "GlassDialog within an LtiTheme subtree must share the root HazeState instance " +
                "so that its glass surface can sample the registered backdrop sources",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun hazeInputSourcesEvaluatesCrossLayerSourceDirectly() = runDesktopComposeUiTest {
        var state: HazeState? = null

        setContent {
            LtiTheme(appTheme = AppTheme.Blue) {
                state = GlassTheme.hazeState
                val currentHazeState = GlassTheme.hazeState

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .hazeSource(currentHazeState),
                )

                // HazeInput.Sources directly samples the shared state's registered sources
                val sourcesInput = HazeInput.Sources(state = currentHazeState)
                assertSame(currentHazeState, sourcesInput.state)

                // HazeInput.Backdrop wraps sources as fallback when native window sampling is unavailable
                val backdropInput = HazeInput.Backdrop(state = currentHazeState)
                assertNotNull(backdropInput.fallback)
                assertSame(currentHazeState, backdropInput.fallback?.state)
            }
        }

        assertNotNull(state, "HazeState must be initialized")
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class)
    @Test
    fun ltiThemeProvidesAccessibilitySettingsAndReducedMotionPolicy() = runDesktopComposeUiTest {
        var reduceTransparencyDefault = false
        var motionPolicyDefault: GlassReducedMotionPolicy? = null
        var reduceTransparencyCustom = false
        var motionPolicyCustom: GlassReducedMotionPolicy? = null

        setContent {
            LtiTheme(
                appTheme = AppTheme.Blue,
                effectsEnabled = true,
                reducedMotion = false,
            ) {
                reduceTransparencyDefault = LocalGlassAccessibilitySettings.current.reduceTransparency
                motionPolicyDefault = GlassTheme.reducedMotionPolicy

                LtiTheme(
                    appTheme = AppTheme.Dark,
                    effectsEnabled = false,
                    reducedMotion = true,
                ) {
                    reduceTransparencyCustom = LocalGlassAccessibilitySettings.current.reduceTransparency
                    motionPolicyCustom = GlassTheme.reducedMotionPolicy
                }
            }
        }

        assertFalse(reduceTransparencyDefault, "effectsEnabled = true must not reduce transparency")
        assertEquals(
            GlassReducedMotionPolicy.System,
            motionPolicyDefault,
            "reducedMotion = false must use System policy",
        )
        assertTrue(reduceTransparencyCustom, "effectsEnabled = false must set reduceTransparency = true")
        assertEquals(
            GlassReducedMotionPolicy.Reduced,
            motionPolicyCustom,
            "reducedMotion = true must use Reduced policy",
        )
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class)
    @Test
    fun nestedLtiThemeWithOmittedParametersInheritsParentAccessibilitySettings() = runDesktopComposeUiTest {
        var rootTransparency = false
        var rootMotionPolicy: GlassReducedMotionPolicy? = null
        var nestedTransparency = false
        var nestedMotionPolicy: GlassReducedMotionPolicy? = null
        var standaloneTransparency = true
        var standaloneMotionPolicy: GlassReducedMotionPolicy? = null

        setContent {
            // Root sets non-default accessibility settings
            LtiTheme(
                appTheme = AppTheme.Blue,
                effectsEnabled = false,
                reducedMotion = true,
            ) {
                rootTransparency = LocalGlassAccessibilitySettings.current.reduceTransparency
                rootMotionPolicy = GlassTheme.reducedMotionPolicy

                // Nested theme omits effectsEnabled and reducedMotion parameters
                LtiTheme(appTheme = AppTheme.Dark) {
                    nestedTransparency = LocalGlassAccessibilitySettings.current.reduceTransparency
                    nestedMotionPolicy = GlassTheme.reducedMotionPolicy
                }
            }

            // Standalone root with omitted parameters defaults to effects enabled, system motion
            LtiTheme(appTheme = AppTheme.Light) {
                standaloneTransparency = LocalGlassAccessibilitySettings.current.reduceTransparency
                standaloneMotionPolicy = GlassTheme.reducedMotionPolicy
            }
        }

        assertTrue(rootTransparency, "Root with effectsEnabled = false must reduce transparency")
        assertEquals(
            GlassReducedMotionPolicy.Reduced,
            rootMotionPolicy,
            "Root with reducedMotion = true must use Reduced policy",
        )

        // Crucial contract: omitted parameters in nested theme MUST inherit from enclosing parent
        assertTrue(
            nestedTransparency,
            "Nested LtiTheme with omitted parameters must inherit reduceTransparency = true from parent",
        )
        assertEquals(
            GlassReducedMotionPolicy.Reduced,
            nestedMotionPolicy,
            "Nested LtiTheme with omitted parameters must inherit Reduced motion policy from parent",
        )

        // Standalone defaults
        assertFalse(
            standaloneTransparency,
            "Standalone LtiTheme with omitted parameters must default to reduceTransparency = false",
        )
        assertEquals(
            GlassReducedMotionPolicy.System,
            standaloneMotionPolicy,
            "Standalone LtiTheme with omitted parameters must default to System motion policy",
        )
    }
}
