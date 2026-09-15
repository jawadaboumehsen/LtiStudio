/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.ide.lti.core.designsystem.icon.AppIconsRomStages
import org.ide.lti.core.model.run.StageId

/** A left-panel object inside a stage. [accessibleLabel] defaults to the visible title. */
public data class StudioSubobject(val id: String, val title: String, val accessibleLabel: String = title)

public data class StudioStageDestination(
    val stageId: StageId,
    val position: Int,
    val title: String,
    val accessibleLabel: String = title,
    val subobjects: List<StudioSubobject>,
    val icon: @Composable () -> Painter,
)

/**
 * Immutable destination catalog for ROM Setup Studio.
 *
 * Contains exactly the eight contract-defined stages in pipeline order.
 *
 * Note on architectural boundaries:
 * - The Overview is NOT in this list: it is a center-panel presentation mode, NEVER a ninth stage.
 * - Machine Setup/Tools and Plugin Manager are separate host-level destinations and MUST NOT appear here.
 */
public object StudioDestinationCatalog {

    public val stages: List<StudioStageDestination> = listOf(
        StudioStageDestination(
            stageId = StageId.FIRMWARE_ACQUISITION,
            position = 1,
            title = "Acquire",
            icon = AppIconsRomStages.acquire,
            subobjects = listOf(
                StudioSubobject("source", "Source"),
                StudioSubobject("firmware-baseline", "Firmware baseline"),
                StudioSubobject("existing-archives", "Existing archives"),
                StudioSubobject("download-policy", "Download policy"),
                StudioSubobject("integrity", "Integrity"),
            ),
        ),
        StudioStageDestination(
            stageId = StageId.FIRMWARE_EXTRACTION,
            position = 2,
            title = "Extract",
            icon = AppIconsRomStages.extract,
            subobjects = listOf(
                StudioSubobject("archive-layout", "Archive layout"),
                StudioSubobject("dynamic-partitions", "Dynamic partitions"),
                StudioSubobject("boot-partitions", "Boot partitions"),
                StudioSubobject("slot", "Slot"),
                StudioSubobject("filesystem-handling", "Filesystem handling"),
            ),
        ),
        StudioStageDestination(
            stageId = StageId.WORK_TREE_ASSEMBLY,
            position = 3,
            title = "Assemble",
            icon = AppIconsRomStages.assemble,
            subobjects = listOf(
                StudioSubobject("work-trees", "Work trees"),
                StudioSubobject("system-ext-handling", "system_ext handling"),
                StudioSubobject("boot-preparation", "Boot preparation"),
                StudioSubobject("aot-cleanup", "AOT cleanup"),
                StudioSubobject("build-properties", "Build properties"),
            ),
        ),
        StudioStageDestination(
            stageId = StageId.DEBLOAT,
            position = 4,
            title = "Debloat",
            icon = AppIconsRomStages.debloat,
            subobjects = listOf(
                StudioSubobject("inventory", "Inventory"),
                StudioSubobject("presets", "Presets"),
                StudioSubobject("remove-selections", "Remove selections"),
                StudioSubobject("keep-rules", "Keep rules"),
                StudioSubobject("risk-review", "Risk review"),
                StudioSubobject("results", "Results"),
            ),
        ),
        StudioStageDestination(
            stageId = StageId.MODULE_APPLICATION,
            position = 5,
            title = "Patches and Mods",
            icon = AppIconsRomStages.patches,
            subobjects = listOf(
                StudioSubobject("module-catalog", "Module catalog"),
                StudioSubobject("enabled-ordered-modules", "Enabled ordered modules"),
                StudioSubobject("file-changes", "File changes"),
                StudioSubobject("text-patches", "Text patches"),
                StudioSubobject("property-patches", "Property patches"),
                StudioSubobject("conflicts", "Conflicts"),
            ),
        ),
        StudioStageDestination(
            stageId = StageId.BUILD_FLASHABLE_ZIP,
            position = 6,
            title = "Build and Sign",
            icon = AppIconsRomStages.build,
            subobjects = listOf(
                StudioSubobject("images", "Images"),
                StudioSubobject("compression", "Compression"),
                StudioSubobject("partition-layout", "Partition layout"),
                StudioSubobject("flashable-members", "Flashable members"),
                StudioSubobject("avb-keys", "AVB keys"),
                StudioSubobject("package-signing", "Package signing"),
                StudioSubobject("output-naming", "Output naming"),
            ),
        ),
        StudioStageDestination(
            stageId = StageId.GENERATE_OTA_MANIFEST,
            position = 7,
            title = "Release Metadata",
            icon = AppIconsRomStages.releaseMetadata,
            subobjects = listOf(
                StudioSubobject("release-identity", "Release identity"),
                StudioSubobject("ota-url", "OTA URL"),
                StudioSubobject("changelog", "Changelog"),
                StudioSubobject("compatibility", "Compatibility"),
                StudioSubobject("manifest-preview", "Manifest preview"),
            ),
        ),
        StudioStageDestination(
            stageId = StageId.PUBLISH_RELEASE,
            position = 8,
            title = "Publish ROM",
            icon = AppIconsRomStages.publish,
            subobjects = listOf(
                StudioSubobject("provider", "Provider"),
                StudioSubobject("release-repository-and-tag", "Release repository and tag"),
                StudioSubobject("split-assets", "Split assets"),
                StudioSubobject("credentials", "Credentials"),
                StudioSubobject("ota-manifest", "OTA manifest"),
                StudioSubobject("retention", "Retention"),
                StudioSubobject("verification", "Verification"),
                StudioSubobject("publish-review-and-recovery", "Publish review and recovery"),
            ),
        ),
    )

    private val stagesById: Map<StageId, StudioStageDestination> = stages.associateBy { it.stageId }

    private val stagesByObjectId: Map<String, StudioStageDestination> = stages.flatMap { stage ->
        stage.subobjects.map { subobject -> subobject.id to stage }
    }.toMap()

    private val subobjectsById: Map<String, StudioSubobject> = stages.flatMap { it.subobjects }
        .associateBy { it.id }

    public fun stage(id: StageId): StudioStageDestination? = stagesById[id]

    public fun owningStage(objectId: String): StudioStageDestination? = stagesByObjectId[objectId]

    public fun subobject(objectId: String): StudioSubobject? = subobjectsById[objectId]
}
