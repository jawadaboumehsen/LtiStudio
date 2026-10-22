/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.example.mod

import org.ide.lti.sdk.patchmod.Partition
import org.ide.lti.sdk.patchmod.isOn
import org.ide.lti.sdk.patchmod.romMod

val basicMod = romMod(
    publisher = "org.example",
    id = "basic-mod",
    version = "1.0.0",
) {
    // The devices (and optionally firmware versions) this mod is made for.
    // compatibleWith("PQ84P01")
    val enabled = settings.boolean(
        id = "opt_enabled",
        label = "Enable feature",
        default = true,
        help = "Enable or disable this mod feature",
    )
    operations {
        // Demonstration only: replace this with your mod's operations before installing it.
        copy("op_copy", "assets/example.conf", Partition.SYSTEM, "etc/basic-mod.conf", condition = enabled.isOn)
        // apkJarPatch("op_jar", Partition.SYSTEM, "system/framework/services.jar") {
        //     method("Lcom/android/server/Foo;", "bar") { insertAfter("    .locals 2", "    nop") }
        // }
    }
}
