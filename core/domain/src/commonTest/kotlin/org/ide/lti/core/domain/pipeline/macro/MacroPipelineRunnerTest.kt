/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.macro

import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.pipeline.macro.MacroRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacroPipelineRunnerTest {
    /** Six steps that succeed, standing in for a recipe made of real actions. */
    private fun sixStepRecipe() = MacroRecipe(
        id = "recipe.test",
        name = "Test recipe",
        description = "Six steps that succeed",
        steps = (1..6).map { MacroStep("s$it", "Step $it", "Succeeds", { Result.success(Unit) }) },
    )

    @Test
    fun testRecipeRunsEveryStepToSuccess() = runTest {
        val recipe = sixStepRecipe()
        val runner = MacroPipelineRunner(recipe)

        val success = runner.run()
        assertTrue(success)

        val completedRecipe = runner.currentRecipe.value
        assertEquals(6, completedRecipe.totalSteps)
        assertEquals(6, completedRecipe.passedSteps)
        assertTrue(completedRecipe.isComplete)
        assertFalse(completedRecipe.hasFailed)
    }

    @Test
    fun testPipelineFailureHaltsAndSkipsSubsequentSteps() = runTest {
        val steps = listOf(
            MacroStep("s1", "Step 1", "Success", { Result.success(Unit) }),
            MacroStep("s2", "Step 2", "Fails", { Result.failure(IllegalStateException("Flash write error")) }),
            MacroStep("s3", "Step 3", "Should be skipped", { Result.success(Unit) }),
        )

        val recipe = MacroRecipe("r1", "Test", "Test", steps)
        val runner = MacroPipelineRunner(recipe)

        val success = runner.run()
        assertFalse(success)

        val state = runner.currentRecipe.value
        assertEquals(MacroStepStatus.PASSED, state.steps[0].status)
        assertEquals(MacroStepStatus.FAILED, state.steps[1].status)
        assertEquals("Flash write error", state.steps[1].errorMessage)
        assertEquals(MacroStepStatus.SKIPPED, state.steps[2].status)
        assertTrue(state.hasFailed)

        // Test retry
        runner.retryStep("s2")
        assertEquals(MacroStepStatus.PENDING, runner.currentRecipe.value.steps[1].status)
    }

    @Test
    fun testPipelineCancellation() = runTest {
        val steps = listOf(
            MacroStep("s1", "Step 1", "Desc", { Result.success(Unit) }),
            MacroStep("s2", "Step 2", "Desc", { Result.success(Unit) }),
        )

        val runner = MacroPipelineRunner(MacroRecipe("r2", "Cancel", "Cancel", steps))
        runner.cancel()
        runner.run()

        val state = runner.currentRecipe.value
        assertEquals(MacroStepStatus.SKIPPED, state.steps[0].status)
        assertEquals(MacroStepStatus.SKIPPED, state.steps[1].status)
    }
}
