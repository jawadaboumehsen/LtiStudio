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

import org.ide.lti.core.domain.pipeline.toolkit.ArchiveBrowser
import org.ide.lti.core.domain.pipeline.toolkit.ArchiveListing

/**
 * Browses a firmware archive and the archives inside it (`ArchiveBrowser`), and copies
 * chosen members out to `out/toolkit/archive/`. The archive is a workspace file, or any file the user picked.
 */
public class ArchiveWorkbench(
    private val runner: WorkbenchRunner,
    /** A Windows path as WSL sees it (`C:\fw.zip` -> `/mnt/c/fw.zip`). */
    private val toLinuxPath: (String) -> String = { it },
) {
    /** What [archive], or the member [chain] leads to inside it, holds. */
    public suspend fun list(workspaceId: String, archive: String, chain: List<String>): ArchiveListing =
        runner.read(workspaceId) {
            requireFolderInside("$CACHE/member", CACHE)
            val output = sh(
                ArchiveBrowser.SCRIPT,
                "list",
                toolPath(SEVEN_ZIP),
                abs(CACHE),
                "-",
                where(archive),
                *chain.toTypedArray(),
            )
            ArchiveBrowser.parse(output, chain.lastOrNull() ?: archive)
        }

    /** Copies [members] of the level [chain] leads to into `out/toolkit/archive/`; returns that folder. */
    public suspend fun extract(
        workspaceId: String,
        archive: String,
        chain: List<String>,
        members: List<String>,
    ): String = runner.job(workspaceId, "Copy out of ${(chain.lastOrNull() ?: archive).substringAfterLast('/')}") {
        // The script keeps members inside the folder; this keeps the folder inside the workspace.
        requireFolderInside("$OUT/member", OUT)
        requireFolderInside("$CACHE/member", CACHE)
        sh(
            ArchiveBrowser.SCRIPT,
            "extract",
            toolPath(SEVEN_ZIP),
            abs(CACHE),
            abs(OUT),
            where(archive),
            *chain.toTypedArray(),
            "--",
            *members.toTypedArray(),
        )
        hostPath(OUT) ?: abs(OUT)
    }

    /** A workspace path as is; a Windows path (`C:\…`, `\\wsl.localhost\…`) as WSL sees it. */
    private fun WorkbenchRunner.Job.where(archive: String): String =
        if ('\\' in archive || archive.getOrNull(1) == ':') toLinuxPath(archive) else abs(archive)

    public companion object {
        public const val OUT: String = "out/toolkit/archive"
        private const val CACHE = "out/toolkit/archive-cache"
        private const val SEVEN_ZIP = "7zz"
    }
}
