/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.tools

sealed interface ShutdownReport {
    data object Stopped : ShutdownReport

    data class Unconfirmed(val executionIds: List<String>) : ShutdownReport
}

interface AppShutdownPort {
    /** Set before the build service is told to stop, so what that stop breaks is not taken for a failure. */
    val closing: Boolean get() = false

    suspend fun shutdown(): ShutdownReport

    suspend fun forceShutdown() {}

    /** The workspaces with a build or a job (a publish, say) that closing the app would stop. */
    suspend fun activeWork(): List<String> = emptyList()
}
