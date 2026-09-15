/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.plugin

import org.ide.lti.core.model.plugin.BytecodeMatchSpec
import org.ide.lti.core.model.plugin.PartitionPath

/**
 * Per-anchor classification of a [ConcreteOperation.HookInjection] match against the real,
 * decoded target component. This is the domain-side mirror of the desktop
 * `BytecodeMatcher.MatchOutcome` result (core:data, which feature:workspace cannot depend on) -
 * a real [AnchorHealthPort] implementation translates its matcher result into this type at the
 * port boundary, the same shape every other port/adapter pair in this codebase already uses.
 */
sealed interface AnchorFinding {
    data class Matched(val anchorStartLine: Int, val anchorEndLine: Int, val score: Double) : AnchorFinding

    data class NearMiss(
        val bestStartLine: Int,
        val score: Double,
        val divergingOpcodeIndex: Int,
        val expected: String,
        val actual: String,
    ) : AnchorFinding

    data class Ambiguous(val candidateStartLines: List<Int>) : AnchorFinding

    data object NotFound : AnchorFinding

    data class ComponentUnavailable(val reason: String) : AnchorFinding
}

/** One [ConcreteOperation.HookInjection]'s anchor, identified by its owning operation. */
data class AnchorCheck(
    val operationId: String,
    val target: PartitionPath,
    val classDescriptor: String,
    val match: BytecodeMatchSpec,
)

/** A package's HookInjection anchors to preflight, keyed the same way as [InstalledRecord.key]. */
data class PackageAnchors(val publisher: String, val id: String, val version: String, val checks: List<AnchorCheck>)

data class AnchorHealthEntry(val operationId: String, val classDescriptor: String, val finding: AnchorFinding)

/**
 * Resolves and matches one anchor against the real, decoded target component. A real
 * implementation owns partition-to-filesystem-path resolution, component decoding, and bytecode
 * matching end to end, so this port stays a single platform-hiding call.
 */
interface AnchorHealthPort {
    suspend fun checkAnchor(target: PartitionPath, classDescriptor: String, match: BytecodeMatchSpec): AnchorFinding
}

/**
 * Eagerly resolves every enabled package's HookInjection anchors against the pinned target, so
 * anchor health (matched / near-miss / ambiguous) is known before any build starts rather than
 * discovered mid-build. Callers invoke [preflight] at workspace open and again on target rebind;
 * this class does not itself observe either event.
 */
class AnchorHealthPreflight(private val port: AnchorHealthPort) {
    suspend fun preflight(packages: List<PackageAnchors>): Map<String, List<AnchorHealthEntry>> =
        packages.associate { pkg ->
            "${pkg.publisher}:${pkg.id}" to pkg.checks.map { check ->
                AnchorHealthEntry(
                    operationId = check.operationId,
                    classDescriptor = check.classDescriptor,
                    finding = port.checkAnchor(check.target, check.classDescriptor, check.match),
                )
            }
        }

    companion object {
        /** Extracts the anchor checks a package's resolved plan actually needs preflighted. */
        fun checksFor(plan: ConcreteMutationPlan): List<AnchorCheck> =
            plan.operations.filterIsInstance<ConcreteOperation.HookInjection>().map { op ->
                AnchorCheck(
                    operationId = op.id,
                    target = op.target,
                    classDescriptor = op.match.classDescriptor,
                    match = op.match,
                )
            }
    }
}
