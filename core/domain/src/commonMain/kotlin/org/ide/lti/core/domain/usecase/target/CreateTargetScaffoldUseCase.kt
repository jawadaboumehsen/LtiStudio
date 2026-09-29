/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.usecase.target

import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetStatus

/**
 * Use case to scaffold a new, unsaved target device profile for the "add target" flow.
 *
 * No surviving spec for scaffold defaults; reuses [DefaultTargetCatalog.PQ84P01_DEFAULT] as the
 * starting template (the same fallback [org.ide.lti.core.data.repository.target.TargetRepositoryImpl]
 * already uses), with a fresh ID and blank identifying fields.
 */
class CreateTargetScaffoldUseCase {
    operator fun invoke(index: Int) = DefaultTargetCatalog.PQ84P01_DEFAULT.copy(
        id = "TARGET_NEW_$index",
        name = "New Target $index",
        codename = "target_$index",
        status = TargetStatus.EXPERIMENTAL,
        isDefault = false,
        description = "Unconfigured custom target profile.",
    )
}
