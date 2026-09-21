package com.kashif_e.backdrop

import kotlin.jvm.JvmInline

/** Immutable integer-backed set of bit flags. */
@JvmInline
internal value class Bitmask(private val value: Int = 0) {
    /** Returns a bitmask containing [flag]. */
    operator fun plus(flag: Int): Bitmask = Bitmask(value or flag)

    /** Returns a bitmask without [flag]. */
    operator fun minus(flag: Int): Bitmask = Bitmask(value and flag.inv())

    /** Returns whether every bit in [flag] is present. */
    operator fun contains(flag: Int): Boolean = (flag and value) == flag

    /** Returns whether any bit in [flag] is present. */
    fun any(flag: Int): Boolean = (flag and value) != 0

    /** Returns whether no bits are set. */
    fun isEmpty(): Boolean = value == 0
}
