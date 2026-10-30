/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

import kotlinx.serialization.Serializable

/**
 * Verified Boot descriptor flags (Android Verified Boot / avbtool vbmeta header).
 */
@Serializable
public enum class VbmetaFlag(public val bitMask: Int, public val flagName: String, public val description: String) {
    DISABLE_VERITY(1, "FLAG_DISABLE_VERITY", "Disables dm-verity tree checking for verified partitions"),
    DISABLE_VERIFICATION(2, "FLAG_DISABLE_VERIFICATION", "Disables hash and signature verification checks"),
    DO_NOT_USE_AB(4, "FLAG_DO_NOT_USE_AB", "Disables A/B slot switching logic"),
}

/**
 * A change in a single Verified Boot flag.
 */
@Serializable
public data class BootFlagChange(
    public val flag: VbmetaFlag,
    public val wasEnabled: Boolean,
    public val isEnabled: Boolean,
) {
    public val isAdded: Boolean get() = !wasEnabled && isEnabled
    public val isRemoved: Boolean get() = wasEnabled && !isEnabled
}

/**
 * Difference between old and new verified-boot descriptor flags (FR-023).
 */
@Serializable
public data class BootFlagDiff(
    public val oldFlags: Int,
    public val newFlags: Int,
    public val changes: List<BootFlagChange> = emptyList(),
) {
    public val hasChanges: Boolean get() = oldFlags != newFlags

    public fun describe(): List<String> = buildList {
        for (change in changes) {
            when {
                change.isAdded -> add("Enable ${change.flag.flagName} (${change.flag.description})")
                change.isRemoved -> add("Disable ${change.flag.flagName}")
                else -> Unit
            }
        }
        val allKnownMask = VbmetaFlag.entries.fold(0) { acc, flag -> acc or flag.bitMask }
        val unknownOld = oldFlags and allKnownMask.inv()
        val unknownNew = newFlags and allKnownMask.inv()
        if (unknownOld != unknownNew) {
            add("Raw bits changed: 0x${oldFlags.toString(16)} -> 0x${newFlags.toString(16)}")
        }
    }

    public companion object {

        public fun compute(oldFlags: Int, newFlags: Int): BootFlagDiff {
            val changes = VbmetaFlag.entries.mapNotNull { flag ->
                val oldSet = (oldFlags and flag.bitMask) != 0
                val newSet = (newFlags and flag.bitMask) != 0
                if (oldSet != newSet) {
                    BootFlagChange(flag = flag, wasEnabled = oldSet, isEnabled = newSet)
                } else {
                    null
                }
            }
            return BootFlagDiff(oldFlags = oldFlags, newFlags = newFlags, changes = changes)
        }

        public fun toggleFlag(currentFlags: Int, flag: VbmetaFlag): Int = currentFlags xor flag.bitMask
    }
}
