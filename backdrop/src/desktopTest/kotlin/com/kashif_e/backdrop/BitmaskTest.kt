package com.kashif_e.backdrop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BitmaskTest {

    private val flagA = 1 shl 0
    private val flagB = 1 shl 1
    private val flagC = 1 shl 2

    @Test
    fun emptyBitmaskIsEmpty() {
        assertTrue(Bitmask().isEmpty())
    }

    @Test
    fun plusAddsFlag() {
        val mask = Bitmask() + flagA
        assertTrue(flagA in mask)
        assertFalse(flagB in mask)
        assertFalse(mask.isEmpty())
    }

    @Test
    fun minusRemovesOnlyThatFlag() {
        val mask = (Bitmask() + flagA + flagB) - flagA
        assertFalse(flagA in mask)
        assertTrue(flagB in mask)
    }

    @Test
    fun containsRequiresEveryBitInFlag() {
        val mask = Bitmask() + flagA + flagB
        assertTrue((flagA or flagB) in mask)
        assertFalse((flagA or flagB or flagC) in mask)
    }

    @Test
    fun anyMatchesIfAtLeastOneBitPresent() {
        val mask = Bitmask() + flagA
        assertTrue(mask.any(flagA or flagC))
        assertFalse(mask.any(flagB or flagC))
    }

    @Test
    fun equalMasksAreStructurallyEqual() {
        assertEquals(Bitmask() + flagA + flagB, Bitmask() + flagB + flagA)
    }
}
