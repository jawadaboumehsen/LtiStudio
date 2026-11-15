/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.model.setup.PersistedResolvedInput
import org.ide.lti.core.model.setup.PersistedSetupPlan
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.core.model.setup.toDomain

/* The journaled form of a setup plan back to the domain plan a resume executes; a field that cannot be
 * rebuilt exactly (a missing frozen commit, an unknown action) refuses the resume rather than guess. */

internal fun PersistedSetupPlan.toDomainPlan(): SetupPlan = SetupPlan(
    planId = planId,
    revisionHash = revisionHash,
    environmentKey = environmentKey,
    kind = SetupPlanKind.valueOf(kind),
    targetStageOrToolId = targetStageOrToolId,
    orderedActions =
    actions.map { act ->
        when (act.type) {
            "INSTALL_PACKAGES" -> SetupPlanAction.InstallPackages(packages = act.payload)

            "SWITCH_TOOLCHAIN" -> {
                // Frozen inputs are persisted in the full PersistedPlanAction.switchInputs field.
                val persistedInputs =
                    act.switchInputs
                        ?: error("SWITCH_TOOLCHAIN is missing frozen source inputs")
                val inputs =
                    persistedInputs.map { (groupId, pinput) ->
                        val gid = org.ide.lti.core.domain.setup.ToolGroupId(groupId)
                        gid to pinput.toDomain(gid)
                    }.toMap()
                val rev =
                    act.selectionRevision
                        ?: error("SWITCH_TOOLCHAIN is missing its selection revision")
                val forceGroups =
                    act.forceRebuildGroups.orEmpty()
                        .map { org.ide.lti.core.domain.setup.ToolGroupId(it) }.toSet()
                SetupPlanAction.SwitchToolchain(
                    inputs = inputs,
                    selectionRevision = rev,
                    forceRebuildGroups = forceGroups,
                )
            }

            // The caller turns this into "invalid persisted plan": the attempt must be planned again.
            else -> throw IllegalArgumentException("Unknown persisted action type '${act.type}'")
        }
    },
)

@Suppress("CyclomaticComplexMethod")
internal fun PersistedResolvedInput.toDomain(groupId: ToolGroupId): ResolvedInput = when (kind) {
    "git" -> {
        val frozenCommit =
            commit
                ?: throw IllegalStateException("Git input '${groupId.value}' has no frozen commit")
        val frozenRef = ref ?: throw IllegalStateException("Git input '${groupId.value}' has no frozen ref")
        ResolvedInput.Git(
            group = groupId,
            repoUrl = repoUrl,
            ref =
            when {
                frozenRef.startsWith("tag:") -> ToolRef.Tag(frozenRef.removePrefix("tag:"))
                frozenRef.startsWith("branch:") -> ToolRef.Branch(frozenRef.removePrefix("branch:"))
                else -> ToolRef.Commit(frozenCommit)
            },
            commit = frozenCommit,
            submoduleCommits = submoduleCommits.orEmpty(),
            resolvedAt =
            resolvedAt
                ?: throw IllegalStateException("Git input '${groupId.value}' has no resolution timestamp"),
        )
    }

    "release" -> {
        val v = version ?: error("Release input '${groupId.value}' has no version")
        val u = url ?: error("Release input '${groupId.value}' has no URL")
        val m = archiveMember ?: error("Release input '${groupId.value}' has no archive member")
        val s = sha256 ?: error("Release input '${groupId.value}' has no checksum")
        val p = platform ?: error("Release input '${groupId.value}' has no platform")
        ResolvedInput.Release(
            group = groupId,
            version = v,
            url = u,
            archiveMember = m,
            sha256 = s,
            platform = p,
        )
    }

    else -> throw IllegalArgumentException("Unknown frozen input kind '$kind'")
}
