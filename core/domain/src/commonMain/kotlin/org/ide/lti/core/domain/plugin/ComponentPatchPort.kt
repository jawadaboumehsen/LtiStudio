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

import org.ide.lti.core.domain.pipeline.configuration.ValidationReport

/**
 * Represents a decoded APK or JAR component directory containing smali bytecode and resources.
 *
 * @property componentPath Original filesystem path to the component archive being patched.
 * @property decodeRoot Directory containing the decoded smali files and extracted resources.
 */
data class DecodedComponent(val componentPath: String, val decodeRoot: String)

/**
 * Port abstraction for decoding, inspecting, mutating, and rebuilding APK and JAR components.
 *
 * Implementations shell out to tools like apktool or baksmali/smali in desktop environments,
 * while domain logic operates purely against this port contract.
 */
interface ComponentPatchPort {
    suspend fun decode(componentAbsPath: String): DecodedComponent?
    suspend fun readMember(decoded: DecodedComponent, relPath: String): String?
    suspend fun writeMember(decoded: DecodedComponent, relPath: String, content: String): Boolean
    suspend fun listMembers(decoded: DecodedComponent, prefix: String): List<String>
    suspend fun rebuild(decoded: DecodedComponent, outputAbsPath: String): Boolean
    suspend fun discard(decoded: DecodedComponent)
}

/**
 * Execution receipt recording the outcome and metadata of patching a single component.
 *
 * @property componentPath Filesystem path to the patched component.
 * @property decodedOnce Confirmation that decode-once contract was satisfied for this component.
 * @property operationIds List of mutation operation identifiers executed on this component.
 * @property addedClasses Descriptors of all new classes merged into the component.
 * @property changedMethods Descriptors of all methods modified via smali diffs or hook injections.
 * @property aotArtifactsInvalidated Sibling AOT compilation artifacts that downstream build stages must purge.
 */
data class ComponentPatchReceipt(
    val componentPath: String,
    val decodedOnce: Boolean,
    val operationIds: List<String>,
    val addedClasses: List<String>,
    val changedMethods: List<String>,
    val aotArtifactsInvalidated: List<String>,
)

/**
 * Outcome of applying or previewing APK/JAR mutation operations across components.
 */
sealed interface ApkJarOutcome {
    data class Applied(val effects: List<MutationEffect>, val receipts: List<ComponentPatchReceipt>) : ApkJarOutcome

    data class Refused(val report: ValidationReport) : ApkJarOutcome
}

/**
 * Validation and execution failure codes for APK and JAR component patching.
 */
object ComponentPatchCodes {
    const val COMPONENT_MISSING: String = "COMPONENT_MISSING"
    const val DECODE_FAILED: String = "DECODE_FAILED"
    const val REBUILD_FAILED: String = "REBUILD_FAILED"
    const val SMALI_MEMBER_MISSING: String = "SMALI_MEMBER_MISSING"
    const val SMALI_PREIMAGE_MISMATCH: String = "SMALI_PREIMAGE_MISMATCH"
    const val PATCH_CONTEXT_NOT_FOUND: String = "PATCH_CONTEXT_NOT_FOUND"
    const val PATCH_CONTEXT_AMBIGUOUS: String = "PATCH_CONTEXT_AMBIGUOUS"
    const val DUPLICATE_CLASS: String = "DUPLICATE_CLASS"
    const val PLATFORM_CLASS_COLLISION: String = "PLATFORM_CLASS_COLLISION"
    const val UNRESOLVED_DEPENDENCY: String = "UNRESOLVED_DEPENDENCY"
    const val ANCHOR_NOT_FOUND: String = "ANCHOR_NOT_FOUND"
    const val ANCHOR_AMBIGUOUS: String = "ANCHOR_AMBIGUOUS"
    const val ANCHOR_PREIMAGE_MISMATCH: String = "ANCHOR_PREIMAGE_MISMATCH"
    const val HOOK_SIGNATURE_MISMATCH: String = "HOOK_SIGNATURE_MISMATCH"
    const val PAYLOAD_MISSING: String = "PAYLOAD_MISSING"
}
