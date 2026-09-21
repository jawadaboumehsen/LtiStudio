package com.kashif_e.backdrop.effects

import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.GammaAdjustmentShaderString
import com.kashif_e.backdrop.ReflectiveGlassShaderString
import com.kashif_e.backdrop.platform.PlatformCapabilities
import com.kashif_e.backdrop.platform.PlatformRenderEffect
import com.kashif_e.backdrop.platform.PlatformRuntimeShader
import com.kashif_e.backdrop.platform.asSkiaImageFilter
import androidx.compose.ui.graphics.asSkiaColorFilter
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix
import androidx.compose.ui.graphics.ColorFilter as ComposeColorFilter
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ImageFilter
import kotlin.math.pow

/**
 * Apply a color filter effect to the backdrop.
 */
internal fun BackdropEffectScope.colorFilter(colorFilter: ColorFilter) {
    if (!PlatformCapabilities.supportsColorFilter) return
    val colorFilterEffect = PlatformRenderEffect.colorFilter(colorFilter)
    applyEffect(colorFilterEffect)
}

/**
 * Apply an opacity/alpha adjustment to the backdrop.
 */
internal fun BackdropEffectScope.opacity(alpha: Float) {
    // 4x5 (20 floats): [R, G, B, A, translate] per row
    val matrix = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, alpha, 0f
    )
    colorFilter(makeSkiaColorMatrixFilter(matrix))
}

/**
 * Apply brightness, contrast, and saturation adjustments.
 */
internal fun BackdropEffectScope.colorControls(
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1f
) {
    if (brightness == 0f && contrast == 1f && saturation == 1f) {
        return
    }
    colorFilter(colorControlsColorFilter(brightness, contrast, saturation))
}

/**
 * Apply a vibrancy effect (increased saturation).
 */
internal fun BackdropEffectScope.vibrancy() {
    colorFilter(VibrantColorFilter)
}

private val VibrantColorFilter by lazy { colorControlsColorFilter(saturation = 1.5f) }

/**
 * Apply exposure adjustment (EV-based brightness scaling).
 */
internal fun BackdropEffectScope.exposureAdjustment(ev: Float) {
    val scale = 2f.pow(ev / 2.2f)
    // 4x5 (20 floats): [R, G, B, A, translate] per row
    val matrix = floatArrayOf(
        scale, 0f, 0f, 0f, 0f,
        0f, scale, 0f, 0f, 0f,
        0f, 0f, scale, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    colorFilter(makeSkiaColorMatrixFilter(matrix))
}

/**
 * Apply gamma adjustment.
 * Uses SkSL runtime shader to apply gamma correction to the backdrop.
 */
internal fun BackdropEffectScope.gammaAdjustment(power: Float) {
    if (!PlatformCapabilities.supportsRuntimeShader) return
    if (power == 1f) return  // No change needed
    
    val shader = PlatformRuntimeShader.create(GammaAdjustmentShaderString)
    if (shader != null) {
        shader.setFloatUniform("power", power)
        // Pass the current imageFilter as input so the shader samples from it
        val currentFilter = imageFilter
        val effect = shader.asRenderEffectWithInput(
            childShaderName = "content",
            input = currentFilter
        )
        // Set imageFilter directly since we're replacing the entire chain
        imageFilter = (effect as? PlatformRenderEffect.Skia)?.imageFilter
    }
}

/**
 * Applies a reflective glass effect to the backdrop.
 * 
 * Creates a mirror-like reflection with optional distortion and chromatic aberration,
 * simulating light reflecting off a curved glass surface.
 * 
 * Uses SkSL RuntimeShader for cross-platform support on iOS/Desktop/Web.
 */
internal fun BackdropEffectScope.reflectiveGlass(
    reflectionStrength: Float = 0.5f,
    distortionAmount: Float = 0.1f,
    chromaticAberration: Float = 0.02f,
    vignetteStrength: Float = 0.3f
) {
    if (!PlatformCapabilities.supportsRuntimeShader) return
    if (reflectionStrength <= 0f) return
    
    val shader = PlatformRuntimeShader.create(ReflectiveGlassShaderString)
    if (shader != null) {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("reflectionStrength", reflectionStrength.coerceIn(0f, 1f))
        shader.setFloatUniform("distortionAmount", distortionAmount.coerceIn(0f, 0.5f))
        shader.setFloatUniform("chromaticAberration", chromaticAberration.coerceIn(0f, 0.1f))
        shader.setFloatUniform("vignetteStrength", vignetteStrength.coerceIn(0f, 1f))
        
        // Pass the current imageFilter as input so the shader samples from it
        val currentFilter = imageFilter
        val effect = shader.asRenderEffectWithInput(
            childShaderName = "content",
            input = currentFilter
        )
        // Set imageFilter directly since we're replacing the entire chain
        imageFilter = (effect as? PlatformRenderEffect.Skia)?.imageFilter
    }
}

private fun colorControlsColorFilter(
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1f
): ColorFilter {
    val invSat = 1f - saturation
    val r = 0.213f * invSat
    val g = 0.715f * invSat
    val b = 0.072f * invSat

    val c = contrast
    // Skia ColorMatrix operates on normalized [0,1] values, NOT 0-255
    // The translation should be in the range -1 to 1
    val t = (0.5f - c * 0.5f + brightness)
    val s = saturation

    val cr = c * r
    val cg = c * g
    val cb = c * b
    val cs = c * s

    // Skia ColorMatrix is 4x5 (20 floats): [R, G, B, A, translate] per row
    val matrix = floatArrayOf(
        cr + cs, cg, cb, 0f, t,
        cr, cg + cs, cb, 0f, t,
        cr, cg, cb + cs, 0f, t,
        0f, 0f, 0f, 1f, 0f
    )
    return makeSkiaColorMatrixFilter(matrix)
}

private fun makeSkiaColorMatrixFilter(matrix: FloatArray): ColorFilter {
    return ComposeColorFilter.colorMatrix(ComposeColorMatrix(matrix)).asSkiaColorFilter()
}

/**
 * Internal helper to apply a platform effect to the current effect chain.
 */
internal fun BackdropEffectScope.applyEffect(effect: PlatformRenderEffect) {
    when (effect) {
        is PlatformRenderEffect.None -> return
        is PlatformRenderEffect.Skia -> {
            val current = imageFilter?.let { PlatformRenderEffect.Skia(it) }
            val merged = if (current != null) {
                PlatformRenderEffect.chain(effect, current)
            } else {
                effect
            }
            imageFilter = merged.asSkiaImageFilter()
        }
    }
}
