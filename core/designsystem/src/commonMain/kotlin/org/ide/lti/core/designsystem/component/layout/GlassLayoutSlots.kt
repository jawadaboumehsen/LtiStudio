/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * Cohesive configuration of composable slots for [GlassLayout].
 * Adheres to the Interface Segregation Principle (ISP).
 */
@Immutable
data class GlassLayoutSlots(
    val mainContent: @Composable () -> Unit,
    val leftPanel: (@Composable () -> Unit)? = null,
    val rightPanel: (@Composable () -> Unit)? = null,
    val bottomPanel: (@Composable () -> Unit)? = null,
    val leftRail: (@Composable () -> Unit)? = null,
    val rightRail: (@Composable () -> Unit)? = null,
    val topBar: (@Composable () -> Unit)? = null,
    val statusBar: (@Composable () -> Unit)? = null,
)
