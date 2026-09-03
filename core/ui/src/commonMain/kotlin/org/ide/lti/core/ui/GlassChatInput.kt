/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextStyle
import org.ide.lti.core.designsystem.component.GlassIconButton
import org.ide.lti.core.designsystem.component.GlassSurface
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

/**
 * Lti chat input field with send button.
 *
 * @param onSendMessage Callback when a message is sent
 * @param modifier Modifier to be applied to the input
 * @param enabled Whether the input is enabled
 * @param placeholder Placeholder text to display when input is empty
 */
@Composable
fun GlassChatInput(
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "Type a message...",
) {
    var inputText by remember { mutableStateOf("") }
    val glassColors = GlassTheme.colors

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = GlassShapes.Capsule,
        surfaceColor = glassColors.glassSurfaceElevated,
        borderColor = glassColors.glassBorder,
        borderWidth = StrokeWidth.Hairline,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Spacing.Medium,
                    end = Spacing.ExtraSmall,
                    top = Spacing.ExtraSmall,
                    bottom = Spacing.ExtraSmall,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                textStyle = TextStyle(
                    fontSize = FontSize.BodyMedium,
                    color = if (enabled) glassColors.onGlass else glassColors.onGlassMuted,
                ),
                cursorBrush = SolidColor(glassColors.glassAccent),
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Enter && inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputText = ""
                            true
                        } else {
                            false
                        }
                    },
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = glassColors.onGlassMuted,
                        )
                    }
                    innerTextField()
                },
            )

            Spacer(modifier = Modifier.width(Spacing.Small))

            GlassIconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText)
                        inputText = ""
                    }
                },
                enabled = enabled && inputText.isNotBlank(),
                size = ComponentSize.IconButtonSize,
            ) {
                Icon(
                    painter = AppIcons.SendPainterResource(),
                    contentDescription = "Send",
                    modifier = Modifier.size(IconSize.Medium),
                )
            }
        }
    }
}
