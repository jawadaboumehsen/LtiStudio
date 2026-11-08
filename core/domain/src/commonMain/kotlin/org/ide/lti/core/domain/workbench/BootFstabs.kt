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

import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException

// The mount tables of the boot images and of the work tree, read and edited as files (Assemble > Mount table).

/**
 * Applies [edit] to every fstab of [partition]'s unpacked ramdisk (the first-stage mount table in vendor_boot).
 * Returns the fstab files changed; repack to keep the change.
 */
public suspend fun BootWorkbench.editFstabs(workspaceId: String, partition: String, edit: FstabEdit): List<String> =
    editFstabs(workspaceId, partition, edit.title, edit::apply)

/** Edits every fstab in the unpacked [partition]'s ramdisk with [transform]; the files it changed. */
public suspend fun BootWorkbench.editFstabs(
    workspaceId: String,
    partition: String,
    title: String,
    transform: (String) -> String,
): List<String> = runner.job(workspaceId, title) {
    ramdiskFstabFiles(this, partition).mapNotNull { rel ->
        val text = readText(rel) ?: return@mapNotNull null
        val edited = transform(text)
        rel.takeIf { edited != text }?.also { writeText(it, edited) }
    }
}

/** The fstabs in the unpacked [partition]'s ramdisk (its first-stage mount table), path -> text. */
public suspend fun BootWorkbench.ramdiskFstabs(workspaceId: String, partition: String): Map<String, String> =
    runner.read(workspaceId) {
        ramdiskFstabFiles(this, partition).mapNotNull { rel -> readText(rel)?.let { rel to it } }.toMap()
    }

/** The fstabs of the assembled work tree (`work/<p>/etc/fstab*`), path -> text. */
public suspend fun BootWorkbench.workFstabs(workspaceId: String): Map<String, String> = runner.read(workspaceId) {
    if (!exists("work")) return@read emptyMap()
    sh("cd \"$1\" && find . -path './*/etc/fstab*' -type f | sort", abs("work")).lines()
        .filter(String::isNotBlank).mapNotNull { f ->
            val rel = "work/${f.removePrefix("./")}"
            readText(rel)?.let { rel to it }
        }.toMap()
}

private suspend fun BootWorkbench.ramdiskFstabFiles(job: WorkbenchRunner.Job, partition: String): List<String> {
    val folder = "${dir(partition)}/${BootWorkbench.RAMDISK_DIR}"
    if (!job.exists(folder)) {
        throw RefusalException(Refusal("$partition.img is not unpacked.", "$partition.img", "Unpack it first."))
    }
    return job.run("find", job.abs(folder), "-type", "f", "-name", "fstab.*").lines().filter { it.isNotBlank() }
        .map { f -> "$folder/${f.removePrefix(job.abs(folder) + "/")}" }
}
