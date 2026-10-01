/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.connection

import kotlinx.serialization.Serializable

@Serializable
data class Connection(
    val id: String,
    val name: String,
    val type: ConnectionType,
    val host: String,
    val port: Int? = null,
    val username: String? = null,
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
)

@Serializable
enum class ConnectionType {
    SSH,
    WSL,
    DOCKER,
    CODECANVAS,
}

@Serializable
enum class ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    CONNECTING,
    ERROR,
}
