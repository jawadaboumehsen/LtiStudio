@file:Suppress("MatchingDeclarationName")

package org.ide.lti.core.designsystem.component.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.IdeShellShapes
import org.ide.lti.core.designsystem.theme.LetterSpacing
import org.ide.lti.core.designsystem.theme.Spacing

@Immutable
data class IdeNavigatorItem(
    val id: String,
    val label: String,
    val icon: String? = null,
    val badge: String? = null,
    val enabled: Boolean = true,
)

/**
 * 230 dp wide workspace navigator with section items and a contextual footer.
 */
@Composable
fun IdeNavigatorPanel(
    headerTitle: String,
    headerSubtitle: String?,
    items: List<IdeNavigatorItem>,
    selectedItemId: String?,
    onSelectItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
    footerContent: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .width(IdeShellDimens.WorkspaceNavigatorWidth)
            .fillMaxHeight()
            .background(colors.navigatorBackground)
            .padding(vertical = Spacing.Small),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = headerTitle.uppercase(),
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GlassFontFamily.code(),
                    letterSpacing = LetterSpacing.TrackingHeader,
                )
                if (headerSubtitle != null) {
                    Text(
                        text = headerSubtitle,
                        color = colors.textDim,
                        fontSize = FontSize.Micro,
                        fontFamily = GlassFontFamily.code(),
                    )
                }
            }

            Spacer(Modifier.height(Spacing.ExtraSmall))

            // Navigation Items
            items.forEach { item ->
                NavigatorItemRow(
                    item = item,
                    isSelected = item.id == selectedItemId,
                    onSelectItem = onSelectItem,
                    colors = colors,
                )
            }
        }

        // Contextual Footer
        if (footerContent != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    thickness = IdeShellDimens.HairlineBorder,
                    color = colors.borderMuted,
                )
                Box(modifier = Modifier.fillMaxWidth().padding(Spacing.Small)) {
                    footerContent()
                }
            }
        }
    }
}

@Composable
private fun NavigatorItemRow(
    item: IdeNavigatorItem,
    isSelected: Boolean,
    onSelectItem: (String) -> Unit,
    colors: IdeShellColorScheme,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val bg = when {
        !item.enabled -> Color.Transparent
        isSelected -> colors.controlSelected
        isPressed -> colors.controlHover
        isHovered -> colors.controlHover.copy(alpha = AlphaTokens.Prominent)
        else -> Color.Transparent
    }

    val contentColor = when {
        !item.enabled -> colors.textDim.copy(alpha = AlphaTokens.Disabled)
        isSelected -> colors.textMain
        isHovered -> colors.textMain
        else -> colors.textMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Small, vertical = Spacing.Hairline)
            .height(IdeShellDimens.NavigatorItemHeight)
            .clip(IdeShellShapes.Control)
            .background(bg)
            .testTag("NavigatorItem_${item.id}")
            .semantics {
                role = Role.Tab
                selected = isSelected
                contentDescription = item.label
                if (!item.enabled) disabled()
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = item.enabled,
                onClick = { onSelectItem(item.id) },
            )
            .padding(horizontal = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            val icon = resolveNavIcon(item.id)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) colors.accentBlue else contentColor,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(
                text = item.label,
                color = contentColor,
                fontSize = FontSize.BodySmall,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                fontFamily = GlassFontFamily.ide(),
            )
        }

        if (item.badge != null) {
            Text(
                text = item.badge,
                color = colors.textDim,
                fontSize = FontSize.Micro,
                fontFamily = GlassFontFamily.code(),
            )
        }
    }
}

private fun resolveNavIcon(id: String): ImageVector = when (id.lowercase()) {
    "overview" -> Icons.Default.Dashboard
    "rom_configuration", "rom config" -> Icons.Default.Settings
    "run_history", "run history" -> Icons.Default.History
    "artifacts" -> Icons.Default.Archive
    "target_profile", "target profile" -> Icons.Default.PhoneAndroid
    else -> Icons.Default.Dashboard
}
