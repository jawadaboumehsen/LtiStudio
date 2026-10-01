/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.target

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.target.TargetDevice
import java.security.MessageDigest

private val canonicalJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    prettyPrint = false
}

public fun computeTargetDigest(target: TargetDevice): String {
    val jsonString = canonicalJson.encodeToString(target)
    val md = MessageDigest.getInstance("SHA-256")
    val hashBytes = md.digest(jsonString.toByteArray(Charsets.UTF_8))
    return "sha256:" + hashBytes.joinToString("") { "%02x".format(it) }
}

/**
 * Immutable value type identifying one archived revision of a target device profile.
 */
@Serializable
public data class TargetRevisionRef(val profileId: String, val revision: Int, val contentDigest: String) {
    init {
        require(revision >= 1) { "revision must be >= 1, but was $revision" }
        require(contentDigest.startsWith("sha256:")) {
            "contentDigest must start with 'sha256:', but was $contentDigest"
        }
    }

    public companion object {
        public fun of(target: TargetDevice): TargetRevisionRef = TargetRevisionRef(
            profileId = target.id,
            revision = target.revision,
            contentDigest = computeTargetDigest(target),
        )

        public fun computeDigest(target: TargetDevice): String = computeTargetDigest(target)
    }
}

/**
 * Outcome of a reference-safe target revision deletion request.
 */
public sealed interface TargetRevisionDeleteOutcome {
    public data class Deleted(val profileId: String, val revision: Int) : TargetRevisionDeleteOutcome
    public data class Refused(val report: ValidationReport) : TargetRevisionDeleteOutcome
    public data class NotFound(val profileId: String, val revision: Int) : TargetRevisionDeleteOutcome

    public val isDeleted: Boolean get() = this is Deleted
}

/**
 * Port for immutable, content-addressed target profile revisions.
 */
public interface TargetRevisionRepository {
    public suspend fun archive(target: TargetDevice): TargetRevisionRef

    public suspend fun get(profileId: String, revision: Int): TargetDevice?

    public suspend fun listRevisions(profileId: String): List<TargetRevisionRef>

    public suspend fun delete(
        profileId: String,
        revision: Int,
        referencedBy: List<String> = emptyList(),
    ): TargetRevisionDeleteOutcome

    public companion object {
        public const val REFERENCED_TARGET_REVISION: String = "REFERENCED_TARGET_REVISION"
    }
}

/**
 * Field-level difference between old and new target device profiles during rebind.
 */
public data class TargetFieldChange(val fieldName: String, val oldValue: Any?, val newValue: Any?)

/**
 * Explicit diff between two revisions of a target device profile.
 */
public data class TargetRebindDiff(
    val profileId: String,
    val oldRevision: Int,
    val newRevision: Int,
    val changedFields: Set<String>,
    val fieldChanges: List<TargetFieldChange> = emptyList(),
) {
    public val hasChanges: Boolean get() = changedFields.isNotEmpty()
}

private data class TargetFieldExtractor(val name: String, val extract: (TargetDevice) -> Any?)

private val TARGET_DEVICE_FIELDS: List<TargetFieldExtractor> = listOf(
    TargetFieldExtractor("name") { it.name },
    TargetFieldExtractor("codename") { it.codename },
    TargetFieldExtractor("revision") { it.revision },
    TargetFieldExtractor("availableRegions") { it.availableRegions },
    TargetFieldExtractor("availableFirmwares") { it.availableFirmwares },
    TargetFieldExtractor("socPlatform") { it.socPlatform },
    TargetFieldExtractor("filesystemType") { it.filesystemType },
    TargetFieldExtractor("superPartitionBytes") { it.superPartitionBytes },
    TargetFieldExtractor("superGroupBytes") { it.superGroupBytes },
    TargetFieldExtractor("bootPartitionBytes") { it.bootPartitionBytes },
    TargetFieldExtractor("vendorBootPartitionBytes") { it.vendorBootPartitionBytes },
    TargetFieldExtractor("initBootPartitionBytes") { it.initBootPartitionBytes },
    TargetFieldExtractor("dtboPartitionBytes") { it.dtboPartitionBytes },
    TargetFieldExtractor("dynamicPartitions") { it.dynamicPartitions },
    TargetFieldExtractor("bootPartitions") { it.bootPartitions },
    TargetFieldExtractor("status") { it.status },
    TargetFieldExtractor("description") { it.description },
    TargetFieldExtractor("isDefault") { it.isDefault },
    TargetFieldExtractor("productName") { it.productName },
    TargetFieldExtractor("fastbootProduct") { it.fastbootProduct },
    TargetFieldExtractor("assertModels") { it.assertModels },
    TargetFieldExtractor("superMetadataSlots") { it.superMetadataSlots },
    TargetFieldExtractor("superGroupName") { it.superGroupName },
    TargetFieldExtractor("virtualAb") { it.virtualAb },
    TargetFieldExtractor("activeSlotSuffix") { it.activeSlotSuffix },
    TargetFieldExtractor("hasStandaloneSystemExt") { it.hasStandaloneSystemExt },
    TargetFieldExtractor("bootDevicePath") { it.bootDevicePath },
    TargetFieldExtractor("packagePolicy") { it.packagePolicy },
)

/**
 * Computes an explicit field-by-field diff between two target device configurations.
 */
public fun diffTargets(old: TargetDevice, new: TargetDevice): TargetRebindDiff {
    val changes = TARGET_DEVICE_FIELDS.mapNotNull { extractor ->
        val oldVal = extractor.extract(old)
        val newVal = extractor.extract(new)
        if (oldVal != newVal) {
            TargetFieldChange(extractor.name, oldVal, newVal)
        } else {
            null
        }
    }
    return TargetRebindDiff(
        profileId = new.id.ifEmpty { old.id },
        oldRevision = old.revision,
        newRevision = new.revision,
        changedFields = changes.map { it.fieldName }.toSet(),
        fieldChanges = changes,
    )
}
