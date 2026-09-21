package com.kashif_e.backdrop.effects

import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.GlassGrainShaderString
import com.kashif_e.backdrop.platform.PlatformCapabilities
import com.kashif_e.backdrop.platform.PlatformRenderEffect
import com.kashif_e.backdrop.platform.PlatformRuntimeShader

/**
 * Apply frosted glass micro-grain dither to the backdrop effect stack.
 * 
 * Simulates the microscopic tactile texture of etched/sandblasted physical glass
 * and eliminates 8-bit color banding steps on deep blurred gradients.
 * 
 * @param intensity Perceptual noise intensity (typically 0.01f to 0.03f, default: 0.015f).
 */
internal fun BackdropEffectScope.frostedGrain(intensity: Float = 0.015f) {
    if (!PlatformCapabilities.supportsRuntimeShader) return
    if (intensity <= 0f) return

    val shader = PlatformRuntimeShader.create(GlassGrainShaderString) ?: return
    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("intensity", intensity.coerceIn(0f, 0.1f))

    val currentFilter = imageFilter
    val effect = shader.asRenderEffectWithInput(
        childShaderName = "content",
        input = currentFilter
    )
    imageFilter = (effect as? PlatformRenderEffect.Skia)?.imageFilter
}
