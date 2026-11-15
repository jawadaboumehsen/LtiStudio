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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CheckTargetTest {
    private fun env(distro: String) = defaultEnvironment(distro, "/home/$distro-user")

    private val ubuntu = env("Ubuntu")
    private val other = env("Ubuntu-22.04")
    private val detected = listOf(DistroStatus.Usable(ubuntu), DistroStatus.Usable(other))

    @Test
    fun anOpenAttemptIsCheckedInItsOwnDistributionEvenWhenAnotherIsCurrent() {
        val target = checkTarget(detected, wanted = "Ubuntu-22.04", preferred = null, current = ubuntu)
        assertEquals("Ubuntu-22.04", target.distro)
        assertEquals(other, target.environment)
        // The home folder still comes from the current environment, as before the split.
        assertEquals(ubuntu.home, target.homeHint)
    }

    @Test
    fun withoutAnAttemptTheCurrentEnvironmentStaysAndTheUsersPickNamesIt() {
        val target = checkTarget(detected, wanted = null, preferred = "Ubuntu-22.04", current = ubuntu)
        assertEquals("Ubuntu-22.04", target.distro)
        assertEquals(ubuntu, target.environment)
    }

    @Test
    fun aFirstCheckTakesTheDefaultUsableDistributionAndNothingDetectedGetsTheDefault() {
        val first = checkTarget(detected, wanted = null, preferred = null, current = null)
        assertEquals(ubuntu, first.environment)
        assertEquals("Ubuntu", first.distro)
        // The default distribution unusable: the first usable one.
        val fallback = checkTarget(
            listOf(DistroStatus.NoUsableUser("Ubuntu", "root only"), DistroStatus.Usable(other)),
            wanted = null,
            preferred = null,
            current = null,
        )
        assertEquals(other, fallback.environment)
        val none = checkTarget(listOf(DistroStatus.NoDistro), wanted = null, preferred = null, current = null)
        assertEquals("Ubuntu", none.distro)
        assertNull(none.environment)
        assertNull(none.homeHint)
        assertEquals("me", defaultEnvironment("Ubuntu", "/home/me").user)
    }
}
