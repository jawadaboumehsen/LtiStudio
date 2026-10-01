/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.navigation

/**
 * Data item representing an individual filter capsule in [GlassFilterChipRow].
 *
 * @property label The human-readable category title displayed on the chip.
 * @property count The number of elements belonging to this category. If negative, count is omitted.
 * @property value The unique string key matching the selected category.
 */
data class GlassFilterChipItem(val label: String, val count: Int, val value: String)
