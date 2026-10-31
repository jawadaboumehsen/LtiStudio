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
 * Signing Block (v2/v3, its magic `APK Sig Block 42`) nor a v1 signature (an RSA, DSA or EC file in META-INF).
 * Whether a signature is valid is not checked here, only that there is one.
 */
public object ApkSignatures {
    /** `sh -c SCRIPT sh <work dir>`: the unsigned APKs, one per line, relative to the work dir. */
    public const val SCRIPT: String = """
        set -e
        cd "$1"
        find . -name '*.apk' -type f -print0 | xargs -0 -r -P8 -n1 sh -c '
          LC_ALL=C grep -qa "APK Sig Block 42" "$1" && exit 0
          unzip -Z1 "$1" 2>/dev/null | grep -qiE "^META-INF/[^/]+[.](RSA|DSA|EC)$" && exit 0
          echo "${'$'}{1#./}"
        ' sh
    """

    public fun parse(output: String): List<String> = output.lines().map(
        String::trim,
    ).filter(String::isNotEmpty).sorted()
}
