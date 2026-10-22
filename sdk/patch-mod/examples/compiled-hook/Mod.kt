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
import org.ide.lti.sdk.patchmod.addSmali
import org.ide.lti.sdk.patchmod.romMod
import java.io.File

/**
 * A hook in two parts: the Java class (`hooks/smali_classes2/…/ExampleHook.java`, compiled by `compileModPayload`
 * into `files/smali_classes2/…`) is added to services.jar, and a method edit calls it.
 */
val exampleCompiledHookMod = romMod(
    publisher = "org.ide.lti",
    id = "example-compiled-hook",
    version = "1.0.0",
) {
    operations {
        apkJarPatch("op_hook", Partition.SYSTEM, "framework/services.jar") {
            addSmali(File("files"))
            method("Lcom/android/server/pm/InstallPackageHelper;", "scanSystemPackageLI") {
                references("Lcom/android/server/pm/ScanPackageUtils;->collectCertificatesLI")
                // @{setting} captures the call's first register (the package setting) and passes it to the hook.
                insertBefore(
                    "invoke-static/range {@{setting}@{*}}, " +
                        "Lcom/android/server/pm/ScanPackageUtils;->collectCertificatesLI@{*}",
                    "    invoke-static {@{setting}}, " +
                        "Lorg/ide/lti/example/hook/ExampleHook;->onCollectCertificates(Ljava/lang/Object;)V",
                )
            }
        }
    }
}
