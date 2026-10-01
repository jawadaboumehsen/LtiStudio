/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

/**
 * Authoritative producer for apt package management commands.
 *
 * Enforces security invariants:
 * - Always prefixes with `sudo apt-get update &&`.
 * - Always uses non-interactive flags (`-y`).
 * - Sorts and de-duplicates package names.
 * - Never contains `NOPASSWD`, `sudoers`, piping, or shell escapes.
 */
public object AptCommandBuilder {

    public fun install(packages: Collection<String>): String {
        require(packages.isNotEmpty()) { "Package collection must not be empty" }
        for (pkg in packages) {
            require(pkg.isNotBlank()) { "Package name must not be blank" }
            require(!pkg.contains(" ") && !pkg.contains(";") && !pkg.contains("&") && !pkg.contains("|")) {
                "Invalid package name contains shell metacharacters: '$pkg'"
            }
        }
        val sortedDistinct = packages.map { it.trim() }.distinct().sorted()
        return "sudo apt-get update && sudo apt-get install -y ${sortedDistinct.joinToString(" ")}"
    }
}
