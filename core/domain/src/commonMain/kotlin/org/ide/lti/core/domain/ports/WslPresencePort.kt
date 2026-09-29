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

/**
 * Port for querying WSL presence and installed distributions from the host platform.
 */
public interface WslPresencePort {
    /** Returns true if WSL is installed on the host. */
    public suspend fun isWslInstalled(): Boolean

    /** Returns true if at least one WSL distribution is registered. */
    public suspend fun hasDistro(): Boolean

    /** Returns the default WSL distribution name, if any. */
    public suspend fun getDefaultDistro(): String?
}
