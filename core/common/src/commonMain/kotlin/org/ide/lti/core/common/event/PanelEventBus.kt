/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.common.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event bus for inter-panel communication.
 * Panels can publish and subscribe to events.
 */
class PanelEventBus {
    private val _events = MutableSharedFlow<PanelEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )

    val events: SharedFlow<PanelEvent> = _events.asSharedFlow()

    /**
     * Publish an event to all subscribers.
     */
    suspend fun publish(event: PanelEvent) {
        _events.emit(event)
    }

    /**
     * Publish an event without suspending (fire and forget).
     */
    fun publishAsync(event: PanelEvent) {
        _events.tryEmit(event)
    }
}
