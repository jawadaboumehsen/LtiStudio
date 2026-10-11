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

import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.model.setup.PersistedPlanAction
import org.ide.lti.core.model.setup.PersistedResolvedInput
import org.ide.lti.core.model.setup.PersistedSetupPlan
import org.ide.lti.core.model.setup.ToolRef

public fun SetupPlanAction.toPersistedPlanAction(): PersistedPlanAction = when (this) {
    is SetupPlanAction.InstallPackages ->
        PersistedPlanAction(
            actionId = actionId(),
            type = "INSTALL_PACKAGES",
            payload = packages,
        )
    is SetupPlanAction.SwitchToolchain ->
        PersistedPlanAction(
            actionId = actionId(),
            type = "SWITCH_TOOLCHAIN",
            payload = inputs.keys.map { it.value },
            switchInputs =
            inputs.mapKeys { it.key.value }.mapValues { (_, input) ->
                when (input) {
                    is ResolvedInput.Git ->
                        PersistedResolvedInput(
                            kind = "git",
                            repoUrl = input.repoUrl,
                            ref =
                            when (val r = input.ref) {
                                is ToolRef.Tag -> "tag:${r.name}"
                                is ToolRef.Branch -> "branch:${r.name}"
                                is ToolRef.Commit -> r.sha
                                is ToolRef.ReleaseVersion -> r.version
                            },
                            commit = input.commit,
                            submoduleCommits = input.submoduleCommits,
                            resolvedAt = input.resolvedAt,
                        )
                    is ResolvedInput.Release ->
                        PersistedResolvedInput(
                            kind = "release",
                            version = input.version,
                            url = input.url,
                            archiveMember = input.archiveMember,
                            sha256 = input.sha256,
                            platform = input.platform,
                        )
                }
            },
            selectionRevision = selectionRevision,
            forceRebuildGroups = forceRebuildGroups.map { it.value },
        )
}

public fun SetupPlan.toPersistedSetupPlan(): PersistedSetupPlan = PersistedSetupPlan(
    planId = planId,
    revisionHash = revisionHash,
    environmentKey = environmentKey,
    kind = kind.name,
    targetStageOrToolId = targetStageOrToolId,
    actions = orderedActions.map { it.toPersistedPlanAction() },
)
