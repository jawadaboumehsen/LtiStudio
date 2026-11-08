/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workbench

import org.ide.lti.core.domain.pipeline.text.PlatformMetadata
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * `sh -c READ_SCRIPT sh <tree> <file>...`: `== <file>` then the file's bytes in base64 on one line, for every one
 * that is a regular file (not a link) inside the tree.
 */
private const val READ_SCRIPT = """
    t="$1"; shift
    for f; do [ -f "${'$'}t/${'$'}f" ] && [ ! -L "${'$'}t/${'$'}f" ] && { echo "== ${'$'}f"; base64 -w0 "${'$'}t/${'$'}f"; echo; }; done
    exit 0
"""

/** The labelling rules of the tree at [treeRel] (normally `work`), read in one call to the build service. */
@OptIn(ExperimentalEncodingApi::class)
internal suspend fun WorkbenchRunner.Job.platformMetadata(treeRel: String = "work"): PlatformMetadata {
    val out = sh(READ_SCRIPT, abs(treeRel), *PlatformMetadata.FILES.toTypedArray())
    val files = mutableMapOf<String, ByteArray>()
    var name: String? = null
    out.lineSequence().forEach { line ->
        val current = name
        if (line.startsWith("== ")) {
            name = line.removePrefix("== ")
        } else if (current != null) {
            runCatching { Base64.decode(line.trim()) }.getOrNull()?.let { files[current] = it }
            name = null
        }
    }
    return PlatformMetadata.load { files[it] }
}
