/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.navigation

import org.ide.lti.core.designsystem.icon.AppIcon

/**
 * Grouping for a [CommandItem] inside [GlassCommandPalette].
 */
enum class CommandCategory(val displayName: String) {
    NAVIGATION("Navigation"),
    TARGET("Target"),
    CONFIG("Configuration"),
    PANELS("Panels"),
    GENERAL("General"),
}

/**
 * A single executable entry in [GlassCommandPalette].
 */
class CommandItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: CommandCategory,
    val icon: AppIcon,
    val shortcut: String? = null,
    val onExecute: () -> Unit,
)
