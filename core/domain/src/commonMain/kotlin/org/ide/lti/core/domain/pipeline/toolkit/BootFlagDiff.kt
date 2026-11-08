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
 * The four ways vbmeta's two verification flags can be set, in words: what the phone checks, what it stops
 * checking, and whether a locked bootloader still boots it. The flags sit in vbmeta's signed header, so changing
 * them breaks the signature; a locked bootloader also refuses disabled verification outright (libavb).
 */
public enum class VerifiedBootMode(
    public val flags: Int,
    public val title: String,
    public val checks: String,
    public val stops: String,
    public val locked: String,
) {
    VERIFY_ALL(
        0,
        "Verify everything",
        "vbmeta's signature, the hash of every image it names (boot, init_boot, vendor_boot, dtbo), and every block " +
            "of system, vendor and the other dynamic partitions as it is read (dm-verity).",
        "Nothing.",
        "Boots only when everything is signed with the key the bootloader trusts: a rebuilt image does not boot.",
    ),
    NO_VERITY(
        VbmetaFlag.DISABLE_VERITY.bitMask,
        "Turn off dm-verity",
        "vbmeta's signature and the hashes of the boot images.",
        "The dynamic partitions mount without dm-verity: a changed system boots, its blocks are not checked.",
        "Refused: the changed flag breaks vbmeta's signature. Unlock the bootloader.",
    ),
    NO_VERIFICATION(
        VbmetaFlag.DISABLE_VERIFICATION.bitMask,
        "Turn off verification",
        "Only vbmeta's layout: which partitions there are.",
        "Every signature and hash; dm-verity is not set up either, as its trees are no longer trusted.",
        "Refused outright: a locked bootloader never boots with verification turned off.",
    ),
    NONE(
        VbmetaFlag.DISABLE_VERITY.bitMask or VbmetaFlag.DISABLE_VERIFICATION.bitMask,
        "Turn off both",
        "Nothing.",
        "Everything: any image boots. What fastboot --disable-verity --disable-verification flashes, and what " +
            "this ROM expects (vbmeta is never in the package).",
        "Refused: unlock the bootloader first.",
    ),
    ;

    /** [current] flags with this mode's two bits, the others (DO_NOT_USE_AB) kept. */
    public fun applyTo(current: Int): Int = (current and MASK.inv()) or flags

    public companion object {
        private val MASK = VbmetaFlag.DISABLE_VERITY.bitMask or VbmetaFlag.DISABLE_VERIFICATION.bitMask

        /** The mode [flags] are in. */
        public fun of(flags: Int): VerifiedBootMode = entries.first { it.flags == flags and MASK }
    }
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
