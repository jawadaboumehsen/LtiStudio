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

import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.plugin.DependencyRef
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.PluginLockfile
import org.ide.lti.core.model.workspace.PluginLockfileEntry

data class InstalledPackage(
    val publisher: String,
    val id: String,
    val version: String,
    val contentDigest: String,
    val dependencies: List<DependencyRef>,
    val conflicts: List<DependencyRef>,
)

val InstalledPackage.key: String
    get() = "$publisher:$id"

sealed interface LockResult {
    data class Locked(val lockfile: PluginLockfile) : LockResult
    data class Rejected(val report: ValidationReport) : LockResult
}

object PluginDependencyResolver {
    const val UNKNOWN_DEPENDENCY: String = "UNKNOWN_DEPENDENCY"
    const val VERSION_OUT_OF_RANGE: String = "VERSION_OUT_OF_RANGE"
    const val DEPENDENCY_CYCLE: String = "DEPENDENCY_CYCLE"
    const val CONFLICTING_PACKAGES: String = "CONFLICTING_PACKAGES"
    const val ORDER_VIOLATES_DEPENDENCY: String = "ORDER_VIOLATES_DEPENDENCY"
    const val DUPLICATE_ENABLED_ID: String = "DUPLICATE_ENABLED_ID"
    const val MALFORMED_VERSION_RANGE: String = "MALFORMED_VERSION_RANGE"
    const val MALFORMED_VERSION: String = "MALFORMED_VERSION"

    fun lock(enabledIds: List<String>, installed: List<InstalledPackage>, requestedOrder: List<String>): LockResult =
        ResolverRun(enabledIds, installed, requestedOrder).execute()
}

private class ResolverRun(
    private val enabledIds: List<String>,
    private val installed: List<InstalledPackage>,
    private val requestedOrder: List<String>,
) {
    private val errors = mutableListOf<ValidationError>()
    private val installedByKey = installed.groupBy { it.key }
    private val parsedPackageVersions = mutableMapOf<InstalledPackage, ParsedVersion>()

    fun execute(): LockResult {
        checkDuplicateEnabledIds()
        validateInstalledVersions()

        val resolved = mutableMapOf<String, InstalledPackage>()
        val dependencyEdges = mutableListOf<Pair<String, String>>()

        resolveGraph(resolved, dependencyEdges)
        checkConflicts(resolved)
        checkCycles(resolved, dependencyEdges)
        checkRequestedOrder(resolved, dependencyEdges)

        if (errors.isNotEmpty()) {
            return LockResult.Rejected(ValidationReport(errors = errors))
        }

        val sortedKeys = topologicalSort(resolved.keys, dependencyEdges)
        val entries = sortedKeys.map { key ->
            val pkg = resolved.getValue(key)
            PluginLockfileEntry(
                publisher = pkg.publisher,
                id = pkg.id,
                version = pkg.version,
                contentDigest = pkg.contentDigest,
            )
        }
        val edges = dependencyEdges.map { "${it.first}->${it.second}" }.distinct().sorted()

        return LockResult.Locked(
            PluginLockfile(
                schemaVersion = 1,
                entries = entries,
                edges = edges,
            ),
        )
    }

    private fun checkDuplicateEnabledIds() {
        val seen = mutableSetOf<String>()
        for (id in enabledIds) {
            if (!seen.add(id)) {
                error(id, "enabledIds", PluginDependencyResolver.DUPLICATE_ENABLED_ID, "Duplicate enabled id: '$id'")
            }
        }
    }

    private fun validateInstalledVersions() {
        for (pkg in installed) {
            when (val parsed = parseVersion(pkg.version)) {
                is VersionParseResult.Success -> parsedPackageVersions[pkg] = parsed.version
                is VersionParseResult.Failure -> {
                    error(
                        pkg.key,
                        "version",
                        PluginDependencyResolver.MALFORMED_VERSION,
                        "Installed package '${pkg.key}' has malformed version '${pkg.version}': ${parsed.reason}",
                    )
                }
            }
        }
    }

    private fun resolveGraph(
        resolved: MutableMap<String, InstalledPackage>,
        dependencyEdges: MutableList<Pair<String, String>>,
    ) {
        val queue = ArrayDeque<String>()

        for (enabledId in enabledIds.distinct()) {
            val candidates = installedByKey[enabledId].orEmpty()
            if (candidates.isEmpty()) {
                error(
                    enabledId,
                    "enabledIds",
                    PluginDependencyResolver.UNKNOWN_DEPENDENCY,
                    "Enabled package '$enabledId' is not installed",
                )
            } else {
                val selected = selectLatest(candidates)
                if (selected != null) {
                    resolved[enabledId] = selected
                    queue.add(enabledId)
                }
            }
        }

        while (queue.isNotEmpty()) {
            val currentKey = queue.removeFirst()
            resolveQueueEntry(currentKey, resolved, dependencyEdges, queue)
        }
    }

    private fun resolveQueueEntry(
        currentKey: String,
        resolved: MutableMap<String, InstalledPackage>,
        dependencyEdges: MutableList<Pair<String, String>>,
        queue: ArrayDeque<String>,
    ) {
        val currentPkg = resolved[currentKey] ?: return
        for (dep in currentPkg.dependencies) {
            resolveSingleDependency(currentKey, dep, resolved, dependencyEdges, queue)
        }
    }

    private fun resolveSingleDependency(
        currentKey: String,
        dep: DependencyRef,
        resolved: MutableMap<String, InstalledPackage>,
        dependencyEdges: MutableList<Pair<String, String>>,
        queue: ArrayDeque<String>,
    ) {
        val depKey = "${dep.publisher}:${dep.id}"
        dependencyEdges.add(currentKey to depKey)

        when (val rangeResult = parseVersionRange(dep.versionRange)) {
            is RangeParseResult.Failure -> error(
                depKey,
                "dependencies",
                rangeResult.code,
                "Dependency '$depKey' in '$currentKey' has malformed range '${dep.versionRange}': " +
                    rangeResult.reason,
            )
            is RangeParseResult.Success -> {
                bindCandidates(currentKey, depKey, dep.versionRange, rangeResult.range, resolved, queue)
            }
        }
    }

    private fun bindCandidates(
        currentKey: String,
        depKey: String,
        rangeStr: String,
        range: VersionRange,
        resolved: MutableMap<String, InstalledPackage>,
        queue: ArrayDeque<String>,
    ) {
        val candidates = installedByKey[depKey].orEmpty()
        val matching = candidates.filter { candidate ->
            parsedPackageVersions[candidate]?.let { range.matches(it) } ?: false
        }

        if (candidates.isEmpty()) {
            error(
                depKey,
                "dependencies",
                PluginDependencyResolver.UNKNOWN_DEPENDENCY,
                "Dependency '$depKey' required by '$currentKey' is not installed",
            )
        } else if (matching.isEmpty()) {
            error(
                depKey,
                "dependencies",
                PluginDependencyResolver.VERSION_OUT_OF_RANGE,
                "No installed version of '$depKey' satisfies range '$rangeStr' required by '$currentKey'",
            )
        } else {
            bindResolvedDependency(depKey, currentKey, rangeStr, range, matching, resolved, queue)
        }
    }

    private fun bindResolvedDependency(
        depKey: String,
        currentKey: String,
        rangeStr: String,
        range: VersionRange,
        matching: List<InstalledPackage>,
        resolved: MutableMap<String, InstalledPackage>,
        queue: ArrayDeque<String>,
    ) {
        val existing = resolved[depKey]
        if (existing != null) {
            val existingVersion = parsedPackageVersions[existing]
            if (existingVersion != null && !range.matches(existingVersion)) {
                error(
                    depKey,
                    "dependencies",
                    PluginDependencyResolver.VERSION_OUT_OF_RANGE,
                    "Resolved version '${existing.version}' of '$depKey' does not satisfy range " +
                        "'$rangeStr' required by '$currentKey'",
                )
            }
        } else {
            val selected = selectLatest(matching)
            if (selected != null) {
                resolved[depKey] = selected
                queue.add(depKey)
            }
        }
    }

    private fun checkConflicts(resolved: Map<String, InstalledPackage>) {
        for ((key, pkg) in resolved) {
            for (conflict in pkg.conflicts) {
                checkSingleConflict(key, conflict, resolved)
            }
        }
    }

    private fun checkSingleConflict(key: String, conflict: DependencyRef, resolved: Map<String, InstalledPackage>) {
        val conflictKey = "${conflict.publisher}:${conflict.id}"
        val targetPkg = resolved[conflictKey] ?: return

        when (val rangeResult = parseVersionRange(conflict.versionRange)) {
            is RangeParseResult.Failure -> error(
                conflictKey,
                "conflicts",
                rangeResult.code,
                "Conflict '$conflictKey' in '$key' has malformed range '${conflict.versionRange}': " +
                    rangeResult.reason,
            )
            is RangeParseResult.Success -> {
                val targetVersion = parsedPackageVersions[targetPkg]
                if (targetVersion != null && rangeResult.range.matches(targetVersion)) {
                    error(
                        key,
                        "conflicts",
                        PluginDependencyResolver.CONFLICTING_PACKAGES,
                        "Package '$key' declares conflict with enabled package '$conflictKey' (${targetPkg.version})",
                    )
                }
            }
        }
    }

    private fun checkCycles(resolved: Map<String, InstalledPackage>, dependencyEdges: List<Pair<String, String>>) {
        val adjacency = mutableMapOf<String, MutableList<String>>()
        for (k in resolved.keys) {
            adjacency[k] = mutableListOf()
        }
        for ((from, to) in dependencyEdges) {
            if (from in resolved && to in resolved) {
                adjacency.getValue(from).add(to)
            }
        }

        val visited = mutableSetOf<String>()
        val onStack = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(node: String): Boolean {
            visited.add(node)
            onStack.add(node)
            path.add(node)
            var cycleFound = false

            for (neighbor in adjacency[node].orEmpty()) {
                if (neighbor in onStack) {
                    val cycleStart = path.indexOf(neighbor)
                    val cyclePath = path.subList(cycleStart, path.size) + neighbor
                    error(
                        node,
                        "dependencies",
                        PluginDependencyResolver.DEPENDENCY_CYCLE,
                        "Dependency cycle detected: ${cyclePath.joinToString(" -> ")}",
                    )
                    cycleFound = true
                } else if (neighbor !in visited && dfs(neighbor)) {
                    cycleFound = true
                }
                if (cycleFound) {
                    break
                }
            }

            path.removeAt(path.size - 1)
            onStack.remove(node)
            return cycleFound
        }

        for (k in resolved.keys.sorted()) {
            if (k !in visited) {
                dfs(k)
            }
        }
    }

    private fun checkRequestedOrder(
        resolved: Map<String, InstalledPackage>,
        dependencyEdges: List<Pair<String, String>>,
    ) {
        val reachableFrom = mutableMapOf<String, MutableSet<String>>()
        for (k in resolved.keys) {
            reachableFrom[k] = mutableSetOf()
        }
        for ((dependent, dependency) in dependencyEdges) {
            if (dependent in resolved && dependency in resolved) {
                reachableFrom.getValue(dependent).add(dependency)
            }
        }

        var changed = true
        while (changed) {
            changed = false
            for (node in resolved.keys) {
                val deps = reachableFrom.getValue(node)
                val newDeps = deps.flatMap { reachableFrom[it].orEmpty() }.toSet()
                if (deps.addAll(newDeps)) {
                    changed = true
                }
            }
        }

        val requestedIndices = requestedOrder.mapIndexed { index, key -> key to index }.toMap()

        for ((xKey, xIndex) in requestedIndices) {
            if (xKey !in resolved) continue
            val xDependencies = reachableFrom[xKey].orEmpty()
            for (depKey in xDependencies) {
                val depIndex = requestedIndices[depKey] ?: continue
                if (xIndex < depIndex) {
                    error(
                        xKey,
                        "requestedOrder",
                        PluginDependencyResolver.ORDER_VIOLATES_DEPENDENCY,
                        "Requested order puts '$xKey' before its dependency '$depKey'",
                    )
                }
            }
        }
    }

    private fun topologicalSort(keys: Set<String>, dependencyEdges: List<Pair<String, String>>): List<String> {
        val dependentsOf = mutableMapOf<String, MutableList<String>>()
        val dependencyCount = mutableMapOf<String, Int>()

        for (k in keys) {
            dependentsOf[k] = mutableListOf()
            dependencyCount[k] = 0
        }

        val dedupedEdges = dependencyEdges.filter { it.first in keys && it.second in keys }.distinct()
        for ((dependent, dependency) in dedupedEdges) {
            dependentsOf.getValue(dependency).add(dependent)
            dependencyCount[dependent] = (dependencyCount[dependent] ?: 0) + 1
        }

        val requestedIndices = requestedOrder.mapIndexed { index, key -> key to index }.toMap()

        val ready = keys.filter { (dependencyCount[it] ?: 0) == 0 }.toMutableList()

        val comparator = Comparator<String> { a, b ->
            val idxA = requestedIndices[a]
            val idxB = requestedIndices[b]
            when {
                idxA != null && idxB != null -> idxA.compareTo(idxB)
                idxA != null -> -1
                idxB != null -> 1
                else -> a.compareTo(b)
            }
        }

        val result = mutableListOf<String>()

        while (ready.isNotEmpty()) {
            ready.sortWith(comparator)
            val next = ready.removeAt(0)
            result.add(next)

            for (dep in dependentsOf[next].orEmpty()) {
                val count = (dependencyCount[dep] ?: 1) - 1
                dependencyCount[dep] = count
                if (count == 0) {
                    ready.add(dep)
                }
            }
        }

        return result
    }

    private fun selectLatest(candidates: List<InstalledPackage>): InstalledPackage? {
        val valid = candidates.mapNotNull { pkg ->
            parsedPackageVersions[pkg]?.let { pv -> pkg to pv }
        }
        return valid.maxWithOrNull(compareBy { it.second })?.first
    }

    private fun error(objectId: String, fieldPath: String, code: String, message: String) {
        errors += ValidationError(
            stageId = StageId.MODULE_APPLICATION,
            objectId = objectId,
            fieldPath = fieldPath,
            code = code,
            severity = Severity.ERROR,
            message = message,
        )
    }
}
private data class ParsedVersion(val segments: List<Long>) : Comparable<ParsedVersion> {
    override fun compareTo(other: ParsedVersion): Int {
        repeat(maxOf(segments.size, other.segments.size)) { i ->
            val cmp = segments.getOrElse(i) { 0L }.compareTo(other.segments.getOrElse(i) { 0L })
            if (cmp != 0) return cmp
        }
        return 0
    }
}

private sealed interface VersionParseResult {
    data class Success(val version: ParsedVersion) : VersionParseResult
    data class Failure(val reason: String) : VersionParseResult
}

private fun parseVersion(versionStr: String): VersionParseResult {
    val parts = versionStr.split('.')
    val bad = parts.firstOrNull { it.isEmpty() || !it.all(Char::isDigit) }
    return when {
        versionStr.isEmpty() -> VersionParseResult.Failure("Empty version string")
        bad != null -> VersionParseResult.Failure("Segment '$bad' is not numeric")
        else -> VersionParseResult.Success(ParsedVersion(parts.map { it.toLong() }))
    }
}

/** Either an exact version or an interval whose ends are independently inclusive `[]` or exclusive `()`. */
private sealed interface VersionRange {
    fun matches(version: ParsedVersion): Boolean

    data class Exact(val expected: ParsedVersion) : VersionRange {
        override fun matches(version: ParsedVersion): Boolean = version == expected
    }

    data class Interval(
        val lower: ParsedVersion,
        val lowerInclusive: Boolean,
        val upper: ParsedVersion,
        val upperInclusive: Boolean,
    ) : VersionRange {
        override fun matches(version: ParsedVersion): Boolean {
            val lowerOk = if (lowerInclusive) version >= lower else version > lower
            val upperOk = if (upperInclusive) version <= upper else version < upper
            return lowerOk && upperOk
        }
    }
}

private sealed interface RangeParseResult {
    data class Success(val range: VersionRange) : RangeParseResult
    data class Failure(val code: String, val reason: String) : RangeParseResult
}

private fun malformedRange(reason: String) =
    RangeParseResult.Failure(PluginDependencyResolver.MALFORMED_VERSION_RANGE, reason)

private fun parseIntervalRange(rangeStr: String): RangeParseResult {
    val closer = rangeStr.last()
    val bounds = rangeStr.substring(1, rangeStr.length - 1).split(',')
    val lower = bounds.getOrNull(0)?.let(::parseVersion)
    val upper = bounds.getOrNull(1)?.let(::parseVersion)
    return when {
        closer != ']' && closer != ')' -> malformedRange("Interval range must end with ']' or ')'")
        bounds.size != 2 -> malformedRange("Interval range must have exactly one comma separating bounds")
        lower is VersionParseResult.Failure ->
            RangeParseResult.Failure(PluginDependencyResolver.MALFORMED_VERSION, "Lower bound: ${lower.reason}")
        upper is VersionParseResult.Failure ->
            RangeParseResult.Failure(PluginDependencyResolver.MALFORMED_VERSION, "Upper bound: ${upper.reason}")
        else -> RangeParseResult.Success(
            VersionRange.Interval(
                lower = (lower as VersionParseResult.Success).version,
                lowerInclusive = rangeStr.first() == '[',
                upper = (upper as VersionParseResult.Success).version,
                upperInclusive = closer == ']',
            ),
        )
    }
}

private fun parseVersionRange(rangeStr: String): RangeParseResult = when {
    rangeStr.firstOrNull() == '[' || rangeStr.firstOrNull() == '(' -> parseIntervalRange(rangeStr)
    rangeStr.any { it in "[]()," || it in "^~><* " } -> malformedRange("Unsupported version range syntax: '$rangeStr'")
    else -> when (val res = parseVersion(rangeStr)) {
        is VersionParseResult.Success -> RangeParseResult.Success(VersionRange.Exact(res.version))
        is VersionParseResult.Failure -> RangeParseResult.Failure(
            PluginDependencyResolver.MALFORMED_VERSION,
            res.reason,
        )
    }
}
