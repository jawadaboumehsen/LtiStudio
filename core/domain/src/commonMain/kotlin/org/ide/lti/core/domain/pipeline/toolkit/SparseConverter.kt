/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.model.run.StepCommand

/**
 * Adapter for Android sparse-image conversion (sparse to raw, raw to sparse) and chunk splitting (US2, FR-010).
 */
public object SparseConverter {

    /**
     * Generates a [StepCommand] converting an Android sparse image to a raw disk image using `simg2img`.
     */
    public fun sparseToRawCommand(sparseImage: String, rawImage: String, workingDir: String): StepCommand = stepCommand(
        toolId = "simg2img",
        args = listOf(sparseImage, rawImage),
        workingDir = workingDir,
    )
}
