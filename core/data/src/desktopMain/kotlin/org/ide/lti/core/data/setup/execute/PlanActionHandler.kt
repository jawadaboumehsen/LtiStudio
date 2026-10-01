/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.execute

import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanAction

/**
 * Unique identifier for plan actions in journal records and execution tracking.
 */
public fun SetupPlanAction.actionId(): String = when (this) {
    is SetupPlanAction.InstallPackages -> "packages"
    is SetupPlanAction.SyncSources -> "sync-sources"
    is SetupPlanAction.BuildRecipes -> "build-recipes"
    is SetupPlanAction.PublishTools -> "publish-tools"
    is SetupPlanAction.SwitchToolchain -> "switch-toolchain"
}

/**
 * Handler interface for executing an individual [SetupPlanAction] (T031).
 */
public interface PlanActionHandler<A : SetupPlanAction> {
    public suspend fun execute(action: A, context: ExecutionContext): SetupOutcome?
}
