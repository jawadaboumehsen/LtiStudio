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

/**
 * Context provided by the ROM Studio shell to an active StageEditor.
 */
public data class StageEditorContext(
    val workspaceId: String,
    val selectedObjectId: StudioSubobjectId?,
    val onSelectedObjectChange: (StudioSubobjectId) -> Unit,
    val onSaveRequested: () -> Unit = {},
    val onValidationRequested: () -> Unit = {},
)
