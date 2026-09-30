/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.inputs

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.hazeGlass
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.primitives.GlassSurface
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.GlassIcon
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassMaterialStyles
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.sceneHazeInput
import org.ide.lti.core.designsystem.utils.nonLetterColorVisualTransformation
import org.ide.lti.core.designsystem.utils.tabNavigation

/**
 * Validation and message state for [GlassTextField].
 */
sealed interface TextFieldState {
    data object Default : TextFieldState
    data class Helper(val message: String) : TextFieldState
    data class Error(val message: String) : TextFieldState
    data class Warning(val message: String) : TextFieldState
    data class Success(val message: String? = null) : TextFieldState
}

/**
 * Leading accessory slot for [GlassTextField].
 */
sealed interface TextFieldLeadingSlot {
    data object None : TextFieldLeadingSlot
    data class Icon(val icon: AppIcon, val onClick: (() -> Unit)? = null) : TextFieldLeadingSlot
    data class Custom(val content: @Composable () -> Unit) : TextFieldLeadingSlot
}

/**
 * Trailing accessory slot for [GlassTextField].
 */
sealed interface TextFieldTrailingSlot {
    data object None : TextFieldTrailingSlot
    data class Icon(val icon: AppIcon, val onClick: (() -> Unit)? = null) : TextFieldTrailingSlot
    data class Clear(val onClear: () -> Unit) : TextFieldTrailingSlot
    data class PasswordToggle(val isVisible: Boolean, val onToggle: () -> Unit, val testTag: String? = null) :
        TextFieldTrailingSlot
    data class Custom(val content: @Composable () -> Unit) : TextFieldTrailingSlot
}

/**
 * Text field input behavior configuration adhering to Interface Segregation.
 */
@Immutable
data class TextFieldConfig(
    val singleLine: Boolean = true,
    val maxLines: Int = 1,
    val keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    val keyboardActions: KeyboardActions = KeyboardActions.Default,
    val visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    companion object {
        val Default = TextFieldConfig()
        val Password = TextFieldConfig(
            singleLine = true,
            maxLines = 1,
            visualTransformation = PasswordVisualTransformation(),
        )
    }
}

/**
 * Glass text field component with focus animations and sealed state/slots.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    state: TextFieldState = TextFieldState.Default,
    leading: TextFieldLeadingSlot = TextFieldLeadingSlot.None,
    trailing: TextFieldTrailingSlot = TextFieldTrailingSlot.None,
    config: TextFieldConfig = TextFieldConfig.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    focusRequester: FocusRequester? = null,
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isError = state is TextFieldState.Error
    val isWarning = state is TextFieldState.Warning
    val isSuccess = state is TextFieldState.Success
    var isHovered by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            isError -> colors.error
            isWarning -> GlassTheme.diagnosticColors.warning
            isSuccess -> GlassTheme.diagnosticColors.success
            isFocused -> colors.primary
            isHovered -> colors.outline.copy(alpha = AlphaTokens.Prominent)
            else -> colors.outline
        },
        label = "GlassTextField_Border",
    )
    val focusGradientColor = colors.primary

    Column(
        modifier = modifier
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false },
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = resolveLabelColor(isError = isError, enabled = enabled),
                modifier = Modifier.padding(bottom = Spacing.ExtraSmall, start = Spacing.ExtraSmall),
            )
        }

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (isFocused) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    focusGradientColor.copy(alpha = AlphaTokens.Subtle),
                                    Color.Transparent,
                                ),
                                startY = 0f,
                                endY = size.height * 0.75f,
                            ),
                        )
                    }
                },
            shape = GlassShapes.HazeMedium,
            borderColor = animatedBorderColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextFieldLeadingContent(leading = leading)

                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                        enabled = enabled,
                        readOnly = readOnly,
                        textStyle = textStyle.copy(
                            color = if (enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ),
                        visualTransformation = config.visualTransformation,
                        keyboardOptions = config.keyboardOptions,
                        keyboardActions = config.keyboardActions,
                        singleLine = config.singleLine,
                        maxLines = config.maxLines,
                        interactionSource = interactionSource,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box {
                                if (value.isEmpty() && placeholder != null) {
                                    Text(
                                        text = placeholder,
                                        style = textStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }

                TextFieldTrailingContent(
                    trailing = trailing,
                    valueNotEmpty = value.isNotEmpty(),
                )
            }
        }

        TextFieldHelperMessage(state = state)
    }
}

/**
 * Renders the leading accessory slot content for a [GlassTextField].
 */
@Composable
private fun TextFieldLeadingContent(leading: TextFieldLeadingSlot, modifier: Modifier = Modifier) {
    when (leading) {
        is TextFieldLeadingSlot.None -> {}
        is TextFieldLeadingSlot.Icon -> {
            Box(modifier = modifier.padding(end = Spacing.SmallMedium)) {
                if (leading.onClick != null) {
                    GlassIconButton(
                        icon = leading.icon,
                        onClick = leading.onClick,
                        size = ComponentSize.TopBarButtonSize,
                    )
                } else {
                    GlassIcon(
                        icon = leading.icon,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.Medium),
                    )
                }
            }
        }
        is TextFieldLeadingSlot.Custom -> {
            Box(modifier = modifier.padding(end = Spacing.SmallMedium)) {
                leading.content()
            }
        }
    }
}

/**
 * Renders the trailing accessory slot content for a [GlassTextField].
 */
@Composable
private fun TextFieldTrailingContent(
    trailing: TextFieldTrailingSlot,
    valueNotEmpty: Boolean,
    modifier: Modifier = Modifier,
) {
    when (trailing) {
        is TextFieldTrailingSlot.None -> {}
        is TextFieldTrailingSlot.Icon -> {
            Box(modifier = modifier.padding(start = Spacing.SmallMedium)) {
                if (trailing.onClick != null) {
                    GlassIconButton(
                        icon = trailing.icon,
                        onClick = trailing.onClick,
                        size = ComponentSize.TopBarButtonSize,
                    )
                } else {
                    GlassIcon(
                        icon = trailing.icon,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.Medium),
                    )
                }
            }
        }
        is TextFieldTrailingSlot.Clear -> {
            if (valueNotEmpty) {
                Box(modifier = modifier.padding(start = Spacing.SmallMedium)) {
                    GlassIconButton(
                        icon = AppIcons.Close,
                        onClick = trailing.onClear,
                        size = ComponentSize.TopBarButtonSize,
                    )
                }
            }
        }
        is TextFieldTrailingSlot.PasswordToggle -> {
            val iconModifier = Modifier
                .size(ComponentSize.TopBarButtonSize)
                .then(if (trailing.testTag != null) Modifier.testTag(trailing.testTag) else Modifier)
            Box(modifier = modifier.padding(start = Spacing.SmallMedium)) {
                IconButton(
                    onClick = trailing.onToggle,
                    modifier = iconModifier,
                ) {
                    val painter = if (trailing.isVisible) {
                        AppIcons.HidePainterResource()
                    } else {
                        AppIcons.ShowPainterResource()
                    }
                    Icon(
                        painter = painter,
                        contentDescription = if (trailing.isVisible) "Hide password" else "Show password",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.Medium),
                    )
                }
            }
        }
        is TextFieldTrailingSlot.Custom -> {
            Box(modifier = modifier.padding(start = Spacing.SmallMedium)) {
                trailing.content()
            }
        }
    }
}

/**
 * Displays validation error, warning, success, or helper text beneath a [GlassTextField].
 */
@Composable
private fun TextFieldHelperMessage(state: TextFieldState, modifier: Modifier = Modifier) {
    val message = when (state) {
        is TextFieldState.Error -> state.message
        is TextFieldState.Warning -> state.message
        is TextFieldState.Helper -> state.message
        is TextFieldState.Success -> state.message
        TextFieldState.Default -> null
    }
    val messageColor = when (state) {
        is TextFieldState.Error -> MaterialTheme.colorScheme.error
        is TextFieldState.Warning -> GlassTheme.diagnosticColors.warning
        is TextFieldState.Success -> GlassTheme.diagnosticColors.success
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    if (message != null) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = messageColor,
            modifier = modifier.padding(top = Spacing.ExtraSmall, start = Spacing.ExtraSmall),
        )
    }
}

/**
 * Dedicated search field with search icon and clear button.
 */
@Composable
fun GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    onSearch: (() -> Unit)? = null,
) {
    GlassTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        leading = TextFieldLeadingSlot.Icon(AppIcons.Search),
        trailing = if (value.isNotEmpty()) {
            TextFieldTrailingSlot.Clear {
                onValueChange("")
            }
        } else {
            TextFieldTrailingSlot.None
        },
        config = TextFieldConfig(
            singleLine = true,
            keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        ),
    )
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
        state = if (hint != null) TextFieldState.Error(hint) else TextFieldState.Default,
        trailing = TextFieldTrailingSlot.PasswordToggle(
            isVisible = showPassword,
            onToggle = { showPasswordChange(!showPassword) },
            testTag = showPasswordTestTag,
        ),
        config = TextFieldConfig(
            singleLine = singleLine,
            visualTransformation = when {
                !showPassword -> PasswordVisualTransformation()
                readOnly -> nonLetterColorVisualTransformation()
                else -> VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = keyboardActions,
        ),
        readOnly = readOnly,
    )
}

/**
 * OTP / PIN input field with glass styling.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun GlassOtpTextField(
    onOtpTextCorrectlyEntered: () -> Unit,
    modifier: Modifier = Modifier,
    realOtp: String = "",
    otpCount: Int = 4,
) {
    var otpText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
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
                val theme = GlassTheme.appTheme
                Row(horizontalArrangement = Arrangement.Center) {
                    repeat(otpCount) { index ->
                        val char = if (index >= otpText.length) "" else otpText[index].toString()
                        val filled = char.isNotEmpty()
                        val otpSurface = if (filled) colors.primaryContainer else colors.surfaceContainerHighest
                        val otpTint = if (filled) colors.primary else Color.Unspecified
                        val otpBoxSurfaceModifier = if (GlassTheme.effectsEnabled) {
                            Modifier.hazeGlass(
                                input = sceneHazeInput(GlassTheme.hazeState),
                                style = remember(theme, otpTint) {
                                    GlassMaterialStyles.baseStyle(
                                        theme = theme,
                                        opticalTint = otpTint,
                                    ).then {
                                        shape(GlassShapes.HazeSmall)
                                    }
                                },
                            )
                        } else {
                            Modifier.background(otpSurface, GlassShapes.HazeSmall)
                        }

                        Box(
                            modifier = Modifier
                                .size(ComponentSize.OtpBoxSize)
                                .padding(Spacing.ExtraSmall)
                                .then(otpBoxSurfaceModifier)
                                .clip(GlassShapes.HazeSmall)
                                .glassOutlineBorder(
                                    color = if (filled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    width = StrokeWidth.Standard,
                                    shape = GlassShapes.HazeSmall,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = char,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
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
    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
