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

/** Marks public declarations reserved for Haze's internal module-to-module implementation. */
@RequiresOptIn(
    message = "This is an internal Haze API and should not be used outside of the library. " +
        "It may be changed or removed without notice.",
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
@Retention(AnnotationRetention.BINARY)
public annotation class InternalHazeApi
