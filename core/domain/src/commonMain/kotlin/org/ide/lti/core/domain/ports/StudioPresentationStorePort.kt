/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.ports

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.ide.lti.core.model.studio.StudioPresentation

public interface StudioPresentationStorePort {
    val presentations: Flow<Map<String, StudioPresentation>>
    val currentPresentations: Map<String, StudioPresentation>
    fun presentation(workspaceId: String): Flow<StudioPresentation?>
    suspend fun get(workspaceId: String): StudioPresentation?
    suspend fun put(presentation: StudioPresentation)
    suspend fun clear(workspaceId: String)
}

public class InMemoryStudioPresentationStore(initial: Map<String, StudioPresentation> = emptyMap()) :
    StudioPresentationStorePort {
    private val _presentations = MutableStateFlow(initial)
    override val presentations: Flow<Map<String, StudioPresentation>> = _presentations.asStateFlow()
    override val currentPresentations: Map<String, StudioPresentation> get() = _presentations.value
    override fun presentation(workspaceId: String): Flow<StudioPresentation?> = _presentations.map { it[workspaceId] }
    override suspend fun get(workspaceId: String): StudioPresentation? = _presentations.value[workspaceId]
    override suspend fun put(presentation: StudioPresentation) {
        _presentations.value = _presentations.value + (presentation.workspaceId to presentation)
    }
    override suspend fun clear(workspaceId: String) {
        _presentations.value = _presentations.value - workspaceId
    }
}
