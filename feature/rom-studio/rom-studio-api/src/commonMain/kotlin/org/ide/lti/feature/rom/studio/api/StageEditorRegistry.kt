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
interface StageEditorRegistry {
    fun editorFor(stageId: StageId): StageEditor?
    fun allEditors(): List<StageEditor>
}

fun StageEditorRegistry.descriptorFor(stageId: StageId): StageDescriptor? =
    editorFor(stageId)?.descriptor

fun StageEditorRegistry.allDescriptors(): List<StageDescriptor> =
    allEditors().map { it.descriptor }

fun StageEditorRegistry.owningStage(subobjectId: StudioSubobjectId): StageDescriptor? =
    allDescriptors().firstOrNull { it.subobject(subobjectId) != null }
