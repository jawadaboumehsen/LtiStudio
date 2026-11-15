/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.SetupEnvironment

/**
 * What an environment check targets: [distro], the home folder when one is already known ([homeHint]; the caller
 * asks WSL when it is null or not absolute) and the [environment] when one was found (null: a default one is made
 * for the home folder).
 */
internal data class CheckTarget(val distro: String, val homeHint: String?, val environment: SetupEnvironment?)

/**
 * The environment a check targets. An open attempt's own distribution ([wanted]) comes first when it is usable, so a
 * resumed attempt is checked where it ran; else the current environment, else the first usable distribution
 * ([detected] lists the default first). The distribution's name prefers the user's pick ([preferred]).
 */
internal fun checkTarget(
    detected: List<DistroStatus>,
    wanted: String?,
    preferred: String?,
    current: SetupEnvironment?,
): CheckTarget {
    val usable = detected.filterIsInstance<DistroStatus.Usable>()
    val wantedEnv = wanted?.let { w -> usable.firstOrNull { it.environment.distro == w }?.environment }
    val usableEnv = wantedEnv
        ?: (detected.firstOrNull() as? DistroStatus.Usable)?.environment
        ?: usable.firstOrNull()?.environment
    val distro = preferred ?: wanted ?: current?.distro ?: usableEnv?.distro ?: DEFAULT_DISTRO
    val switchToWanted = wanted != null && current?.distro != wanted && wantedEnv != null
    val environment = if (switchToWanted) wantedEnv else current ?: usableEnv
    return CheckTarget(distro, current?.home ?: usableEnv?.home, environment)
}

/** The environment assumed for [distro] when none was detected: Ubuntu 24.04 on WSL 2, the user named by [home]. */
internal fun defaultEnvironment(distro: String, home: String): SetupEnvironment = SetupEnvironment(
    distro = distro,
    wslVersion = 2,
    osId = "ubuntu",
    osVersionId = "24.04",
    user = home.substringAfterLast('/'),
    home = home,
)

private const val DEFAULT_DISTRO = "Ubuntu"
