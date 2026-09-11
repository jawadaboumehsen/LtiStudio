package org.ide.lti.core.model.run

import kotlinx.serialization.Serializable

/**
 * Identifier for the six distinct stages in the product-owned ROM pipeline.
 *
 * Declared in strict pipeline execution order (ordinal = order):
 * 1. [FIRMWARE_ACQUISITION] - Download or import stock firmware archive
 * 2. [FIRMWARE_EXTRACTION] - Extract payload / images and filesystem trees
 * 3. [WORK_TREE_ASSEMBLY] - Assemble unified work trees and patch props
 * 4. [MODULE_APPLICATION] - Apply user/system module customizations
 * 5. [BUILD_FLASHABLE_ZIP] - Pack filesystem images and build signed OTA zip
 * 6. [GENERATE_OTA_MANIFEST] - Generate metadata manifest for distribution
 */
@Serializable
public enum class StageId {
    FIRMWARE_ACQUISITION,
    FIRMWARE_EXTRACTION,
    WORK_TREE_ASSEMBLY,
    MODULE_APPLICATION,
    BUILD_FLASHABLE_ZIP,
    GENERATE_OTA_MANIFEST
}
