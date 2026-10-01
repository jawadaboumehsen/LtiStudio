/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalFocusManager

fun Modifier.tabNavigation(): Modifier = this then TabNavigationElement

private object TabNavigationElement : ModifierNodeElement<TabNavigationNode>() {
    override fun create(): TabNavigationNode = TabNavigationNode()

    override fun update(node: TabNavigationNode) {}

    override fun InspectorInfo.inspectableProperties() {
        name = "tabNavigation"
    }

    override fun hashCode(): Int = "tabNavigation".hashCode()

    override fun equals(other: Any?): Boolean = other === this
}

private class TabNavigationNode :
    Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    KeyInputModifierNode {

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        if (event.key == Key.Tab && event.type == KeyEventType.KeyDown) {
            val focusManager = currentValueOf(LocalFocusManager)
            focusManager.moveFocus(
                if (event.isShiftPressed) {
                    FocusDirection.Previous
                } else {
                    FocusDirection.Next
                },
            )
            return true
        }
        return false
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false
}
