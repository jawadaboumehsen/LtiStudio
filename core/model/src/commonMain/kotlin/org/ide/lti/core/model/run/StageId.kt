/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.run

import kotlinx.serialization.Serializable

/**
 * Identifier for the eight distinct stages in the product-owned ROM pipeline.
 *
 * Eight IDs are declared; execution order is owned by the canonical pipeline sequence
 * ([PipelineStageSequence.ORDER]), never by ordinal.
 *
 * 1. [FIRMWARE_ACQUISITION] - Download or import stock firmware archive
 * 2. [FIRMWARE_EXTRACTION] - Extract payload / images and filesystem trees
 * 3. [WORK_TREE_ASSEMBLY] - Assemble unified work trees and patch props
 * 4. [DEBLOAT] - Remove/replace system packages per debloat preset
 * 5. [MODULE_APPLICATION] - Apply user/system module customizations
 * 6. [BUILD_FLASHABLE_ZIP] - Pack filesystem images and build signed OTA zip
 * 7. [GENERATE_OTA_MANIFEST] - Generate metadata manifest for distribution
 * 8. [PUBLISH_RELEASE] - Publish artifacts to a release channel
 */
@Serializable
public enum class StageId {
    FIRMWARE_ACQUISITION,
    FIRMWARE_EXTRACTION,
    WORK_TREE_ASSEMBLY,
    DEBLOAT,
    MODULE_APPLICATION,
    BUILD_FLASHABLE_ZIP,
    GENERATE_OTA_MANIFEST,
    PUBLISH_RELEASE,
}
