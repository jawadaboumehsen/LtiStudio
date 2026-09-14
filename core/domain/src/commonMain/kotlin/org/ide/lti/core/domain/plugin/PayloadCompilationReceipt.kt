/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.plugin

import kotlinx.serialization.Serializable
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport

@Serializable
data class ToolIdentity(val name: String, val version: String, val sha256: String?)

@Serializable
data class PayloadCompilationReceipt(
    val sourceFingerprint: String,
    val outputSha256: String,
    val kotlinCompiler: ToolIdentity,
    val dexer: ToolIdentity,
    val disassembler: ToolIdentity,
    val frameworkStubDigest: String,
    val minRuntimeApi: Int,
    val dependencyDigests: List<String>,
    val emittedClassDescriptors: List<String>,
)

interface PayloadCompilerPort {
    suspend fun compile(request: PayloadCompilationRequest): PayloadCompilationResult
}

data class PayloadCompilationRequest(
    val sourceFiles: List<String>,
    val frameworkStubJars: List<String>,
    val dependencyJars: List<String>,
    val minRuntimeApi: Int,
    val outputSmaliDir: String,
)

sealed interface PayloadCompilationResult {
    data class Compiled(val receipt: PayloadCompilationReceipt, val smaliFiles: List<String>) : PayloadCompilationResult

    data class Failed(val report: ValidationReport) : PayloadCompilationResult
}

object PayloadCompilerCodes {
    const val TOOL_NOT_FOUND: String = "TOOL_NOT_FOUND"
    const val KOTLINC_FAILED: String = "KOTLINC_FAILED"
    const val DEX_FAILED: String = "DEX_FAILED"
    const val DISASSEMBLE_FAILED: String = "DISASSEMBLE_FAILED"
    const val KOTLIN_RUNTIME_REFERENCE: String = "KOTLIN_RUNTIME_REFERENCE"
    const val PLATFORM_CLASS_EMITTED: String = "PLATFORM_CLASS_EMITTED"
    const val NO_CLASSES_EMITTED: String = "NO_CLASSES_EMITTED"
}
