/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.ports

/**
 * Diagnostic categorization for toolchain compilation and compatibility failures.
 */
public enum class ToolchainFailureCategory {
    MISSING_BINARIES,
    MISSING_PUBLISHED_TOOLS,
    EROFS_INCOMPATIBLE,
}
