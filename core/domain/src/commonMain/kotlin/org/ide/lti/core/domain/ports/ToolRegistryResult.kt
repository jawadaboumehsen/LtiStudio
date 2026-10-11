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

public sealed interface ToolRegistryResult {
    public data class Tools(val list: List<ToolStatus>) : ToolRegistryResult

    /** The service answered, but not with a tool list. */
    public data class ServiceError(val reason: String) : ToolRegistryResult

    /** No answer: the connection failed. */
    public data class Unreachable(val reason: String) : ToolRegistryResult
}
