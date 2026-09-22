package com.kashif_e.backdrop.effects

import androidx.compose.ui.graphics.Color
import com.kashif_e.backdrop.BackdropEffectScope
import org.jetbrains.skia.ImageFilter

private const val PROGRESSIVE_BLUR_SKSL = """
uniform shader content;
uniform float2 size;
uniform half4 tint;
uniform float tintIntensity;
uniform float fadeStart;
uniform float fadeEnd;

half4 main(float2 coord) {
    float normalizedY = coord.y / size.y;
    float blurAlpha = smoothstep(fadeStart, fadeEnd, normalizedY);
    float tintAlpha = smoothstep(fadeStart, fadeEnd, normalizedY);
    half4 blurred = content.eval(coord) * blurAlpha;
    return mix(blurred, tint * tintAlpha, tintIntensity);
}
"""

internal fun BackdropEffectScope.progressiveBlur(
    blurRadius: Float,
    tintColor: Color = Color.Transparent,
    tintIntensity: Float = 0f,
    fadeStart: Float = 1f,
    fadeEnd: Float = 0.5f
) {
    // First apply the blur
    blur(blurRadius)

    // Then apply the alpha mask shader. Compiled once per element and reused across every redraw
    // via obtainPlatformShader's cache (keyed on the shader source itself, a fixed constant) -
    // this used to build a fresh RuntimeShaderBuilder from RuntimeEffect.makeForShader on every
    // call, bypassing the cache lens()/frostedGrain() already route through even though
    // GlassScrollEdgeFade's progressive band recomputes every scroll frame.
    val builder = obtainPlatformShader(PROGRESSIVE_BLUR_SKSL, PROGRESSIVE_BLUR_SKSL).getBuilder() ?: return
    builder.uniform("size", size.width, size.height)
    builder.uniform("tint", tintColor.red, tintColor.green, tintColor.blue, tintColor.alpha)
    builder.uniform("tintIntensity", tintIntensity)
    builder.uniform("fadeStart", fadeStart)
    builder.uniform("fadeEnd", fadeEnd)

    // Use ImageFilter.makeRuntimeShader to apply the effect
    // Note: This requires the content as a child shader, which ImageFilter.makeRuntimeShader handles
    val filter = ImageFilter.makeRuntimeShader(
        runtimeShaderBuilder = builder,
        shaderNames = arrayOf("content"),
        inputs = arrayOf(null) // null means use the previous filter result
    )

    effect(filter)
}
