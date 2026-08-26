/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ide.lti.core.designsystem.component.GlassPanelContainer
import org.ide.lti.core.designsystem.component.GlassPanelHeader
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TerminalPanel(
    modifier: Modifier = Modifier,
    viewModel: TerminalViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    GlassPanelContainer(
        modifier = modifier,
        header = {
            GlassPanelHeader(
                title = "Terminal",
            )
        },
    ) {
        when (uiState) {
            is TerminalUiState.Empty -> {
                EmptyTerminalView()
            }
        }
    }
}

@Composable
private fun EmptyTerminalView() {
    val glassColors = GlassTheme.colors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(glassColors.glassAccent)
                )
                Text(
                    text = "Integrated Terminal",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = glassColors.onGlass,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$ lti --status ready",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = glassColors.onGlassMuted,
            )
        }
    }
}