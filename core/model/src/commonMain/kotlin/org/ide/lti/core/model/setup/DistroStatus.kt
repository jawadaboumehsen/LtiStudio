/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.setup

sealed interface DistroStatus {
    data class WslUnavailable(val detail: String, val accessDenied: Boolean = false) : DistroStatus
    data object NoDistro : DistroStatus
    data class StartupFailed(val distro: String, val detail: String) : DistroStatus
    data class Unsupported(val distro: String, val reason: String) : DistroStatus
    data class NoUsableUser(val distro: String, val reason: String) : DistroStatus
    data class Usable(val environment: SetupEnvironment) : DistroStatus
}

/** The status nearest to usable, so a stray non-Ubuntu distro never hides the real problem. */
fun List<DistroStatus>.closestToUsable(): DistroStatus? = minByOrNull {
    when (it) {
        is DistroStatus.Usable -> 0
        is DistroStatus.NoUsableUser -> 1
        is DistroStatus.StartupFailed -> 2
        is DistroStatus.Unsupported -> 3
        is DistroStatus.NoDistro -> 4
        is DistroStatus.WslUnavailable -> 5
    }
}

/** The distro this status is about; null when there is none (WSL missing or no distro installed). */
val DistroStatus.distroName: String?
    get() = when (this) {
        is DistroStatus.Usable -> environment.distro
        is DistroStatus.Unsupported -> distro
        is DistroStatus.StartupFailed -> distro
        is DistroStatus.NoUsableUser -> distro
        is DistroStatus.NoDistro, is DistroStatus.WslUnavailable -> null
    }
