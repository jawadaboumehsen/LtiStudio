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

/** Runs [block] inside a synchronous platform trace section named [sectionName]. */
@InternalHazeApi
public expect inline fun <R> trace(sectionName: String, block: () -> R): R

/** Runs [block] inside an asynchronous platform trace section identified by [cookie]. */
@InternalHazeApi
public expect suspend inline fun <R> traceAsync(
    sectionName: String,
    cookie: Int,
    crossinline block: suspend () -> R,
): R
