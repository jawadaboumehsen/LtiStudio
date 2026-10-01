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
public object PipelineStageSequence {
    public val ORDER: List<StageId> = listOf(
        StageId.FIRMWARE_ACQUISITION,
        StageId.FIRMWARE_EXTRACTION,
        StageId.WORK_TREE_ASSEMBLY,
        StageId.DEBLOAT,
        StageId.MODULE_APPLICATION,
        StageId.BUILD_FLASHABLE_ZIP,
        StageId.GENERATE_OTA_MANIFEST,
        StageId.PUBLISH_RELEASE,
    )

    public fun orderOf(stageId: StageId): Int {
        val index = ORDER.indexOf(stageId)
        require(index >= 0) { "Stage $stageId is not part of canonical pipeline sequence" }
        return index + 1
    }
}
