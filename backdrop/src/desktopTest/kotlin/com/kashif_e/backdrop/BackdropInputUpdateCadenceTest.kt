package com.kashif_e.backdrop

import kotlin.test.Test
import kotlin.test.assertEquals

class BackdropInputUpdateCadenceTest {

    @Test
    fun firstRecordedUpdateIsNeverABurst() {
        val cadence = BackdropInputUpdateCadence()
        assertEquals(1, cadence.recordUpdate(nowNanos = 0L))
    }

    @Test
    fun updatesWithinTheBurstWindowIncrementTheMultiplier() {
        val cadence = BackdropInputUpdateCadence()
        assertEquals(1, cadence.recordUpdate(nowNanos = 0L))
        assertEquals(2, cadence.recordUpdate(nowNanos = 50_000_000L))
        assertEquals(3, cadence.recordUpdate(nowNanos = 100_000_000L))
    }

    @Test
    fun multiplierCapsAtThreeEvenWithMoreBurstUpdates() {
        val cadence = BackdropInputUpdateCadence()
        cadence.recordUpdate(nowNanos = 0L)
        cadence.recordUpdate(nowNanos = 10_000_000L)
        cadence.recordUpdate(nowNanos = 20_000_000L)
        assertEquals(3, cadence.recordUpdate(nowNanos = 30_000_000L))
    }

    @Test
    fun anUpdateJustPastTheWindowResetsToOne() {
        val cadence = BackdropInputUpdateCadence()
        cadence.recordUpdate(nowNanos = 0L)
        cadence.recordUpdate(nowNanos = 50_000_000L)
        assertEquals(1, cadence.recordUpdate(nowNanos = 150_000_001L))
    }

    @Test
    fun anUpdateExactlyAtTheWindowBoundaryStillCountsAsABurst() {
        val cadence = BackdropInputUpdateCadence()
        cadence.recordUpdate(nowNanos = 0L)
        assertEquals(2, cadence.recordUpdate(nowNanos = 100_000_000L))
    }

    @Test
    fun resetForgetsThePreviousMultiplierAndTiming() {
        val cadence = BackdropInputUpdateCadence()
        cadence.recordUpdate(nowNanos = 0L)
        cadence.recordUpdate(nowNanos = 10_000_000L)
        cadence.reset()
        assertEquals(1, cadence.recordUpdate(nowNanos = 20_000_000L))
    }
}
