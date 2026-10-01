/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.common.timing

import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Nanosecond-precision startup timing logger without external framework overhead.
 */
object StartupTiming {
    val processStart: TimeMark = TimeSource.Monotonic.markNow()

    fun log(event: String) {
        val elapsedMs = processStart.elapsedNow().inWholeMilliseconds
        println("[STARTUP_TIMING] $event at +${elapsedMs}ms")
    }
}
