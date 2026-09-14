/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.text

import org.ide.lti.core.domain.pipeline.RuntimeKeys
import org.ide.lti.core.domain.pipeline.StageContext

/**
 * `build.prop` stamp (contract Stage 3 step 6): drop then append the `org.lti.build.*` keys and
 * replace `ro.build.type` in place. Pure text transform so it can be unit-tested.
 */
public object BuildPropStamper {
    private val LTI_KEYS = listOf(
        "org.lti.build.date",
        "org.lti.build.device",
        "org.lti.build.version",
        "org.lti.build.variant",
    )

    public fun stamper(ctx: StageContext): (String) -> String = { original ->
        stamp(
            original = original,
            date = requireNotNull(ctx.value(RuntimeKeys.RUN_DATE)) { "run date missing from runtime values" },
            device = ctx.target.id,
            version = ctx.snapshot.assembly.romVersion,
            buildType = ctx.snapshot.assembly.buildType,
        )
    }

    public fun stamp(original: String, date: String, device: String, version: String, buildType: String): String {
        val kept = original.lineSequence()
            .filterNot { line -> LTI_KEYS.any { key -> line.startsWith("$key=") } }
            .map { line -> if (line.startsWith("ro.build.type=")) "ro.build.type=$buildType" else line }
            .toList()
            .dropLastWhile { it.isBlank() }
        val appended = listOf(
            "org.lti.build.date=$date",
            "org.lti.build.device=$device",
            "org.lti.build.version=$version",
            "org.lti.build.variant=$buildType",
        )
        return (kept + appended).joinToString("\n", postfix = "\n")
    }

    /** Reads a key from stock `build.prop` text captured at extraction time. */
    public fun prop(buildProp: String?, key: String): String? =
        buildProp?.lineSequence()?.firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')?.trim()?.takeIf { it.isNotEmpty() }
}
