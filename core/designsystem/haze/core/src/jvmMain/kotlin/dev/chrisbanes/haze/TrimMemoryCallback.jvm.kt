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
import kotlinx.coroutines.DisposableHandle

internal actual fun registerTrimMemoryCallback(
    context: PlatformContext,
    lifecycle: Lifecycle?,
    callback: (TrimMemoryLevel) -> Unit,
): DisposableHandle = registerLifecycleTrimMemoryCallback(lifecycle, callback)
