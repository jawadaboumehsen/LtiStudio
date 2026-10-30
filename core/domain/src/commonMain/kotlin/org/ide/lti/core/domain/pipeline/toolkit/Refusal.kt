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

import kotlinx.serialization.Serializable

/**
 * Uniform shape of every refusal or failure shown to the user (FR-037).
 *
 * Consists of three mandatory non-blank parts:
 * 1. [cause]: One plain sentence explaining why the operation was refused or failed.
 * 2. [item]: The specific file, partition, or tool involved.
 * 3. [nextStep]: One plain sentence action the user can take to remedy the problem.
 *
 * Optional [details] can hold extended technical diagnostics (e.g. exit codes, stderr)
 * which are collapsed into an expander in the UI and never shown in the headline.
 */
@Serializable
data class Refusal(val cause: String, val item: String, val nextStep: String, val details: String? = null) {
    init {
        require(cause.isNotBlank()) { "Refusal cause must not be blank" }
        require(item.isNotBlank()) { "Refusal item must not be blank" }
        require(nextStep.isNotBlank()) { "Refusal nextStep must not be blank" }
    }
}

/**
 * Exception thrown when a pipeline or toolkit operation is refused with a structured [Refusal].
 */
public class RefusalException(public val refusal: Refusal) :
    Exception("${refusal.cause} (${refusal.item}): ${refusal.nextStep}")

/**
 * Standard catalog of refusals across the ROM Workbench Toolkit (contract: `contracts/messages.md`, SC-009).
 */
public object RefusalCatalog {
    public fun incrementalPayload(item: String = "payload.bin"): Refusal = Refusal(
        cause = "The package is an incremental update.",
        item = item,
        nextStep = "Import the full update instead.",
    )

    public fun encryptedPackage(item: String): Refusal = Refusal(
        cause = "The package is encrypted.",
        item = item,
        nextStep = "Decrypt the package with the vendor tool before importing.",
    )

    public fun unknownPackage(item: String): Refusal = Refusal(
        cause = "The package format was not recognized.",
        item = item,
        nextStep = "Use a supported ZIP, 7z, TAR, payload.bin, block-OTA, or Odin archive.",
    )

    public fun checksumMismatch(item: String, expected: String, actual: String): Refusal = Refusal(
        cause = "The package checksum does not match the manifest.",
        item = item,
        nextStep = "Re-download the firmware or verify the source file.",
        details = "Expected: $expected, Actual: $actual",
    )

    public fun superOverlap(item: String = "super.img", details: String? = null): Refusal = Refusal(
        cause = "The dynamic partition images exceed the super partition size.",
        item = item,
        nextStep = "Reduce partition content or adjust the super partition layout.",
        details = details,
    )

    public fun vabcMissing(item: String = "Virtual-A/B"): Refusal = Refusal(
        cause = "Virtual-A/B compression properties are absent.",
        item = item,
        nextStep = "Verify device compatibility or acknowledge proceeding without compression.",
    )

    public fun toolMissing(item: String): Refusal = Refusal(
        cause = "Required tool is not installed.",
        item = item,
        nextStep = "Install the required tool group from the Tools page.",
    )

    public fun workspaceLocked(item: String = "Workspace", holder: String): Refusal = Refusal(
        cause = "Another operation is currently editing this workspace.",
        item = item,
        nextStep = "Wait for active operation '$holder' to complete or cancel it.",
        details = "Lock held by: $holder",
    )

    public fun uncoveredFiles(item: String = "Work tree", count: Int): Refusal = Refusal(
        cause = "Files in the work tree are missing from fs_config or file_contexts.",
        item = item,
        nextStep = "Click 'Add entries' to generate coverage rules for uncovered files.",
        details = "$count uncovered entry(ies)",
    )

    public val ALL_SAMPLES: List<Refusal> = listOf(
        incrementalPayload(),
        encryptedPackage("firmware.tar.md5"),
        unknownPackage("archive.bin"),
        checksumMismatch("package.zip", "abc", "def"),
        superOverlap(),
        vabcMissing(),
        toolMissing("lpunpack"),
        workspaceLocked(holder = "Debloat"),
        uncoveredFiles(count = 3),
    )
}
