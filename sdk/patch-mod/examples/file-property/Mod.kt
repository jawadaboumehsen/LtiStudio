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

import org.ide.lti.sdk.patchmod.eq
import org.ide.lti.sdk.patchmod.literal
import org.ide.lti.sdk.patchmod.romMod
import org.ide.lti.sdk.patchmod.setting

val exampleFilePropertyMod = romMod(
    publisher = "org.ide.lti",
    id = "example-file-property",
    version = "1.0.0",
) {
    settings {
        boolean(
            id = "opt_custom_hosts",
            label = "Install custom hosts file",
            default = true,
            help = "Copy custom hosts asset to /system/etc/hosts",
        )
        text(
            id = "opt_custom_banner",
            label = "Custom system banner",
            default = "LtiRom Custom Build",
            help = "Banner text inserted into system properties and notice file",
        )
    }

    operations {
        copy(
            id = "op_copy_hosts",
            source = "assets/overlay/hosts",
            partition = "system",
            path = "etc/hosts",
            condition = setting("opt_custom_hosts") eq literal("true"),
        )
        propertyPatch(
            id = "op_prop_banner",
            partition = "system",
            path = "build.prop",
            key = "ro.lti.banner",
            value = setting("opt_custom_banner"),
        )
        textPatch(
            id = "op_patch_notice",
            partition = "product",
            path = "etc/notice.txt",
            context = "DEFAULT_BANNER",
            replacement = setting("opt_custom_banner"),
            expectedSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            condition = setting("opt_custom_hosts") eq literal("true"),
        )
    }
}
