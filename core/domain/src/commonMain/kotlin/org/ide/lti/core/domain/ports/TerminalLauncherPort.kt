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
 * Port for launching a host interactive terminal attached to a specific WSL environment.
 */
public interface TerminalLauncherPort {
    /**
     * Launches an external interactive terminal session inside the specified [distro].
     */
    public fun launchTerminal(distro: String): Result<Unit>
}
