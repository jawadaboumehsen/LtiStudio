/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.ports

import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.workspace.Workspace

/**
 * The first link among the folders and files the app itself makes in [ws], or null. Anything that clears or writes
 * them (`work`, `restore`, `out/images`, a `.partial`) would follow a link there out of the workspace, so nothing
 * does while one is there. Firmware contents are left alone, as links are normal in them: the partition trees of
 * `work/`, and what lies deeper than the app's own folders. Throws when the scan fails: that proves nothing.
 */
public suspend fun PipelineExecutionPort.linkInWorkspace(ws: Workspace): String? {
    val root = ws.linuxPath ?: return null
    val scan = execute(ws, StepCommand("find", linkScanArgs(root), root))
    check(scan.exitCode == 0) { "The workspace could not be checked for links: ${scan.stderr.trim()}" }
    return scan.stdout.lineSequence().firstOrNull(String::isNotBlank)
}

/** Fails with what to remove when [linkInWorkspace] finds a link. */
public suspend fun PipelineExecutionPort.requireNoLinkInWorkspace(ws: Workspace) {
    linkInWorkspace(ws)?.let { link ->
        error("$link is a link, which could lead this work out of the workspace. Remove it; nothing was changed.")
    }
}

/** Three levels: `out/images/system.img`, `work/configs/fs_config-system`; firmware starts at `work/system/bin`. */
private fun linkScanArgs(root: String): List<String> = listOf(
    root,
    "-maxdepth",
    "3",
    "(",
    "(",
    "-path",
    "$root/work/*/*",
    "!",
    "-path",
    "$root/work/configs/*",
    "!",
    "-path",
    "$root/work/kernel/*",
    "!",
    "-path",
    "$root/work/package/*",
    ")",
    "-o",
    "-path",
    "$root/workbench/*/*",
    "-o",
    "-path",
    "$root/out/vendor-ramdisk-by-name/*",
    "-o",
    "-path",
    "$root/out/verify/*",
    ")",
    "-prune",
    "-o",
    "-type",
    "l",
    "-print",
)
