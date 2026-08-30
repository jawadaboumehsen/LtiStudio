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
        if (node.backdrop != backdrop) {
            if (backdrop.layerCoordinates == null) {
                backdrop.layerCoordinates = node.backdrop.layerCoordinates
            }
        }
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

    override val shouldAutoInvalidate: Boolean = false

    override fun ContentDrawScope.draw() {
        drawContent()
        recordLayer(backdrop.graphicsLayer) { backdrop.onDraw(this@draw) }
        // drawRevision is bumped by the composable that owns this backdrop's content (via
        // SideEffect, tied to recomposition), NOT here. draw() re-runs every frame the scene
        // repaints for any reason - bumping a mutableStateOf on every draw call is itself a
        // state write, which schedules another frame, whose draw() bumps it again: a
        // self-sustaining infinite render loop (confirmed: it hung the screenshot test suite's
        // waitForIdle() indefinitely and pegged a CPU core forever). See CosmicAuroraBackdrop's
        // SideEffect for the correct, composition-scoped place to signal a real content change.
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.layerCoordinates = coordinates
        }
    }
}
