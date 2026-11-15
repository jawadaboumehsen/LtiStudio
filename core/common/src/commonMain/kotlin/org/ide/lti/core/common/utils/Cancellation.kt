/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.common.utils

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] for coroutine code: a [CancellationException] is thrown on, never returned as a failure. A broad
 * catch around a suspending call otherwise turns "this job was cancelled" into an ordinary error and the job goes on.
 * Inline, so the block may suspend.
 */
inline fun <R> runCatchingCancellable(block: () -> R): Result<R> =
    runCatching(block).onFailure { if (it is CancellationException) throw it }
