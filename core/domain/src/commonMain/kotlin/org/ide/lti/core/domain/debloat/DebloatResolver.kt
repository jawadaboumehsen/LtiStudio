/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.debloat

import kotlinx.serialization.Serializable
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.DebloatSettings

public const val MALFORMED_SELECTOR: String = "MALFORMED_SELECTOR"
public const val UNMATCHED_SELECTOR: String = "UNMATCHED_SELECTOR"
public const val PARTITION_NOT_IN_TARGET: String = "PARTITION_NOT_IN_TARGET"
public const val PROTECTED_ENTRY: String = "PROTECTED_ENTRY"

@Serializable
public data class DebloatCandidate(
    val partition: String,
    val relativePath: String,
    val sizeBytes: Long,
    val packageId: String?,
)

@Serializable
public enum class RemovalReason {
    PRESET,
    EXPLICIT_SELECTOR,
}

@Serializable
public data class DebloatResolvedRemoval(
    val partition: String,
    val relativePath: String,
    val sizeBytes: Long,
    val packageId: String?,
    val reason: RemovalReason,
    val sidecarEntries: List<String>,
)

@Serializable
public enum class KeepReason {
    KEEP_SELECTOR,
    PROTECTED_POLICY,
    UNKNOWN_RISK,
}

@Serializable
public data class KeptEntry(val partition: String, val relativePath: String, val reason: KeepReason)

/**
 * Resolved debloat decision.
 *
 * @property removals Resolved list of candidate removals sorted deterministically by partition, then relativePath.
 * @property kept Entries kept despite candidate matches or explicitly protected.
 * @property logicalBytesFreed The sum of removed file sizes documented strictly as logical file bytes,
 *   not rebuilt-image savings.
 */
@Serializable
public data class DebloatDecision(
    val removals: List<DebloatResolvedRemoval>,
    val kept: List<KeptEntry>,
    val logicalBytesFreed: Long,
)

public sealed interface DebloatResolution {
    public data class Resolved(val decision: DebloatDecision) : DebloatResolution
    public data class Rejected(val report: ValidationReport) : DebloatResolution
}

public object DebloatResolver {
    public fun resolve(
        settings: DebloatSettings,
        inventory: List<DebloatCandidate>,
        presetSelectors: List<String>,
        protectedSelectors: List<String>,
        targetPartitions: Set<String>,
    ): DebloatResolution = org.ide.lti.core.domain.debloat.resolve(
        settings = settings,
        inventory = inventory,
        presetSelectors = presetSelectors,
        protectedSelectors = protectedSelectors,
        targetPartitions = targetPartitions,
    )
}

@Suppress("ReturnCount")
public fun resolve(
    settings: DebloatSettings,
    inventory: List<DebloatCandidate>,
    presetSelectors: List<String>,
    protectedSelectors: List<String>,
    targetPartitions: Set<String>,
): DebloatResolution {
    if (!settings.enabled) {
        return DebloatResolution.Resolved(
            DebloatDecision(
                removals = emptyList(),
                kept = emptyList(),
                logicalBytesFreed = 0L,
            ),
        )
    }

    val errors = mutableListOf<ValidationError>()
    collectSelectorErrors(settings, inventory, presetSelectors, protectedSelectors, targetPartitions, errors)
    collectProtectedErrors(settings, inventory, presetSelectors, protectedSelectors, errors)

    if (errors.isNotEmpty()) {
        return DebloatResolution.Rejected(ValidationReport(errors = errors))
    }

    val decision = buildDecision(settings, inventory, presetSelectors)
    return DebloatResolution.Resolved(decision)
}

private sealed interface SelectorKind {
    data class Package(val packageId: String) : SelectorKind
    data class PartitionPath(val partition: String, val relativePath: String) : SelectorKind
}

private fun candidateMatches(kind: SelectorKind, candidate: DebloatCandidate): Boolean = when (kind) {
    is SelectorKind.Package -> candidate.packageId == kind.packageId
    is SelectorKind.PartitionPath ->
        candidate.partition == kind.partition && candidate.relativePath == kind.relativePath
}

@Suppress("ReturnCount")
private fun validateSelectorFormat(selector: String, errors: MutableList<ValidationError>): SelectorKind? {
    if (selector.startsWith("pkg:")) {
        val pkg = selector.removePrefix("pkg:")
        if (pkg.isEmpty() || !isValidPackageId(pkg)) {
            errors.add(
                ValidationError(
                    stageId = StageId.DEBLOAT,
                    objectId = null,
                    fieldPath = selector,
                    code = MALFORMED_SELECTOR,
                    severity = Severity.ERROR,
                    message = "Selector '$selector' is malformed; invalid package identifier",
                ),
            )
            return null
        }
        return SelectorKind.Package(pkg)
    }

    val colonIdx = selector.indexOf(':')
    if (colonIdx <= 0 || colonIdx == selector.length - 1) {
        errors.add(
            ValidationError(
                stageId = StageId.DEBLOAT,
                objectId = null,
                fieldPath = selector,
                code = MALFORMED_SELECTOR,
                severity = Severity.ERROR,
                message = "Selector '$selector' is malformed; expected 'partition:relativePath' or 'pkg:<id>'",
            ),
        )
        return null
    }

    val partition = selector.substring(0, colonIdx)
    val relPath = selector.substring(colonIdx + 1)
    if (!isValidPartitionName(partition) || !isValidRelativePath(relPath)) {
        errors.add(
            ValidationError(
                stageId = StageId.DEBLOAT,
                objectId = null,
                fieldPath = selector,
                code = MALFORMED_SELECTOR,
                severity = Severity.ERROR,
                message = "Selector '$selector' is malformed; invalid partition or relative path syntax",
            ),
        )
        return null
    }

    return SelectorKind.PartitionPath(partition, relPath)
}

private fun collectSelectorErrors(
    settings: DebloatSettings,
    inventory: List<DebloatCandidate>,
    presetSelectors: List<String>,
    protectedSelectors: List<String>,
    targetPartitions: Set<String>,
    errors: MutableList<ValidationError>,
) {
    for (sel in protectedSelectors) {
        validateSelectorFormat(sel, errors)
    }

    val activeSelectors = (presetSelectors + settings.removeSelectors + settings.keepSelectors).distinct()
    for (sel in activeSelectors) {
        val kind = validateSelectorFormat(sel, errors)
        if (kind != null) {
            if (kind is SelectorKind.PartitionPath && kind.partition !in targetPartitions) {
                errors.add(
                    ValidationError(
                        stageId = StageId.DEBLOAT,
                        objectId = null,
                        fieldPath = sel,
                        code = PARTITION_NOT_IN_TARGET,
                        severity = Severity.ERROR,
                        message = "Selector partition '${kind.partition}' not in target partitions $targetPartitions",
                    ),
                )
            } else if (inventory.none { candidateMatches(kind, it) }) {
                errors.add(
                    ValidationError(
                        stageId = StageId.DEBLOAT,
                        objectId = null,
                        fieldPath = sel,
                        code = UNMATCHED_SELECTOR,
                        severity = Severity.ERROR,
                        message = "Selector '$sel' matched no entries in the debloat inventory",
                    ),
                )
            }
        }
    }
}

private fun collectProtectedErrors(
    settings: DebloatSettings,
    inventory: List<DebloatCandidate>,
    presetSelectors: List<String>,
    protectedSelectors: List<String>,
    errors: MutableList<ValidationError>,
) {
    val dummyErrors = mutableListOf<ValidationError>()
    val keepKinds = settings.keepSelectors.mapNotNull { validateSelectorFormat(it, dummyErrors) }
    val removeExplicitKinds = settings.removeSelectors.mapNotNull { validateSelectorFormat(it, dummyErrors) }
    val presetKinds = presetSelectors.mapNotNull { validateSelectorFormat(it, dummyErrors) }
    val protectedKinds = protectedSelectors.mapNotNull { validateSelectorFormat(it, dummyErrors) }

    for (candidate in inventory) {
        val isRemoval = removeExplicitKinds.any { candidateMatches(it, candidate) } ||
            presetKinds.any { candidateMatches(it, candidate) }
        val isKept = keepKinds.any { candidateMatches(it, candidate) }
        if (isRemoval && !isKept) {
            val isProtected = protectedKinds.any { candidateMatches(it, candidate) }
            if (isProtected) {
                errors.add(
                    ValidationError(
                        stageId = StageId.DEBLOAT,
                        objectId = null,
                        fieldPath = "${candidate.partition}:${candidate.relativePath}",
                        code = PROTECTED_ENTRY,
                        severity = Severity.ERROR,
                        message = "Protected entry '${candidate.partition}:${candidate.relativePath}' " +
                            "cannot be removed",
                    ),
                )
            }
        }
    }
}

private fun buildDecision(
    settings: DebloatSettings,
    inventory: List<DebloatCandidate>,
    presetSelectors: List<String>,
): DebloatDecision {
    val dummyErrors = mutableListOf<ValidationError>()
    val keepKinds = settings.keepSelectors.mapNotNull { validateSelectorFormat(it, dummyErrors) }
    val removeExplicitKinds = settings.removeSelectors.mapNotNull { validateSelectorFormat(it, dummyErrors) }
    val presetKinds = presetSelectors.mapNotNull { validateSelectorFormat(it, dummyErrors) }

    val removals = mutableListOf<DebloatResolvedRemoval>()
    val kept = mutableListOf<KeptEntry>()

    for (candidate in inventory) {
        if (keepKinds.any { candidateMatches(it, candidate) }) {
            kept.add(
                KeptEntry(
                    partition = candidate.partition,
                    relativePath = candidate.relativePath,
                    reason = KeepReason.KEEP_SELECTOR,
                ),
            )
        } else if (removeExplicitKinds.any { candidateMatches(it, candidate) }) {
            removals.add(
                DebloatResolvedRemoval(
                    partition = candidate.partition,
                    relativePath = candidate.relativePath,
                    sizeBytes = candidate.sizeBytes,
                    packageId = candidate.packageId,
                    reason = RemovalReason.EXPLICIT_SELECTOR,
                    sidecarEntries = emptyList(),
                ),
            )
        } else if (presetKinds.any { candidateMatches(it, candidate) }) {
            removals.add(
                DebloatResolvedRemoval(
                    partition = candidate.partition,
                    relativePath = candidate.relativePath,
                    sizeBytes = candidate.sizeBytes,
                    packageId = candidate.packageId,
                    reason = RemovalReason.PRESET,
                    sidecarEntries = emptyList(),
                ),
            )
        }
    }

    val sortedRemovals = removals.sortedWith(compareBy({ it.partition }, { it.relativePath }))
    val sortedKept = kept.sortedWith(compareBy({ it.partition }, { it.relativePath }))
    val logicalBytesFreed = sortedRemovals.sumOf { it.sizeBytes }

    return DebloatDecision(
        removals = sortedRemovals,
        kept = sortedKept,
        logicalBytesFreed = logicalBytesFreed,
    )
}

private fun isValidPackageId(pkg: String): Boolean =
    pkg.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' } &&
        !pkg.any { it.isWhitespace() || it in "*?[]{}()$;&|'\"`\\~<>" }

private fun isValidPartitionName(partition: String): Boolean =
    partition.isNotEmpty() && partition.all { it.isLetterOrDigit() || it == '_' || it == '-' }

private fun isValidRelativePath(path: String): Boolean = path.isNotEmpty() &&
    !path.startsWith('/') &&
    !path.endsWith('/') &&
    !path.contains('\\') &&
    !path.any { it.isWhitespace() || it in "*?[]{}()$;&|'\"`~<>" } &&
    path.split('/').none { it.isEmpty() || it == "." || it == ".." }
