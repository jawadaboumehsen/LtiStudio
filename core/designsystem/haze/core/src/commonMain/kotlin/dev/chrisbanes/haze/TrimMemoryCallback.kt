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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.DisposableHandle

internal expect fun registerTrimMemoryCallback(
    context: PlatformContext,
    lifecycle: Lifecycle?,
    callback: (TrimMemoryLevel) -> Unit,
): DisposableHandle

internal fun registerLifecycleTrimMemoryCallback(
    lifecycle: Lifecycle?,
    callback: (TrimMemoryLevel) -> Unit,
): DisposableHandle {
    if (lifecycle == null) return DisposableHandle {}

    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) {
            callback(TrimMemoryLevel.MODERATE)
        }
    }
    lifecycle.addObserver(observer)
    return DisposableHandle { lifecycle.removeObserver(observer) }
}
