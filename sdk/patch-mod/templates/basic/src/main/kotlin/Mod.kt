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
    settings {
        boolean(
            id = "opt_enabled",
            label = "Enable feature",
            default = true,
            help = "Enable or disable this mod feature",
        )
    }
    operations {
        // Add mod operations here, e.g.:
        // copy("op_copy", "assets/my_asset", "system", "etc/my_file")
        // propertyPatch("op_prop", "system", "build.prop", "ro.example.flag", literal("true"))
    }
}
