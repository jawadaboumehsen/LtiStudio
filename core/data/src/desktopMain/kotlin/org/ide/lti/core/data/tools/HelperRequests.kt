/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.tools

import kotlinx.serialization.Serializable

@Serializable
public data class OutputData(val file: String, val size: Long = 0L, val mode: String = "exec")

@Serializable
public data class RefPayload(val type: String, val name: String)

@Serializable
public data class SourceData(
    val kind: String,
    val repo: String? = null,
    val commit: String? = null,
    val submodules: Map<String, String> = emptyMap(),
    val ref: RefPayload? = null,
    val version: String? = null,
    val url: String? = null,
    val sha256: String? = null,
    val member: String? = null,
)

@Serializable
public data class RecipeData(val type: String, val revision: Int = 1)

@Serializable
public data class VersionData(
    val schema: Int = 1,
    val group: String,
    val id: String,
    val label: String,
    val source: SourceData,
    val recipe: RecipeData,
    val outputs: List<OutputData> = emptyList(),
    val builtAt: String = "",
    val intact: Boolean = true,
)

@Serializable
public data class HelperInstallResult(
    val ok: Boolean = true,
    val reused: Boolean = false,
    val versionId: String? = null,
    val opId: String? = null,
    val type: String? = null,
    val schema: Int? = null,
    val code: String? = null,
    val message: String? = null,
)
