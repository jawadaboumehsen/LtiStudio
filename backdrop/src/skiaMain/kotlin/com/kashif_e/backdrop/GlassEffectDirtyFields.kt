package com.kashif_e.backdrop

/**
 * Per-field dirty bitmask for [ResolvedGlassEffect], ported from Haze's `BlurDirtyFields`/
 * `GlassDirtyFields`. [DrawBackdropNode.update] diffs the previous resolved effect against the
 * new one through [diff] and only rebuilds the Skia effect graph when the result intersects
 * [RenderEffectAffectingFlags] - today that is every field (this engine has a single render
 * stage), but the named bits keep the shape Haze uses when a future stage split only cares about
 * a subset.
 */
internal object GlassEffectDirtyFields {
    const val Blur = 1 shl 0
    const val LensHeight = 1 shl 1
    const val LensAmount = 1 shl 2
    const val LensDepthEffect = 1 shl 3
    const val LensChromaticAberration = 1 shl 4
    const val Vibrancy = 1 shl 5
    const val Brightness = 1 shl 6
    const val Saturation = 1 shl 7
    const val Grain = 1 shl 8
    const val ProgressiveBlur = 1 shl 9

    const val RenderEffectAffectingFlags = Blur or LensHeight or LensAmount or LensDepthEffect or
        LensChromaticAberration or Vibrancy or Brightness or Saturation or Grain or ProgressiveBlur

    /** `old == null` (first attach) is treated as every field having changed. */
    fun diff(old: ResolvedGlassEffect?, new: ResolvedGlassEffect): Bitmask {
        if (old == null) return Bitmask() + RenderEffectAffectingFlags
        if (old == new) return Bitmask()
        var mask = Bitmask()
        if (old.blur != new.blur) mask += Blur
        if (old.lensHeight != new.lensHeight) mask += LensHeight
        if (old.lensAmount != new.lensAmount) mask += LensAmount
        if (old.lensDepthEffect != new.lensDepthEffect) mask += LensDepthEffect
        if (old.lensChromaticAberration != new.lensChromaticAberration) mask += LensChromaticAberration
        if (old.vibrancy != new.vibrancy) mask += Vibrancy
        if (old.brightness != new.brightness) mask += Brightness
        if (old.saturation != new.saturation) mask += Saturation
        if (old.grain != new.grain) mask += Grain
        if (old.progressiveBlur != new.progressiveBlur) mask += ProgressiveBlur
        return mask
    }

    fun stringify(mask: Bitmask): String = buildList {
        if (Blur in mask) add("Blur")
        if (LensHeight in mask) add("LensHeight")
        if (LensAmount in mask) add("LensAmount")
        if (LensDepthEffect in mask) add("LensDepthEffect")
        if (LensChromaticAberration in mask) add("LensChromaticAberration")
        if (Vibrancy in mask) add("Vibrancy")
        if (Brightness in mask) add("Brightness")
        if (Saturation in mask) add("Saturation")
        if (Grain in mask) add("Grain")
        if (ProgressiveBlur in mask) add("ProgressiveBlur")
    }.joinToString(prefix = "[", postfix = "]")
}
