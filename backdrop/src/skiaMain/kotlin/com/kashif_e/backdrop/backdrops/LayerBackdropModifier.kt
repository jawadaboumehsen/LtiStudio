package com.kashif_e.backdrop.backdrops

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import com.kashif_e.backdrop.recordLayer

actual fun Modifier.layerBackdrop(backdrop: LayerBackdrop): Modifier =
    this then LayerBackdropElement(backdrop)

private class LayerBackdropElement(
    val backdrop: LayerBackdrop
) : ModifierNodeElement<LayerBackdropNode>() {

    override fun create(): LayerBackdropNode {
        return LayerBackdropNode(backdrop)
    }

    override fun update(node: LayerBackdropNode) {
        node.backdrop = backdrop
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "layerBackdrop"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayerBackdropElement) return false

        if (backdrop != other.backdrop) return false

        return true
    }

    override fun hashCode(): Int {
        return backdrop.hashCode()
    }
}

private class LayerBackdropNode(
    var backdrop: LayerBackdrop
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    // Must stay auto-invalidating (the default). LayerBackdropElement.equals() only compares
    // the LayerBackdrop instance, not the content drawn into it - and callers are expected to
    // share one stable LayerBackdrop instance across recompositions (see LtiTheme's
    // sharedBackdrop param), so that equals() check is always true across a recomposition that
    // changes only the drawn content (e.g. a dark/light theme flip changes the colors this
    // Canvas draws, not the LayerBackdrop identity). With shouldAutoInvalidate = false,
    // update()/invalidateDraw() then never fire for that case, so draw() - and therefore the
    // recordLayer() call every glass surface samples from - never reruns, leaving every
    // glass-backed surface showing the old theme's frozen pixels until something unrelated
    // (e.g. a window resize) forces a layout-driven redraw regardless of this flag.
    override val shouldAutoInvalidate: Boolean = true

    override fun ContentDrawScope.draw() {
        drawContent()
        recordLayer(backdrop.graphicsLayer) { backdrop.onDraw(this@draw) }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.layerCoordinates = coordinates
        }
    }

    override fun onDetach() {
        backdrop.layerCoordinates = null
    }
}
