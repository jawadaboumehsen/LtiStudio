/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.install

/** Architecture used when choosing the bundled Linux tool releases. */
public fun detectHostArch(): String = when (System.getProperty("os.arch")?.lowercase()) {
    "aarch64", "arm64" -> "aarch64"
    else -> "x86_64"
}
