/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package dev.chrisbanes.haze

import kotlin.jvm.JvmInline

/** Immutable integer-backed set of bit flags. */
@JvmInline
@InternalHazeApi
public value class Bitmask(private val value: Int = 0) {
    /** Returns a bitmask containing [flag]. */
    public operator fun plus(flag: Int): Bitmask = Bitmask(value or flag)

    /** Returns a bitmask without [flag]. */
    public operator fun minus(flag: Int): Bitmask = Bitmask(value and flag.inv())

    /** Returns whether every bit in [flag] is present. */
    public operator fun contains(flag: Int): Boolean = (flag and value) == flag

    /** Returns whether any bit in [flag] is present. */
    public fun any(flag: Int): Boolean = (flag and value) != 0

    /** Returns whether no bits are set. */
    public fun isEmpty(): Boolean = value == 0
}
