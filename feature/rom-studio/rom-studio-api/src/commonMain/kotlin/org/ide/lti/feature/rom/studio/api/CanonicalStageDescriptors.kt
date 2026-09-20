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

import org.ide.lti.core.model.run.StageId

/**
 * Canonical contract descriptors for all eight ROM Setup Studio pipeline stages.
 */
public object CanonicalStageDescriptors {

    public val ACQUIRE: StageDescriptor = StageDescriptor(
        stageId = StageId.FIRMWARE_ACQUISITION,
        title = "Acquire",
        iconKey = StudioIconKey.ACQUIRE,
        subobjects = listOf(
            StudioSubobject(StudioSubobjectId("source"), "Source"),
            StudioSubobject(StudioSubobjectId("firmware-baseline"), "Firmware baseline"),
            StudioSubobject(StudioSubobjectId("existing-archives"), "Existing archives"),
            StudioSubobject(StudioSubobjectId("download-policy"), "Download policy"),
            StudioSubobject(StudioSubobjectId("integrity"), "Integrity"),
        ),
    )

    public val EXTRACT: StageDescriptor = StageDescriptor(
        stageId = StageId.FIRMWARE_EXTRACTION,
        title = "Extract",
        iconKey = StudioIconKey.EXTRACT,
        subobjects = listOf(
            StudioSubobject(StudioSubobjectId("archive-layout"), "Archive layout"),
            StudioSubobject(StudioSubobjectId("dynamic-partitions"), "Dynamic partitions"),
            StudioSubobject(StudioSubobjectId("boot-partitions"), "Boot partitions"),
            StudioSubobject(StudioSubobjectId("slot"), "Slot"),
            StudioSubobject(StudioSubobjectId("filesystem-handling"), "Filesystem handling"),
        ),
    )

    public val ASSEMBLE: StageDescriptor = StageDescriptor(
        stageId = StageId.WORK_TREE_ASSEMBLY,
        title = "Assemble",
        iconKey = StudioIconKey.ASSEMBLE,
        subobjects = listOf(
            StudioSubobject(StudioSubobjectId("work-trees"), "Work trees"),
            StudioSubobject(StudioSubobjectId("system-ext-handling"), "system_ext handling"),
            StudioSubobject(StudioSubobjectId("boot-preparation"), "Boot preparation"),
            StudioSubobject(StudioSubobjectId("aot-cleanup"), "AOT cleanup"),
            StudioSubobject(StudioSubobjectId("build-properties"), "Build properties"),
        ),
    )

    public val DEBLOAT: StageDescriptor = StageDescriptor(
        stageId = StageId.DEBLOAT,
        title = "Debloat",
        iconKey = StudioIconKey.DEBLOAT,
        subobjects = listOf(
            StudioSubobject(StudioSubobjectId("inventory"), "Inventory"),
            StudioSubobject(StudioSubobjectId("presets"), "Presets"),
            StudioSubobject(StudioSubobjectId("remove-selections"), "Remove selections"),
            StudioSubobject(StudioSubobjectId("keep-rules"), "Keep rules"),
            StudioSubobject(StudioSubobjectId("risk-review"), "Risk review"),
            StudioSubobject(StudioSubobjectId("results"), "Results"),
        ),
    )

    public val PATCH: StageDescriptor = StageDescriptor(
        stageId = StageId.MODULE_APPLICATION,
        title = "Patches and Mods",
        iconKey = StudioIconKey.PATCH,
        subobjects = listOf(
            StudioSubobject(StudioSubobjectId("module-catalog"), "Module catalog"),
            StudioSubobject(StudioSubobjectId("enabled-ordered-modules"), "Enabled ordered modules"),
            StudioSubobject(StudioSubobjectId("file-changes"), "File changes"),
            StudioSubobject(StudioSubobjectId("text-patches"), "Text patches"),
            StudioSubobject(StudioSubobjectId("property-patches"), "Property patches"),
            StudioSubobject(StudioSubobjectId("conflicts"), "Conflicts"),
        ),
    )

    public val BUILD: StageDescriptor = StageDescriptor(
        stageId = StageId.BUILD_FLASHABLE_ZIP,
        title = "Build and Sign",
        iconKey = StudioIconKey.BUILD,
        subobjects = listOf(
            StudioSubobject(StudioSubobjectId("images"), "Images"),
            StudioSubobject(StudioSubobjectId("compression"), "Compression"),
            StudioSubobject(StudioSubobjectId("partition-layout"), "Partition layout"),
            StudioSubobject(StudioSubobjectId("flashable-members"), "Flashable members"),
            StudioSubobject(StudioSubobjectId("avb-keys"), "AVB keys"),
            StudioSubobject(StudioSubobjectId("package-signing"), "Package signing"),
            StudioSubobject(StudioSubobjectId("output-naming"), "Output naming"),
        ),
    )

    public val METADATA: StageDescriptor = StageDescriptor(
        stageId = StageId.GENERATE_OTA_MANIFEST,
        title = "Release Metadata",
        iconKey = StudioIconKey.METADATA,
        subobjects = listOf(
            StudioSubobject(StudioSubobjectId("release-identity"), "Release identity"),
            StudioSubobject(StudioSubobjectId("ota-url"), "OTA URL"),
            StudioSubobject(StudioSubobjectId("changelog"), "Changelog"),
            StudioSubobject(StudioSubobjectId("compatibility"), "Compatibility"),
            StudioSubobject(StudioSubobjectId("manifest-preview"), "Manifest preview"),
        ),
    )

    public val PUBLISH: StageDescriptor = StageDescriptor(
        stageId = StageId.PUBLISH_RELEASE,
        title = "Publish ROM",
        iconKey = StudioIconKey.PUBLISH,
        subobjects = listOf(
            StudioSubobject(StudioSubobjectId("provider"), "Provider"),
            StudioSubobject(StudioSubobjectId("release-repository-and-tag"), "Release repository and tag"),
            StudioSubobject(StudioSubobjectId("split-assets"), "Split assets"),
            StudioSubobject(StudioSubobjectId("credentials"), "Credentials"),
            StudioSubobject(StudioSubobjectId("ota-manifest"), "OTA manifest"),
            StudioSubobject(StudioSubobjectId("retention"), "Retention"),
            StudioSubobject(StudioSubobjectId("verification"), "Verification"),
            StudioSubobject(StudioSubobjectId("publish-review-and-recovery"), "Publish review and recovery"),
        ),
    )

    public val ALL: List<StageDescriptor> = listOf(
        ACQUIRE,
        EXTRACT,
        ASSEMBLE,
        DEBLOAT,
        PATCH,
        BUILD,
        METADATA,
        PUBLISH,
    )

    private val byStageId: Map<StageId, StageDescriptor> = ALL.associateBy { it.stageId }

    public fun descriptorFor(stageId: StageId): StageDescriptor? = byStageId[stageId]

    public fun owningStage(subobjectId: StudioSubobjectId): StageDescriptor? =
        ALL.firstOrNull { it.subobject(subobjectId) != null }

    public fun subobject(subobjectId: StudioSubobjectId): StudioSubobject? =
        ALL.firstNotNullOfOrNull { it.subobject(subobjectId) }
}
