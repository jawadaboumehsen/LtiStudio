/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import androidx.compose.runtime.Immutable
import org.ide.lti.core.domain.setup.ToolCategory
import org.ide.lti.core.domain.setup.ToolComponentItem

/**
 * 4-package Stitch sidebar grouping toolchain binaries into functional packages.
 */
enum class ToolNavPackage(val id: String, val label: String, val icon: String, val categories: Set<ToolCategory>) {
    ALL(
        id = "all_tools",
        label = "All tools",
        icon = "all_tools",
        categories = ToolCategory.entries.toSet(),
    ),
    FIRMWARE(
        id = "firmware",
        label = "Firmware",
        icon = "firmware",
        categories = setOf(ToolCategory.BOOT_AND_KERNEL, ToolCategory.DYNAMIC_PARTITIONS),
    ),
    FILESYSTEMS(
        id = "filesystems",
        label = "Filesystems",
        icon = "filesystems",
        categories = setOf(ToolCategory.FILESYSTEM_AND_IMAGES),
    ),
    SIGNING(
        id = "signing",
        label = "Signing",
        icon = "signing",
        categories = setOf(ToolCategory.SIGNING_AND_SECURITY),
    ),
    ;

    companion object {
        fun find(id: String): ToolNavPackage? = entries.firstOrNull { it.id == id }

        fun fromCategory(category: ToolCategory?): ToolNavPackage =
            category?.let { cat -> entries.firstOrNull { it != ALL && cat in it.categories } } ?: ALL
    }
}

/**
 * Single cohesive filter model coordinating the package sidebar and the category chip row.
 */
@Immutable
data class ToolSelection(val navPackage: ToolNavPackage = ToolNavPackage.ALL, val category: ToolCategory? = null) {
    /**
     * Categories to expose in the chip row for the currently selected package.
     * When ALL is selected, all six domain categories are exposed.
     * When a specific package is selected, its constituent categories are exposed.
     */
    val availableCategories: List<ToolCategory>
        get() = if (navPackage == ToolNavPackage.ALL) {
            ToolCategory.entries
        } else {
            navPackage.categories.toList()
        }

    /**
     * The chip row is only shown when there are multiple categories to distinguish.
     * Single-category packages (Filesystems, Signing) hide the redundant row.
     */
    val shouldShowCategoryChips: Boolean
        get() = availableCategories.size > 1

    fun selectPackage(pkg: ToolNavPackage): ToolSelection = ToolSelection(navPackage = pkg, category = null)

    fun selectCategory(cat: ToolCategory?): ToolSelection = when {
        cat == null -> copy(category = null)
        cat in navPackage.categories -> copy(category = cat)
        else -> ToolSelection(
            navPackage = ToolNavPackage.fromCategory(cat),
            category = cat,
        )
    }

    fun matches(tool: ToolComponentItem): Boolean = when {
        category != null -> tool.category == category
        else -> tool.category in navPackage.categories
    }
}
