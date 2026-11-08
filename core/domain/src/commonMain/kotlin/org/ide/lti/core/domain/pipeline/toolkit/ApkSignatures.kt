/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

/**
 * APKs Android would not install because they carry no signature at all: neither an APK
 * Signing Block (v2/v3: its magic `APK Sig Block 42` right before the central directory, and the same size at both
 * ends of the block, so that an asset holding those bytes does not count) nor a v1 signature (an RSA, DSA or EC file
 * in META-INF). A zip with a comment has no fixed end record to read the directory's offset from, so there the magic
 * anywhere counts.
 * Whether a signature is valid is not checked here, only that there is one.
 */
public object ApkSignatures {
    /** `sh -c SCRIPT sh <work dir>`: the unsigned APKs, one per line, relative to the work dir. */
    public const val SCRIPT: String = """
        set -e
        cd "$1"
        find . -name '*.apk' -type f -print0 | xargs -0 -r -P8 -n1 sh -c '
          if [ "${'$'}(tail -c 22 "$1" | od -An -tx1 -N4 | tr -d " ")" = 504b0506 ]; then
            o=${'$'}(tail -c 6 "$1" | od -An -tu4 -N4 | tr -d " ")
            s=${'$'}(tail -c +${'$'}((o - 23)) "$1" | head -c 8 | od -An -tu8 | tr -d " ")
            if [ "${'$'}(tail -c +${'$'}((o - 15)) "$1" | head -c 16)" = "APK Sig Block 42" ] && [ "${'$'}s" -lt "${'$'}o" ] &&
              [ "${'$'}(tail -c +${'$'}((o - s - 7)) "$1" | head -c 8 | od -An -tu8 | tr -d " ")" = "${'$'}s" ]; then
              exit 0
            fi
          else
            LC_ALL=C grep -qa "APK Sig Block 42" "$1" && exit 0
          fi
          unzip -Z1 "$1" 2>/dev/null | grep -qiE "^META-INF/[^/]+[.](RSA|DSA|EC)$" && exit 0
          echo "${'$'}{1#./}"
        ' sh
    """

    public fun parse(output: String): List<String> = output.lines().map(
        String::trim,
    ).filter(String::isNotEmpty).sorted()
}
