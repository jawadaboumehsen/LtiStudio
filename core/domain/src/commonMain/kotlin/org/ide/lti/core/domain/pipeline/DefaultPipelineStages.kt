package org.ide.lti.core.domain.pipeline

import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage
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
        ModuleApplicationStage(),
        BuildFlashableZipStage(),
        GenerateOtaManifestStage()
    )

    public val allRequiredToolIds: Set<String> = all.flatMap { it.requiredToolIds }.toSet()
}
