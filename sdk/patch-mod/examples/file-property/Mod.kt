/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.sdk.patchmod.example

import org.ide.lti.sdk.patchmod.Partition
import org.ide.lti.sdk.patchmod.isOn
import org.ide.lti.sdk.patchmod.romMod

val exampleFilePropertyMod = romMod(
    publisher = "org.ide.lti",
    id = "example-file-property",
    version = "1.0.0",
) {
    compatibleWith("PQ84P01")

    val customHosts = settings.boolean(
        id = "opt_custom_hosts",
        label = "Install custom hosts file",
        default = true,
        help = "Copy custom hosts asset to /system/etc/hosts",
    )
    val banner = settings.text(
        id = "opt_custom_banner",
        label = "Custom system banner",
        default = "LtiRom Custom Build",
        help = "Banner text inserted into system properties and notice file",
    )

    operations {
        copy(
            id = "op_copy_hosts",
            source = "assets/overlay/hosts",
            partition = Partition.SYSTEM,
            path = "etc/hosts",
            condition = customHosts.isOn,
        )
        propertyPatch(
            id = "op_prop_banner",
            partition = Partition.SYSTEM,
            path = "build.prop",
            key = "ro.lti.banner",
            value = banner.ref,
        )
        textPatch(
            id = "op_patch_notice",
            partition = Partition.PRODUCT,
            path = "etc/notice.txt",
            context = "DEFAULT_BANNER",
            replacement = banner.ref,
            expectedSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            condition = customHosts.isOn,
        )
    }
}
