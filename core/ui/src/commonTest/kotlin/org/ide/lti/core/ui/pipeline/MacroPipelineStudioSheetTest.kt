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

import org.ide.lti.core.domain.pipeline.macro.MacroPipelineRunner
import org.ide.lti.core.domain.pipeline.macro.MacroStepStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacroPipelineStudioSheetTest {

    @Test
    fun testDefaultFlashRecipeStructure() {
        val recipe = MacroPipelineRunner.createDefaultFlashRecipe()
        assertEquals(6, recipe.totalSteps)
        assertEquals(0, recipe.passedSteps)
        assertFalse(recipe.isComplete)
        assertFalse(recipe.hasFailed)

        val titles = recipe.steps.map { it.title }
        assertTrue(titles.contains("Extract Payload.bin"))
        assertTrue(titles.contains("Inject KernelSU"))
        assertTrue(titles.contains("Repack EROFS Super"))
        assertTrue(titles.contains("Patch Vbmeta"))
        assertTrue(titles.contains("Flash Fastbootd"))
        assertTrue(titles.contains("Reboot Device"))
    }

    @Test
    fun testRecipeProgressCalculations() {
        val recipe = MacroPipelineRunner.createDefaultFlashRecipe()
        val updatedSteps = recipe.steps.mapIndexed { index, step ->
            if (index < 3) step.copy(status = MacroStepStatus.PASSED) else step
        }
        val progressingRecipe = recipe.copy(steps = updatedSteps)

        assertEquals(3, progressingRecipe.passedSteps)
        assertEquals(6, progressingRecipe.totalSteps)
        assertFalse(progressingRecipe.isComplete)
    }
}
