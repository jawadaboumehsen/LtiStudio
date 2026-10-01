/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.example.hook

// Note: This compiled smali is the CompiledClassMerge payload.
// It is compiled against Android framework stubs by author tooling.
object ExampleHook {
    @JvmStatic
    fun onSignatureCheck(packageName: String?, signatureHash: String?) {
        android.util.Log.d("ExampleHook", "Checking signature: " + packageName + " " + signatureHash)
    }
}
