/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.connection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ide.lti.core.model.connection.Connection
import org.ide.lti.core.model.connection.ConnectionStatus

/**
 * Manages remote connections (SSH, WSL, Docker, etc.)
 */
class ConnectionManager {
    private val _connections = MutableStateFlow<List<Connection>>(emptyList())
    val connections: StateFlow<List<Connection>> = _connections.asStateFlow()

    private val _activeConnection = MutableStateFlow<Connection?>(null)
    val activeConnection: StateFlow<Connection?> = _activeConnection.asStateFlow()

    suspend fun connect(connection: Connection): Result<Unit> {
        // TODO: Implement actual connection logic
        _activeConnection.value = connection.copy(status = ConnectionStatus.CONNECTED)
        return Result.success(Unit)
    }

    suspend fun disconnect() {
        _activeConnection.value = null
    }

    fun addConnection(connection: Connection) {
        _connections.value = _connections.value + connection
    }

    fun removeConnection(id: String) {
        _connections.value = _connections.value.filterNot { it.id == id }
    }
}
