/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.target

/**
 * A single target-configuration validation failure.
 */
public sealed class TargetValidationError(public val field: String, public val message: String) {
    public object EmptyId : TargetValidationError("id", "Target ID cannot be blank.")
    public object EmptyName : TargetValidationError("name", "Target name cannot be blank.")
    public object EmptyCodename : TargetValidationError("codename", "Target codename cannot be blank.")
    public object EmptySocPlatform : TargetValidationError("socPlatform", "SoC platform cannot be blank.")
    public object EmptyFilesystemType : TargetValidationError("filesystemType", "Filesystem type cannot be blank.")
    public object InvalidSuperPartitionSize :
        TargetValidationError("superPartitionBytes", "Super partition size must be greater than zero.")
    public object EmptyDynamicPartitions :
        TargetValidationError("dynamicPartitions", "At least one dynamic partition is required.")
    public object EmptyBootPartitions :
        TargetValidationError("bootPartitions", "At least one boot partition is required.")
}
