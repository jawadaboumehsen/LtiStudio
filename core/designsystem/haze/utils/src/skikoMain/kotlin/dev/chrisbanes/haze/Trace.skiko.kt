/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package dev.chrisbanes.haze

/** Runs [block] inside a Compose trace section named [sectionName]. */
@InternalHazeApi
public actual inline fun <R> trace(sectionName: String, block: () -> R): R {
    return androidx.compose.ui.util.trace(sectionName, block)
}

/** Runs [block]; Skiko does not currently emit an asynchronous platform trace section. */
@InternalHazeApi
public actual suspend inline fun <R> traceAsync(
    sectionName: String,
    cookie: Int,
    crossinline block: suspend () -> R,
): R = block()
