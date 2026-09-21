package com.kashif_e.backdrop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.colorControls
import com.kashif_e.backdrop.effects.frostedGrain
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.progressiveBlur
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.platform.PlatformRuntimeShader
import org.jetbrains.skia.ImageFilter
import kotlin.math.min

/**
 * Skia-backed scope for rendering a [ResolvedGlassEffect]: provides access to the element's live
 * size/shape/density and the Skia `ImageFilter` chain the effect DSL functions
 * (`com.kashif_e.backdrop.effects.*`) build up. Plain desktop-only class - `:backdrop` only ever
 * builds the Skiko target, so this no longer needs an `expect`/`actual` split.
 *
 * Never part of any public signature outside `:backdrop`: every caller now hands
 * [Modifier.drawBackdrop]/[Modifier.drawPlainBackdrop] a [ResolvedGlassEffect] value instead of a
 * `BackdropEffectScope.() -> Unit` lambda, so the effect DSL functions this scope drives are
 * exclusively an implementation detail of [DrawBackdropNode.updateEffects].
 */
internal sealed interface BackdropEffectScope : Density {

    val size: Size

    val layoutDirection: LayoutDirection

    val shape: Shape

    /** Padding applied around the backdrop for effects that extend beyond bounds. */
    var padding: Float

    /** The Skia ImageFilter to apply to the backdrop. */
    var imageFilter: ImageFilter?

    /** Obtain a cached runtime shader for the given key and source. */
    fun obtainPlatformShader(key: String, source: String): PlatformRuntimeShader
}

internal abstract class BackdropEffectScopeImpl : BackdropEffectScope {

    override var density: Float = 1f
    override var fontScale: Float = 1f
    override var size: Size = Size.Unspecified
    override var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    override var padding: Float = 0f
    override var imageFilter: ImageFilter? = null

    private val shaderCache = mutableMapOf<String, PlatformRuntimeShader>()

    override fun obtainPlatformShader(key: String, source: String): PlatformRuntimeShader {
        return shaderCache.getOrPut(key) {
            PlatformRuntimeShader.compile(source)
        }
    }

    fun update(scope: DrawScope): Boolean {
        val newDensity = scope.density
        val newFontScale = scope.fontScale
        val newSize = scope.size
        val newLayoutDirection = scope.layoutDirection

        val changed = newDensity != density ||
            newFontScale != fontScale ||
            newSize != size ||
            newLayoutDirection != layoutDirection

        if (changed) {
            density = newDensity
            fontScale = newFontScale
            size = newSize
            layoutDirection = newLayoutDirection
        }

        return changed
    }

    /**
     * Renders [resolved] against this scope's current (live, post-layout) [size] - the
     * `OpticalSizeValue.resolve(shortestSide)` step Haze's own resolved-style objects perform
     * inside their visual effect, not at the style-authoring call site. Same conditionals
     * `GlassStyle.toEffects()` used to apply: skip `colorControls`/`vibrancy` at identity
     * brightness/saturation, skip `blur`/`lens` when resolved to zero, skip `frostedGrain` at
     * zero grain, skip the progressive-blur band entirely when absent.
     */
    fun applyResolved(resolved: ResolvedGlassEffect) {
        padding = 0f
        imageFilter = null

        val shortestSide = min(size.width, size.height).toDp()

        if (resolved.brightness != 0f || resolved.saturation != 1f) {
            colorControls(brightness = resolved.brightness, saturation = resolved.saturation)
        }
        if (resolved.vibrancy) vibrancy()

        val resolvedBlur = resolved.blur.resolve(shortestSide)
        if (resolvedBlur > 0.dp) blur(resolvedBlur.toPx())

        val resolvedLensHeight = resolved.lensHeight.resolve(shortestSide)
        val resolvedLensAmount = resolved.lensAmount.resolve(shortestSide)
        if (resolvedLensHeight > 0.dp || resolvedLensAmount > 0.dp) {
            lens(
                resolvedLensHeight.toPx(),
                resolvedLensAmount.toPx(),
                depthEffect = resolved.lensDepthEffect,
                chromaticAberration = resolved.lensChromaticAberration,
            )
        }

        if (resolved.grain > 0f) frostedGrain(resolved.grain)

        resolved.progressiveBlur?.let { band ->
            progressiveBlur(
                blurRadius = band.blurRadiusPx,
                tintColor = band.tintColor,
                tintIntensity = band.tintIntensity,
                fadeStart = band.fadeStart,
                fadeEnd = band.fadeEnd,
            )
        }
    }

    fun reset() {
        density = 1f
        fontScale = 1f
        size = Size.Unspecified
        layoutDirection = LayoutDirection.Ltr
        padding = 0f
        imageFilter = null
        shaderCache.clear()
    }
}
