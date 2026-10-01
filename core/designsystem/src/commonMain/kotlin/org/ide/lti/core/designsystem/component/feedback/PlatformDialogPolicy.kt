/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.feedback

/**
 * Platform capability contract for dialog rendering.
 *
 * Controls whether the host platform's dialog implementation supports cross-window
 * blur sampling from the parent window's Haze canvas. On Desktop Compose, native platform
 * dialogs run in independent OS windows with separate Skia graphics contexts, requiring
 * an honest solid container fallback.
 */
expect object PlatformDialogPolicy {
    val supportsCrossWindowBlur: Boolean
}
