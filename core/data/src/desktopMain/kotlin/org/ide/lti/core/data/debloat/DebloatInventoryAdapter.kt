/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.debloat

import org.ide.lti.core.domain.debloat.DebloatCandidate
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Adapter scanning work-tree partition roots for candidate debloat targets and extracting
 * relevant sidecar configuration lines (fs_config, file_context).
 */
public class DebloatInventoryAdapter {

    public suspend fun inventory(workTreeRoot: Path, partitionRoots: Map<String, String>): List<DebloatCandidate> =
        Companion.inventory(workTreeRoot, partitionRoots)

    public suspend fun sidecarEntriesFor(workTreeRoot: Path, partition: String, relativePath: String): List<String> =
        Companion.sidecarEntriesFor(workTreeRoot, partition, relativePath)

    public companion object {
        /**
         * Walks each present partition root in [partitionRoots] under [workTreeRoot], emitting
         * one [DebloatCandidate] per regular file with its actual byte size.
         *
         * Package identifier is derived strictly when the path conforms to the standard app layout
         * (`app/<Name>/<Name>.apk` or `priv-app/<Name>/<Name>.apk`), otherwise `null`. Symlinks
         * are skipped.
         */
        public suspend fun inventory(workTreeRoot: Path, partitionRoots: Map<String, String>): List<DebloatCandidate> {
            val candidates = mutableListOf<DebloatCandidate>()
            for ((partition, relRoot) in partitionRoots) {
                val partRoot = workTreeRoot.resolve(relRoot).normalize()
                if (Files.exists(partRoot)) {
                    collectPartitionCandidates(partRoot, partition, candidates)
                }
            }
            return candidates.sortedWith(compareBy({ it.partition }, { it.relativePath }))
        }

        private fun collectPartitionCandidates(partRoot: Path, partition: String, out: MutableList<DebloatCandidate>) {
            Files.walk(partRoot).use { stream ->
                stream.forEach { p ->
                    toCandidateOrNull(p, partRoot, partition)?.let(out::add)
                }
            }
        }

        private fun toCandidateOrNull(p: Path, partRoot: Path, partition: String): DebloatCandidate? {
            if (Files.isSymbolicLink(p) || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                return null
            }
            val rel = partRoot.relativize(p).toString().replace('\\', '/')
            val normalizedRel = if (rel.startsWith("$partition/")) {
                rel.removePrefix("$partition/")
            } else {
                rel
            }
            val packageId = derivePackageId(normalizedRel)
            return DebloatCandidate(
                partition = partition,
                relativePath = normalizedRel,
                sizeBytes = Files.size(p),
                packageId = packageId,
            )
        }

        private fun derivePackageId(normalizedRel: String): String? {
            val segments = normalizedRel.split('/')
            return if (
                segments.size == 3 &&
                (segments[0] == "app" || segments[0] == "priv-app") &&
                segments[2] == "${segments[1]}.apk"
            ) {
                segments[1]
            } else {
                null
            }
        }

        /**
         * Reads matching lines mentioning [relativePath] from `<workTreeRoot>/configs/fs_config-<partition>`
         * and `<workTreeRoot>/configs/file_context-<partition>` (falling back to `file_contexts-<partition>`),
         * returning the unmodified line texts.
         */
        public suspend fun sidecarEntriesFor(
            workTreeRoot: Path,
            partition: String,
            relativePath: String,
        ): List<String> {
            val configsDir = workTreeRoot.resolve("configs").normalize()
            val matchingLines = mutableListOf<String>()
            val escapedPath = relativePath.replace(".", "\\.")

            val targetFiles = listOf(
                configsDir.resolve("fs_config-$partition"),
                configsDir.resolve("file_context-$partition").takeIf { Files.exists(it) }
                    ?: configsDir.resolve("file_contexts-$partition"),
            )

            for (file in targetFiles) {
                if (Files.exists(file)) {
                    val lines = Files.readAllLines(file, Charsets.UTF_8)
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith('#')) continue
                        if (trimmed.contains(relativePath) || trimmed.contains(escapedPath)) {
                            matchingLines.add(line)
                        }
                    }
                }
            }

            return matchingLines
        }
    }
}
