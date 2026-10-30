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

/** Where a boot-chain image edited in the Studio goes; the Assemble stage copies it into the build (FR-020..FR-026). */
public object KernelOverrides {
    public fun path(partition: String): String = "overrides/kernel/$partition.img"
}
