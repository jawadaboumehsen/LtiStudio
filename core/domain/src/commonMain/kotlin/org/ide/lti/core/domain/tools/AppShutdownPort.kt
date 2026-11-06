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

public sealed interface ShutdownReport {
    public data object Stopped : ShutdownReport

    public data class Unconfirmed(val executionIds: List<String>) : ShutdownReport
}

public interface AppShutdownPort {
    /** Set before the build service is told to stop, so what that stop breaks is not taken for a failure. */
    public val closing: Boolean get() = false

    public suspend fun shutdown(): ShutdownReport

    public suspend fun forceShutdown() {}

    /** The names of the workspaces with a build queued or running, which closing the app would stop. */
    public suspend fun activeBuilds(): List<String> = emptyList()
}
