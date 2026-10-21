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
        // Add mod operations here, e.g.:
        // copy("op_copy", "assets/my_asset", Partition.SYSTEM, "etc/my_file", condition = enabled.isOn)
        // propertyPatch("op_prop", Partition.SYSTEM, "build.prop", "ro.example.flag", literal("true"))
        // apkJarPatch("op_jar", Partition.SYSTEM, "system/framework/services.jar") {
        //     method("Lcom/android/server/Foo;", "bar") { insertAfter("    .locals 2", "    nop") }
        // }
    }
}
