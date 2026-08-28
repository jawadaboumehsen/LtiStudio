package org.ide.lti.core.designsystem.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import com.kyant.capsule.ContinuousCapsule
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LensRefraction
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.utils.InteractiveHighlight
import org.ide.lti.core.designsystem.utils.applyInteractiveHighlightTransform
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun GlassIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    icon: @Composable () -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val glassColors = GlassTheme.colors
    val actualBackdrop = backdrop ?: GlassTheme.backdrop ?: rememberLayerBackdrop()

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope
        )
    }

    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = actualBackdrop,
                shape = { ContinuousCapsule },
                effects = {
                    vibrancy()
                    blur(BackdropBlur.Button.toPx())
                    lens(LensRefraction.Button.height.toPx(), LensRefraction.Button.amount.toPx())
                },
                highlight = { Highlight.Plain },
                layerBlock = if (isInteractive) {
                    {
                        applyInteractiveHighlightTransform(interactiveHighlight)
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    drawRect(if (surfaceColor.isSpecified) surfaceColor else glassColors.glassSurface)
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = AlphaTokens.IconTint))
                    }
                }
            )
            .border(width = StrokeWidth.Hairline, color = glassColors.glassBorder, shape = ContinuousCapsule)
            .clickable(
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
            .then(
                if (isInteractive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Preview
@Composable
fun GlassIconPreview() {
    val backdrop = rememberLayerBackdrop()
    GlassIcon(
        onClick = {},
        backdrop = backdrop,
        icon = {
//            androidx.compose.foundation.Image(
//                painter = androidx.compose.ui.res.painterResource("ic_launcher.png"), // Replace with your actual icon
//                contentDescription = "Preview Icon",
//                modifier = androidx.compose.ui.Modifier.size(24.dp)
//            )
            Icons.Default.Folder
        }
    )
}
