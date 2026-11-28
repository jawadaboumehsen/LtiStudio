/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui.pipeline

import org.ide.lti.core.domain.pipeline.macro.MacroRecipe
import org.ide.lti.core.domain.pipeline.macro.MacroStep
import org.ide.lti.core.domain.pipeline.macro.MacroStepStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MacroPipelineStudioSheetTest {
    /** Six steps that succeed, standing in for a recipe made of real actions. */
    private fun sixStepRecipe() = MacroRecipe(
        id = "recipe.test",
        name = "Test recipe",
        description = "Six steps that succeed",
        steps = (1..6).map { MacroStep("s$it", "Step $it", "Succeeds", { Result.success(Unit) }) },
    )

    @Test
    fun testRecipeProgressCalculations() {
        val recipe = sixStepRecipe()
        val updatedSteps = recipe.steps.mapIndexed { index, step ->
            if (index < 3) step.copy(status = MacroStepStatus.PASSED) else step
        }
        val progressingRecipe = recipe.copy(steps = updatedSteps)

        assertEquals(3, progressingRecipe.passedSteps)
        assertEquals(6, progressingRecipe.totalSteps)
        assertFalse(progressingRecipe.isComplete)
    }
}
