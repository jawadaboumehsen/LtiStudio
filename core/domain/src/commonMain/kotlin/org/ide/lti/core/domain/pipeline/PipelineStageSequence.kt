/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline

import org.ide.lti.core.model.run.StageId

/**
 * Single canonical definition of the 8-stage pipeline sequence.
 * Directly referenced by [PipelineDefinitionRegistry] and the ROM Studio shell.
 */
object PipelineStageSequence {
    val ORDER: List<StageId> = listOf(
        StageId.FIRMWARE_ACQUISITION,
        StageId.FIRMWARE_EXTRACTION,
        StageId.WORK_TREE_ASSEMBLY,
        StageId.DEBLOAT,
        StageId.MODULE_APPLICATION,
        StageId.BUILD_FLASHABLE_ZIP,
        StageId.GENERATE_OTA_MANIFEST,
        StageId.PUBLISH_RELEASE,
    )

    /** The stage's name as the app shows it. */
    fun label(stage: StageId): String = when (stage) {
        StageId.FIRMWARE_ACQUISITION -> "Acquire firmware"
        StageId.FIRMWARE_EXTRACTION -> "Extract"
        StageId.WORK_TREE_ASSEMBLY -> "Assemble"
        StageId.DEBLOAT -> "Debloat"
        StageId.MODULE_APPLICATION -> "Patches & mods"
        StageId.BUILD_FLASHABLE_ZIP -> "Build & sign"
        StageId.GENERATE_OTA_MANIFEST -> "Release metadata"
        StageId.PUBLISH_RELEASE -> "Publish"
    }

    fun orderOf(stageId: StageId): Int {
        val index = ORDER.indexOf(stageId)
        require(index >= 0) { "Stage $stageId is not part of canonical pipeline sequence" }
        return index + 1
    }
}
