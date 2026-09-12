/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.desktop.menu.dispatcher

import org.ide.lti.desktop.menu.model.StudioAction

/**
 * Interface Segregation & Dependency Inversion compliant abstraction for dispatching IDE actions.
 * UI components depend only on this dispatcher without coupling to concrete window, panel, or ViewModel implementations.
 */
fun interface StudioActionDispatcher {
    /**
     * Dispatches the specified [StudioAction] to its registered handler.
     */
    fun dispatch(action: StudioAction)
}

/**
 * Thread-safe default implementation of [StudioActionDispatcher] allowing runtime registration
 * and unregistration of action handlers (complying with Open/Closed Principle).
 */
class DefaultStudioActionDispatcher(
    initialHandlers: Map<StudioAction, () -> Unit> = emptyMap(),
) : StudioActionDispatcher {

    private val handlers: MutableMap<StudioAction, () -> Unit> =
        java.util.concurrent.ConcurrentHashMap(initialHandlers)

    /**
     * Registers a callback [handler] for the given [action].
     */
    fun register(action: StudioAction, handler: () -> Unit) {
        handlers[action] = handler
    }

    /**
     * Unregisters any existing handler for the given [action].
     */
    fun unregister(action: StudioAction) {
        handlers.remove(action)
    }

    override fun dispatch(action: StudioAction) {
        handlers[action]?.invoke()
    }
}
