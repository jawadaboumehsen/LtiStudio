package com.kashif_e.backdrop.backdrops

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.InverseLayerScope

@Composable
actual fun rememberLayerBackdrop(
    onDraw: ContentDrawScope.() -> Unit
): LayerBackdrop {
    val graphicsLayer = rememberGraphicsLayer()
    return remember(graphicsLayer) {
        LayerBackdrop(graphicsLayer, onDraw)
    }.also {
        it.onDraw = onDraw
    }
}

@Stable
actual class LayerBackdrop internal constructor(
    val graphicsLayer: GraphicsLayer,
    initialOnDraw: ContentDrawScope.() -> Unit
) : Backdrop {

    // Must be snapshot state: draw() reads this outside of composition, and a plain var
    // mutation from rememberLayerBackdrop() (see .also { it.onDraw = onDraw }) would not
    // invalidate the node's draw phase, silently re-drawing stale content otherwise.
    internal var onDraw: ContentDrawScope.() -> Unit by mutableStateOf(initialOnDraw)

    actual override val isCoordinatesDependent: Boolean = true

    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)

    private var inverseLayerScope: InverseLayerScope? = null

    actual override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val currentLayerCoordinates = layerCoordinates
        val bothAttached = currentLayerCoordinates != null && coordinates != null &&
            currentLayerCoordinates.isAttached && coordinates.isAttached
        val offset = if (!bothAttached) {
            // Not (yet) laid out on screen - e.g. the first frame before the initial layout
            // pass, or a coordinate not yet re-attached after a structural change. Draw
            // unshifted rather than not at all; see LayerBackdropModifier.kt's onDetach for
            // why we no longer null out layerCoordinates as soon as this becomes detached.
            Offset.Zero
        } else {
            try {
                currentLayerCoordinates!!.localPositionOf(coordinates!!)
            } catch (_: Exception) {
                try {
                    coordinates!!.positionInWindow() - currentLayerCoordinates!!.positionInWindow()
                } catch (_: Exception) {
                    Offset.Zero
                }
            }
        }
        withTransform({
            if (layerBlock != null) {
                with(obtainInverseLayerScope()) { inverseTransform(density, layerBlock) }
            }
            translate(-offset.x, -offset.y)
        }) {
            drawLayer(graphicsLayer)
        }
    }

    private fun obtainInverseLayerScope(): InverseLayerScope {
        return inverseLayerScope?.apply { reset() }
            ?: InverseLayerScope().also { inverseLayerScope = it }
    }
}
