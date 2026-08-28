/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.catalog.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import org.ide.lti.core.designsystem.component.GlassOtpTextField
import org.ide.lti.core.designsystem.component.GlassPasswordField
import org.ide.lti.core.designsystem.component.GlassSlider
import org.ide.lti.core.designsystem.component.GlassTextField
import org.ide.lti.core.designsystem.component.GlassToggle
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing

@Composable
fun InputsTab() {
    val scrollState = rememberScrollState()
    val backdrop = rememberLayerBackdrop()

    var textValue by remember { mutableStateOf("workspace-alpha") }
    var emailValue by remember { mutableStateOf("developer@ltirom.org") }
    var errorTextValue by remember { mutableStateOf("invalid-token-input") }
    var passwordValue by remember { mutableStateOf("SuperSecretPassword123") }
    var showPassword by remember { mutableStateOf(false) }
    var otpSuccess by remember { mutableStateOf(false) }

    var sliderValue by remember { mutableFloatStateOf(0.72f) }
    var toggleState1 by remember { mutableStateOf(true) }
    var toggleState2 by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraLarge)
    ) {
        // Section 1: Glass Text Fields
        SectionContainer(
            title = "1. Glass Text Fields (GlassTextField)",
            description = "Text inputs with animated focus/error border transitions and glass backdrop sampling."
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                GlassTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = "Workspace Name",
                    placeholder = "Enter workspace name",
                    leadingIcon = {
                        Icon(AppIcons.Folder, contentDescription = null, tint = GlassTheme.colors.onGlassSecondary, modifier = Modifier.size(IconSize.Medium))
                    },
                    helperText = "Alphanumeric characters and hyphens only"
                )

                GlassTextField(
                    value = emailValue,
                    onValueChange = { emailValue = it },
                    label = "Email Address",
                    placeholder = "developer@domain.com",
                    leadingIcon = {
                        Icon(AppIcons.OutlinedInfo, contentDescription = null, tint = GlassTheme.colors.onGlassSecondary, modifier = Modifier.size(IconSize.Medium))
                    }
                )

                GlassTextField(
                    value = errorTextValue,
                    onValueChange = { errorTextValue = it },
                    label = "Verification Token",
                    errorText = "Invalid signature token. Please regenerate.",
                    leadingIcon = {
                        Icon(AppIcons.OutlinedLock, contentDescription = null, tint = GlassTheme.colors.glassAccent, modifier = Modifier.size(IconSize.Medium))
                    }
                )
            }
        }

        // Section 2: Glass Password Field
        SectionContainer(
            title = "2. Secure Password Input (GlassPasswordField)",
            description = "Specialized text input with visibility toggle and security masks."
        ) {
            GlassPasswordField(
                label = "Access Key / Master Password",
                value = passwordValue,
                showPassword = showPassword,
                showPasswordChange = { showPassword = it },
                onValueChange = { passwordValue = it },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Section 3: Glass OTP Field
        SectionContainer(
            title = "3. Multi-Box OTP Input (GlassOtpTextField)",
            description = "Character-boxed verification input with glass effect border transitions."
        ) {
            GlassOtpTextField(
                realOtp = "2026",
                otpCount = 4,
                onOtpTextCorrectlyEntered = { otpSuccess = true }
            )

            if (otpSuccess) {
                Text(
                    text = "OTP successfully verified (2026)!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTheme.colors.glassAccent
                )
            }
        }

        // Section 4: Glass Toggle Switches
        SectionContainer(
            title = "4. Glass Toggle Switches (GlassToggle)",
            description = "Fluid toggle switch with velocity-squish thumb drag and track highlighting."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Aurora Background Shader", color = GlassTheme.colors.onGlass)
                GlassToggle(
                    selected = { toggleState1 },
                    onSelect = { toggleState1 = it },
                    backdrop = backdrop,
                )
            }

            Spacer(Modifier.height(Spacing.Small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-format On Save", color = GlassTheme.colors.onGlass)
                GlassToggle(
                    selected = { toggleState2 },
                    onSelect = { toggleState2 = it },
                    backdrop = backdrop,
                )
            }
        }

        // Section 5: Glass Slider
        SectionContainer(
            title = "5. Glass Slider (GlassSlider)",
            description = "Drag-interpolated slider with velocity squish thumb effect and track backdrop sampling."
        ) {
            Text(
                text = "Backdrop Blur Radius: ${"%.1f".format(sliderValue * 100)}%",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTheme.colors.onGlass
            )
            Spacer(Modifier.height(Spacing.Small))
            GlassSlider(
                value = { sliderValue },
                onValueChange = { sliderValue = it },
                valueRange = 0f..1f,
                visibilityThreshold = 0.01f,
                backdrop = backdrop,
            )
        }
    }
}
