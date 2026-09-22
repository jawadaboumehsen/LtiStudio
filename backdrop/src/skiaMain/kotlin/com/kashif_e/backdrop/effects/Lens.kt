package com.kashif_e.backdrop.effects

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.LayoutDirection
import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.GlassDispersionShaderString
import com.kashif_e.backdrop.GlassRefractionShaderString
import com.kashif_e.backdrop.SurfaceProfile
import com.kashif_e.backdrop.platform.PlatformCapabilities
import com.kashif_e.backdrop.shaderIndex
import org.jetbrains.skia.ImageFilter
import kotlin.math.ceil
import kotlin.math.min

/**
 * Applies a lens refraction effect to the backdrop.
 * 
 * This effect creates a glass-like refraction that distorts the background content
 * based on the shape's edges, simulating light bending through a curved surface.
 * 
 * On iOS and Desktop, this uses a RuntimeShader that directly samples at displaced coordinates,
 * avoiding DisplacementMap artifacts and delivering authentic optical glass physics.
 * 
 * @param refractionHeight The height/depth of the refraction zone from the edges
 * @param refractionAmount The intensity of the refraction distortion
 * @param depthEffect Whether to include a radial depth component to the refraction
 * @param chromaticAberration Whether to add chromatic dispersion (color separation)
 * @param surfaceProfile Which displacement-vs-depth curve gives the bevel its character - see
 * [SurfaceProfile]. Defaults to [SurfaceProfile.Standard], byte-identical to this effect's
 * original curve.
 */
internal fun BackdropEffectScope.lens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Boolean = false,
    surfaceProfile: SurfaceProfile = SurfaceProfile.Standard
) {
    // Requires RuntimeShader support
    if (!PlatformCapabilities.supportsRuntimeShader) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    if (padding > 0f) {
        padding = ceil(maxOf(padding - refractionHeight, 0f))
    }

    val cornerRadii = cornerRadii
    val shaderString = if (chromaticAberration) {
        GlassDispersionShaderString
    } else {
        GlassRefractionShaderString
    }
    // Compiled once per element and reused across every redraw via obtainPlatformShader's cache
    // (keyed on the shader source itself, since both candidates are fixed constants) -
    // RuntimeEffect.makeForShader is a real SKSL compile, not a cheap call, and this function now
    // runs every animation frame during a BackdropInteractionController-driven hover/press
    // transition, not just once per composition.
    val builder = obtainPlatformShader(shaderString, shaderString).getBuilder() ?: return
    builder.uniform("size", size.width, size.height)
    builder.uniform("offset", -padding, -padding)
    builder.uniform("cornerRadii", cornerRadii[0], cornerRadii[1], cornerRadii[2], cornerRadii[3])
    builder.uniform("refractionHeight", refractionHeight)
    builder.uniform("refractionAmount", refractionAmount)
    builder.uniform("depthEffect", if (depthEffect) 1f else 0f)
    builder.uniform("surfaceProfile", surfaceProfile.shaderIndex)
    if (chromaticAberration) {
        builder.uniform("chromaticAberration", 0.018f)
    }
    
    // Create RuntimeShader-based image filter that samples content at displaced coords
    val currentFilter = imageFilter
    val lensFilter = ImageFilter.makeRuntimeShader(
        runtimeShaderBuilder = builder,
        shaderNames = arrayOf("content"),
        inputs = arrayOf(currentFilter),
    )
    
    // Only apply if filter creation succeeded
    if (lensFilter != null) {
        imageFilter = lensFilter
    }
}

private val BackdropEffectScope.cornerRadii: FloatArray
    get() {
        val size = size
        val maxRadius = size.minDimension / 2f
        val shape = shape
        val shapeClassName = shape::class.simpleName ?: ""
        if (shapeClassName.contains("Capsule") || shapeClassName.contains("Circle")) {
            return FloatArray(4) { maxRadius }
        }
        if (shapeClassName.contains("Rectangle") && !shapeClassName.contains("Rounded")) {
            return FloatArray(4) { 0f }
        }
        val cornerShape = shape as? CornerBasedShape ?: return FloatArray(4) { maxRadius }
        val isLtr = layoutDirection == LayoutDirection.Ltr
        val topLeft =
            if (isLtr) cornerShape.topStart.toPx(size, this)
            else cornerShape.topEnd.toPx(size, this)
        val topRight =
            if (isLtr) cornerShape.topEnd.toPx(size, this)
            else cornerShape.topStart.toPx(size, this)
        val bottomRight =
            if (isLtr) cornerShape.bottomEnd.toPx(size, this)
            else cornerShape.bottomStart.toPx(size, this)
        val bottomLeft =
            if (isLtr) cornerShape.bottomStart.toPx(size, this)
            else cornerShape.bottomEnd.toPx(size, this)
        return floatArrayOf(
            min(topLeft, maxRadius),
            min(topRight, maxRadius),
            min(bottomRight, maxRadius),
            min(bottomLeft, maxRadius)
        )
    }
