/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.run

import kotlinx.serialization.Serializable

/**
 * Execution state of a product-owned ROM pipeline build run.
 *
 * State transitions:
 * QUEUED -> RUNNING -> (PAUSED_WAITING_FOR_APP <-> RUNNING) -> (CANCELLING ->) CANCELLED | SUCCEEDED | FAILED | INTERRUPTED
 */
@Serializable
public enum class RunState {
    QUEUED,
    RUNNING,
    PAUSED_WAITING_FOR_APP,
    CANCELLING,
    CANCELLED,
    SUCCEEDED,
    FAILED,
    INTERRUPTED,
    ;

    public val isTerminal: Boolean
        get() = this == CANCELLED || this == SUCCEEDED || this == FAILED || this == INTERRUPTED

    public val isActive: Boolean
        get() = !isTerminal
}
