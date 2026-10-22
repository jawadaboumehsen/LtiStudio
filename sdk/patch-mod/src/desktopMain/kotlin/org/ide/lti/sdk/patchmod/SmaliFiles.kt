/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.sdk.patchmod

import java.io.File

/**
 * Adds every smali class under [dir] to the component, each as the member at its path below [dir]
 * (`smali_classes2/org/example/Hook.smali`): the classes `compileModPayload` compiled from a mod's Java hooks.
 * A mod then calls them from a [ApkJarScope.method] edit.
 */
fun ApkJarScope.addSmali(dir: File) {
    require(dir.isDirectory) { "No compiled hooks in ${dir.path}: run compileModPayload first" }
    dir.walkTopDown().filter { it.isFile && it.extension == "smali" }.sortedBy { it.invariantSeparatorsPath }
        .forEach { addFile(it.relativeTo(dir).invariantSeparatorsPath, it.readText()) }
}
