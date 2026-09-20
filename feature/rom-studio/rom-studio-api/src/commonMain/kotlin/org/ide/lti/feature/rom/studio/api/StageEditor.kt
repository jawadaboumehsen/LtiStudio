/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.ide.lti.core.model.run.StageId

/**
 * Public integration contract implemented by each independent pipeline stage module.
 */
public interface StageEditor {
    public val stageId: StageId
    public val descriptor: StageDescriptor

    @Composable
    public fun Content(
        context: StageEditorContext,
        modifier: Modifier,
    )
}
