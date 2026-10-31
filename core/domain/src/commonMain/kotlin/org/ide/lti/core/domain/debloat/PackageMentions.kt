/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.debloat

/**
 * The config files that name apps by package: `etc/permissions`, `etc/sysconfig` and
 * `etc/default-permissions` XMLs, with the `package="…"` values each holds. A file that only names apps the debloat
 * removes is removed with them; one that also declares a library, feature or permission is always kept.
 */
public object PackageMentions {
    /** A file's named packages, and whether it declares anything of its own (then it is never removed). */
    public data class Mentions(val packages: Set<String>, val declares: Boolean)

    /** `sh -c SCRIPT sh <work dir>`: `<file>\t<package>` per mention, `<file>\t!` for a file that declares things. */
    public const val SCRIPT: String = """
        cd "$1" || exit 0
        xmls() { find . -type f -name '*.xml' \( -path '*/etc/permissions/*' -o -path '*/etc/sysconfig/*' -o -path '*/etc/default-permissions/*' \) -print0; }
        xmls | xargs -0 -r grep -oHE 'package="[A-Za-z][A-Za-z0-9_.]*"' 2>/dev/null |
          sed -e 's#^\./##' -e 's#:package="\(.*\)"$#\t\1#'
        xmls | xargs -0 -r grep -lE '<(library|feature|updatable-library|apex-library)[[:space:]]' 2>/dev/null |
          sed -e 's#^\./##' -e 's#$#\t!#'
        # <permission> declares one only outside the grants of privapp-permissions and default-permissions.
        xmls | xargs -0 -r grep -lZE '<permission[[:space:]]' 2>/dev/null |
          xargs -0 -r grep -LE '<(privapp-permissions|exception)[[:space:]>]' 2>/dev/null |
          sed -e 's#^\./##' -e 's#$#\t!#'
        true
    """

    /** [SCRIPT]'s output by file (relative to work/). */
    public fun parse(output: String): Map<String, Mentions> = output.lines()
        .mapNotNull { line -> line.split('\t').takeIf { it.size == 2 && it[0].isNotBlank() } }
        .groupBy({ it[0] }, { it[1] })
        .mapValues { (_, values) -> Mentions(values.filter { it != "!" }.toSet(), "!" in values) }

    /** The files that name [pkg]. */
    public fun namedIn(mentions: Map<String, Mentions>, pkg: String): List<String> =
        mentions.filterValues { pkg in it.packages }.keys.sorted()

    /** The files that name only apps of [removed] and declare nothing: removed with those apps. */
    public fun orphaned(mentions: Map<String, Mentions>, removed: Set<String>): List<String> =
        mentions.filterValues { !it.declares && it.packages.isNotEmpty() && removed.containsAll(it.packages) }
            .keys.sorted()
}
