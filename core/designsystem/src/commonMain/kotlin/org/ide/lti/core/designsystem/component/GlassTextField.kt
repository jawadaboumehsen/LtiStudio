/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousRoundedRectangle
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LensRefraction
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.resolveAnimationSpec
import org.ide.lti.core.designsystem.utils.nonLetterColorVisualTransformation
import org.ide.lti.core.designsystem.utils.tabNavigation

/**
 * Text field glass effect stack — subtle, per the kmp-liquid-glass skill's guidance for
 * inputs that shouldn't distract from typed content.
 */
val GlassTextFieldEffects: BackdropEffectScope.() -> Unit = {
    vibrancy()
    blur(BackdropBlur.TextField.toPx())
    lens(LensRefraction.TextField.height.toPx(), LensRefraction.TextField.amount.toPx())
}

/**
 * Glass text field component with focus animations.
 */
@Suppress("LongParameterList")
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    effects: BackdropEffectScope.() -> Unit = GlassTextFieldEffects,
    backdrop: Backdrop? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isError = errorText != null

    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.error
            isFocused -> GlassTheme.colors.glassBorderHighlight
            else -> GlassTheme.colors.glassBorder
        },
        animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
    )

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = resolveLabelColor(isError = isError, enabled = enabled),
                modifier = Modifier.padding(bottom = Spacing.ExtraSmall, start = Spacing.ExtraSmall),
            )
        }

        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = ContinuousRoundedRectangle(CornerRadius.Medium),
            effects = effects,
            backdrop = backdrop,
            borderColor = animatedBorderColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Box(modifier = Modifier.padding(end = Spacing.SmallMedium)) {
                        leadingIcon()
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = GlassTheme.colors.onGlassMuted,
                        )
                    }

                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                        readOnly = readOnly,
                        textStyle = textStyle.copy(
                            color = if (enabled) GlassTheme.colors.onGlass else GlassTheme.colors.onGlassMuted,
                        ),
                        visualTransformation = visualTransformation,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        singleLine = singleLine,
                        maxLines = maxLines,
                        interactionSource = interactionSource,
                        cursorBrush = SolidColor(GlassTheme.colors.glassAccent),
                    )
                }

                if (trailingIcon != null) {
                    Box(modifier = Modifier.padding(start = Spacing.SmallMedium)) {
                        trailingIcon()
                    }
                }
            }
        }

        val message = errorText ?: helperText
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else GlassTheme.colors.onGlassMuted,
                modifier = Modifier.padding(top = Spacing.ExtraSmall, start = Spacing.ExtraSmall),
            )
        }
    }
}

/**
 * Secure password input with glass styling and visibility toggle.
 */
@Composable
fun GlassPasswordField(
    label: String,
    value: String,
    showPassword: Boolean,
    showPasswordChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    hint: String? = null,
    showPasswordTestTag: String? = null,
    autoFocus: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Password,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
        }
    }
    GlassTextField(
        modifier = modifier
            .tabNavigation()
            .focusRequester(focusRequester),
        label = label,
        value = value,
        onValueChange = onValueChange,
        visualTransformation = when {
            !showPassword -> PasswordVisualTransformation()
            readOnly -> nonLetterColorVisualTransformation()
            else -> VisualTransformation.None
        },
        singleLine = singleLine,
        readOnly = readOnly,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = keyboardActions,
        errorText = hint,
        trailingIcon = {
            IconButton(
                onClick = { showPasswordChange(!showPassword) },
            ) {
                val painter = if (showPassword) {
                    AppIcons.HidePainterResource()
                } else {
                    AppIcons.ShowPainterResource()
                }

                Icon(
                    modifier = Modifier
                        .size(IconSize.Medium)
                        .semantics { showPasswordTestTag?.let { testTag = it } },
                    painter = painter,
                    contentDescription = "togglePassword",
                    tint = GlassTheme.colors.onGlassSecondary,
                )
            }
        },
    )
}

/**
 * OTP / PIN input field with glass styling.
 */
@Composable
fun GlassOtpTextField(
    onOtpTextCorrectlyEntered: () -> Unit,
    modifier: Modifier = Modifier,
    realOtp: String = "",
    otpCount: Int = 4,
) {
    var otpText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.Medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicTextField(
            value = TextFieldValue(otpText, selection = TextRange(otpText.length)),
            onValueChange = {
                otpText = it.text
                isError = false
                if (otpText.length == otpCount) {
                    if (otpText != realOtp) {
                        isError = true
                    } else {
                        onOtpTextCorrectlyEntered()
                    }
                }
            },
            keyboardActions = KeyboardActions(
                onDone = {
                    if (otpText != realOtp) {
                        isError = true
                    } else {
                        onOtpTextCorrectlyEntered()
                    }
                },
            ),
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            decorationBox = {
                Row(horizontalArrangement = Arrangement.Center) {
                    repeat(otpCount) { index ->
                        val char = if (index >= otpText.length) "" else otpText[index].toString()
                        val filled = char.isNotEmpty()
                        Box(
                            modifier = Modifier
                                .size(ComponentSize.OtpBoxSize)
                                .padding(Spacing.ExtraSmall)
                                .glassEffect(
                                    shape = ContinuousRoundedRectangle(CornerRadius.Small),
                                    effects = {
                                        vibrancy()
                                        blur(BackdropBlur.TextField.toPx())
                                    },
                                    tint = if (filled) GlassTheme.colors.glassTint else Color.Unspecified,
                                )
                                .glassBorder(
                                    color = if (filled) {
                                        GlassTheme.colors.glassBorderHighlight
                                    } else {
                                        GlassTheme.colors.glassBorder
                                    },
                                    width = StrokeWidth.Standard,
                                    shape = ContinuousRoundedRectangle(CornerRadius.Small),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = char,
                                style = MaterialTheme.typography.titleMedium,
                                color = GlassTheme.colors.onGlass,
                            )
                        }
                    }
                }
            },
        )
        if (isError) {
            Text(
                text = "Invalid OTP",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.Small),
            )
        }
    }
}

@Composable
private fun resolveLabelColor(isError: Boolean, enabled: Boolean): Color = when {
    isError -> MaterialTheme.colorScheme.error
    !enabled -> GlassTheme.colors.onGlassMuted
    else -> GlassTheme.colors.onGlassSecondary
}
