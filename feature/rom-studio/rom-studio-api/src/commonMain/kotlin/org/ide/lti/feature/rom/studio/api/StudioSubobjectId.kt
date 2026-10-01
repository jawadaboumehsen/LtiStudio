/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.api

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Strongly-typed value class representing a subobject identifier within a pipeline stage editor.
 */
@Serializable
@JvmInline
public value class StudioSubobjectId(public val value: String) {
    init {
        require(value.isNotBlank()) { "StudioSubobjectId value must not be blank" }
        require(!value.contains('\n') && !value.contains('\u0000')) {
            "StudioSubobjectId value must not contain newlines or null bytes: '$value'"
        }
    }

    override fun toString(): String = value
}
