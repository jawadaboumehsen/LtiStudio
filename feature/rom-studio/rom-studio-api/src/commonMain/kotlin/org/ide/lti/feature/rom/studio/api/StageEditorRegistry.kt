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

import org.ide.lti.core.model.run.StageId

/**
 * Registry interface for resolving stage editors.
 * Implementation lives inside rom-studio:shell.
 */
public interface StageEditorRegistry {
    public fun editorFor(stageId: StageId): StageEditor?
    public fun allEditors(): List<StageEditor>
}

public fun StageEditorRegistry.descriptorFor(stageId: StageId): StageDescriptor? =
    editorFor(stageId)?.descriptor

public fun StageEditorRegistry.allDescriptors(): List<StageDescriptor> =
    allEditors().map { it.descriptor }

public fun StageEditorRegistry.owningStage(subobjectId: StudioSubobjectId): StageDescriptor? =
    allDescriptors().firstOrNull { it.subobject(subobjectId) != null }
