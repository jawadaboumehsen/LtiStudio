package com.kashif_e.backdrop

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpticalSizeValueTest {

    @Test
    fun fixedResolvesToItsValueAtAnySize() {
        val value = OpticalSizeValue.Fixed(4.dp)
        assertEquals(4.dp, value.resolve(1.dp))
        assertEquals(4.dp, value.resolve(2000.dp))
    }

    @Test
    fun responsiveClampsOutsideAuthoredRange() {
        val value = OpticalSizeValue.Responsive(
            OpticalSizePoint(64.dp, 2.dp),
            OpticalSizePoint(220.dp, 8.dp),
        )
        assertEquals(2.dp, value.resolve(20.dp))
        assertEquals(8.dp, value.resolve(4000.dp))
    }

    @Test
    fun responsiveInterpolatesAtMidpoint() {
        val value = OpticalSizeValue.Responsive(
            OpticalSizePoint(1.dp, 0.dp),
            OpticalSizePoint(101.dp, 100.dp),
        )
        // smoothstep(1, 101, 51) = 0.5 exactly (symmetry of the cubic ease at the midpoint).
        assertEquals(50.dp, value.resolve(51.dp))
    }

    @Test
    fun responsiveRejectsFewerThanTwoPoints() {
        assertFailsWith<IllegalArgumentException> {
            OpticalSizeValue.Responsive(OpticalSizePoint(64.dp, 2.dp))
        }
    }

    @Test
    fun responsiveRejectsNonIncreasingDimensions() {
        assertFailsWith<IllegalArgumentException> {
            OpticalSizeValue.Responsive(
                OpticalSizePoint(100.dp, 2.dp),
                OpticalSizePoint(50.dp, 8.dp),
            )
        }
    }

    @Test
    fun scaleMultipliesAFixedValue() {
        val value = OpticalSizeValue.Fixed(40.dp)
        assertEquals(26.dp, value.scale(0.65f).resolve(1.dp))
    }

    @Test
    fun scaleMultipliesEveryResponsivePointIndependently() {
        val value = OpticalSizeValue.Responsive(
            OpticalSizePoint(64.dp, 20.dp),
            OpticalSizePoint(220.dp, 40.dp),
        )
        val scaled = value.scale(0.5f)
        assertEquals(10.dp, scaled.resolve(20.dp))
        assertEquals(20.dp, scaled.resolve(4000.dp))
    }
}
