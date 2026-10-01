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

import org.ide.lti.core.model.plugin.HookPlacement
import org.ide.lti.sdk.patchmod.romMod

val exampleCompiledHookMod = romMod(
    publisher = "org.ide.lti",
    id = "example-compiled-hook",
    version = "1.0.0",
) {
    operations {
        compiledClassMerge(
            id = "op_merge_hook",
            partition = "system",
            path = "framework/services.jar",
            payload = "assets/payload",
            classDescriptors = listOf("Lorg/ide/lti/example/hook/ExampleHook;"),
        )
        hookInjection(
            id = "op_inject_hook",
            partition = "system",
            path = "framework/services.jar",
            classDescriptor = "Lcom/android/server/pm/InstallPackageHelper;",
            methodDescriptor = "scanPackageTracedLI(Lcom/android/server/pm/ParsedPackage;)V",
            opcodeSequence = listOf(
                "sget-object",
                "invoke-static",
                "const-string",
            ),
            expectedPreimageSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            hookDescriptor = "Lorg/ide/lti/example/hook/ExampleHook;->onSignatureCheck(Ljava/lang/String;Ljava/lang/String;)V",
            placement = HookPlacement.BEFORE,
            invokeSignature = "Lcom/android/server/pm/ScanPackageUtils;->collectCertificatesLI(...)V",
        )
    }
}
