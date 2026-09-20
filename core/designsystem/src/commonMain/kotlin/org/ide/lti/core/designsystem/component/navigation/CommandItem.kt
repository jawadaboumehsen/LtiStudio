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
public enum class CommandCategory(public val displayName: String) {
    NAVIGATION("Navigation"),
    TARGET("Target"),
    CONFIG("Configuration"),
    PANELS("Panels"),
    GENERAL("General"),
}

/**
 * A single executable entry in [GlassCommandPalette].
 */
public class CommandItem(
    public val id: String,
    public val title: String,
    public val subtitle: String,
    public val category: CommandCategory,
    public val icon: AppIcon,
    public val shortcut: String? = null,
    public val onExecute: () -> Unit,
)
