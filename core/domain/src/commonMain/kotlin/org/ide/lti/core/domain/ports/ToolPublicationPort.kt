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
 * Port for registering product-built tool binaries with LtiRomServer daemon.
 */
public interface ToolPublicationPort {
    /**
     * Scans [binDir] on the remote host, writes daemon manifests for installed binaries matching [toolIds],
     * and refreshes the daemon's dynamic tool registry.
     */
    public suspend fun publish(binDir: String, toolIds: Set<String>): PublicationReport

    /**
     * Queries the daemon for all currently resolved and available tools.
     */
    public suspend fun resolvedTools(): ToolRegistryResult
}
