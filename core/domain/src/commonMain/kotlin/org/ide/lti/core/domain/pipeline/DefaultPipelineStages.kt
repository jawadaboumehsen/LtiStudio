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

import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage
import org.ide.lti.core.domain.pipeline.stages.DebloatStage
import org.ide.lti.core.domain.pipeline.stages.FirmwareAcquisitionStage
import org.ide.lti.core.domain.pipeline.stages.FirmwareExtractionStage
import org.ide.lti.core.domain.pipeline.stages.GenerateOtaManifestStage
import org.ide.lti.core.domain.pipeline.stages.ModuleApplicationStage
import org.ide.lti.core.domain.pipeline.stages.WorkTreeAssemblyStage

public object DefaultPipelineStages {
    public val all: List<StageDefinition> = listOf(
        FirmwareAcquisitionStage(),
        FirmwareExtractionStage(),
        WorkTreeAssemblyStage(),
        DebloatStage(),
        ModuleApplicationStage(),
        BuildFlashableZipStage(),
        GenerateOtaManifestStage(),
    )

    public val allRequiredToolIds: Set<String> = all.flatMap { it.requiredToolIds }.toSet()
}
