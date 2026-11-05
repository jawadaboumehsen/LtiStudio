/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workbench

import org.ide.lti.core.domain.pipeline.stageCacheFile
import org.ide.lti.core.domain.pipeline.stages.FirmwareExtractionStage
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.model.run.StageId

/** One folder of a workspace and what it takes on disk. */
public data class FolderUsage(
    val path: String,
    val bytes: Long,
    /** The next build makes it again, so it may be deleted to free the space. */
    val cleanable: Boolean,
    val note: String,
)

/**
 * What a workspace's folders take in WSL, and deleting the ones the next build makes again. A deleted folder is
 * rebuilt because a stage counts as cached only while its declared outputs are still there.
 */
public class WorkspaceStorage(private val runner: WorkbenchRunner, private val runs: RunRepository) {

    /** The known folders that exist, largest first, and everything else in the workspace as `other`. */
    public suspend fun usage(workspaceId: String): List<FolderUsage> = runner.read(workspaceId) {
        parseUsage(sh(USAGE_SCRIPT, root, *FOLDERS.map { it.path }.toTypedArray()))
    }

    /** Deletes [path] when it is one the next build makes again; returns the bytes freed. */
    public suspend fun clean(workspaceId: String, path: String): Long {
        cleanRefusal(path, runs.latestActive(workspaceId) != null)?.let { throw RefusalException(it) }
        return runner.job(workspaceId, "Free disk space") {
            val freed = parseUsage(sh(USAGE_SCRIPT, root, path)).firstOrNull { it.path == path }?.bytes ?: 0L
            // Extract's cached result does not depend on the raw members, so it must be told to unpack them again.
            if (path == FirmwareExtractionStage.RAW) writeText(stageCacheFile(StageId.FIRMWARE_EXTRACTION), "")
            run("rm", "-rf", "--", abs(path))
            freed
        }
    }

    private data class Folder(val path: String, val cleanable: Boolean, val note: String)

    public companion object {
        private const val OTHER = "other"

        private val FOLDERS = listOf(
            Folder("firmware/downloaded", false, "The firmware archive: only a new download or import brings it back."),
            Folder("firmware/raw", true, "Members unpacked from the archive; the next build extracts them again."),
            Folder("firmware/extracted", true, "Partition images and trees; Extract makes them again (minutes)."),
            Folder("work", true, "The assembled tree; the next build assembles it again."),
            Folder("out/images", true, "Built partition images; the next build makes them again."),
            Folder("out/verify", true, "Mount points of the image check; the next build makes them again."),
            Folder("out/extra", true, "Full super.img and Odin parts; the next build makes them again."),
            Folder("out/package", false, "Your built packages: delete old ones yourself."),
            Folder("out/toolkit", false, "Files you extracted or wrote with the tools."),
            Folder("restore", false, "Backups behind the restore points: without them nothing can be restored."),
            Folder("overrides", false, "Boot images edited in the Studio and used by the build."),
            Folder("firmware/replacements", false, "Your own partition images, used instead of the stock ones."),
            Folder("workbench", true, "Scratch of the boot, image, content and debloat tools; unsaved boot edits go."),
        )

        /**
         * `sh -c USAGE_SCRIPT sh <root> <folder>...`: `<folder>\t<bytes>` per existing folder, then the whole
         * workspace as `.`. `du -sb` counts apparent bytes, as the files would copy.
         */
        private const val USAGE_SCRIPT: String =
            "r=\"\$1\"; shift; for p in \"\$@\"; do [ -e \"\$r/\$p\" ] && printf '%s\\t%s\\n' \"\$p\" " +
                "\"\$(du -sb \"\$r/\$p\" | cut -f1)\"; done; printf '.\\t%s\\n' \"\$(du -sb \"\$r\" | cut -f1)\""

        /** [USAGE_SCRIPT]'s output as known folders (largest first) plus `other` for the rest of the total. */
        public fun parseUsage(output: String): List<FolderUsage> {
            val sizes = output.lines().mapNotNull { line ->
                val (path, bytes) = line.split('\t').takeIf { it.size == 2 } ?: return@mapNotNull null
                bytes.trim().toLongOrNull()?.let { path to it }
            }.toMap()
            val known = FOLDERS.mapNotNull { f -> sizes[f.path]?.let { FolderUsage(f.path, it, f.cleanable, f.note) } }
            val rest = (sizes["."] ?: 0L) - known.filterNot { nested(it.path, known) }.sumOf { it.bytes }
            val other = FolderUsage(OTHER, rest.coerceAtLeast(0L), false, "Settings, logs and everything else.")
            return known.sortedByDescending { it.bytes } + listOfNotNull(other.takeIf { it.bytes > 0 })
        }

        /** Why [path] may not be deleted now, or null when it may. */
        public fun cleanRefusal(path: String, runActive: Boolean): Refusal? = when {
            FOLDERS.none { it.path == path && it.cleanable } -> Refusal(
                "$path is not one the next build makes again.",
                path,
                "Only the folders marked as re-created can be freed here.",
            )

            runActive -> Refusal(
                "A build is running in this workspace and uses its folders.",
                path,
                "Wait for it to finish, or cancel it, then free the space.",
            )

            else -> null
        }

        /** [path] lies inside another listed folder, so the total counts it already. */
        private fun nested(path: String, all: List<FolderUsage>): Boolean =
            all.any { it.path != path && path.startsWith(it.path + "/") }
    }
}
