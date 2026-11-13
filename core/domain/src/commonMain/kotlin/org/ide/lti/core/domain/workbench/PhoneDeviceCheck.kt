/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workbench

import org.ide.lti.core.model.target.TargetDevice

/** One device-configuration value next to what the phone measured, and how to take the phone's. */
public class PhoneFact(
    public val label: String,
    public val configured: String,
    public val measured: String,
    public val apply: (TargetDevice) -> TargetDevice,
) {
    public val differs: Boolean get() = configured != measured
}

/**
 * The device configuration against the phone itself (`fastboot getvar all`): measured beats assumed. Only what the
 * phone answered is compared: its fastboot product, the super partition's size and the boot chain's partition sizes
 * (the running slot's copy).
 */
public object PhoneDeviceCheck {
    public fun facts(phone: PhoneFacts, device: TargetDevice): List<PhoneFact> {
        fun size(name: String): Long? {
            val own = phone.partition(name) ?: phone.currentSlot?.let { phone.partition(name + it) }
            return own?.sizeBytes
        }
        fun bytes(label: String, name: String, configured: Long, set: (TargetDevice, Long) -> TargetDevice) =
            size(name)?.let { measured ->
                PhoneFact(label, configured.toString(), measured.toString()) { set(it, measured) }
            }
        return listOfNotNull(
            phone.product?.takeIf { phone.mode.fastboot }?.let { p ->
                PhoneFact("Fastboot product", device.fastbootProduct, p) { it.copy(fastbootProduct = p) }
            },
            bytes("Super partition (bytes)", phone.superName ?: "super", device.superPartitionBytes) { d, v ->
                d.copy(superPartitionBytes = v)
            },
            bytes("boot (bytes)", "boot", device.bootPartitionBytes) { d, v -> d.copy(bootPartitionBytes = v) },
            bytes("vendor_boot (bytes)", "vendor_boot", device.vendorBootPartitionBytes) { d, v ->
                d.copy(vendorBootPartitionBytes = v)
            },
            bytes("init_boot (bytes)", "init_boot", device.initBootPartitionBytes) { d, v ->
                d.copy(initBootPartitionBytes = v)
            },
            bytes("dtbo (bytes)", "dtbo", device.dtboPartitionBytes) { d, v -> d.copy(dtboPartitionBytes = v) },
        )
    }
}
