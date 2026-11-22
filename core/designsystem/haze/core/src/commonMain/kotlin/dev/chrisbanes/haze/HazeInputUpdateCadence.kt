/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package dev.chrisbanes.haze

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Tracks short bursts of distinct effect-input updates for adaptive sampling policies. */
@InternalHazeApi
public class HazeInputUpdateCadence(
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private var previousUpdateKey: Any? = UnsetUpdateKey
    private var lastUpdateMark: TimeMark? = null

    /** Number of distinct updates observed in the current burst, capped at three. */
    public var multiplier: Int = 1
        private set

    /** Returns whether [multiplier] changed. Repeated observations of the same input do not count. */
    public fun observeUpdate(updateKey: Any?): Boolean {
        if (previousUpdateKey == updateKey) {
            val burstExpired = lastUpdateMark?.elapsedNow()?.let { it > UPDATE_BURST_WINDOW } == true
            if (burstExpired && multiplier != 1) {
                multiplier = 1
                return true
            }
            return false
        }

        val rapidUpdate = lastUpdateMark?.elapsedNow()?.let { it <= UPDATE_BURST_WINDOW } == true
        val previousMultiplier = multiplier
        multiplier = if (rapidUpdate) {
            (multiplier + 1).coerceAtMost(MAX_UPDATE_BURST_SIZE)
        } else {
            1
        }
        previousUpdateKey = updateKey
        lastUpdateMark = timeSource.markNow()
        return multiplier != previousMultiplier
    }

    /** Clears the current update burst and restores [multiplier] to one. */
    public fun reset() {
        previousUpdateKey = UnsetUpdateKey
        lastUpdateMark = null
        multiplier = 1
    }
}

private object UnsetUpdateKey

private const val MAX_UPDATE_BURST_SIZE: Int = 3
private val UPDATE_BURST_WINDOW = 100.milliseconds
