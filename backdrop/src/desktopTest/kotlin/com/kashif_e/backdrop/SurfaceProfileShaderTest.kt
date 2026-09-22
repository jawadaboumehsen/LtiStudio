package com.kashif_e.backdrop

import com.kashif_e.backdrop.platform.PlatformRuntimeShader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The SKSL in [GlassRefractionShaderString]/[GlassDispersionShaderString] is a plain string
 * constant at Kotlin-compile time - Skia only validates its syntax when actually compiled at
 * runtime, so [SurfaceProfile]'s per-profile curves (SurfaceProfileCurves in Shaders.kt) need a
 * real compile smoke test, not just a Kotlin build, to catch an SKSL typo.
 */
class SurfaceProfileShaderTest {

    @Test
    fun refractionShaderCompiles() {
        val shader = PlatformRuntimeShader.compile(GlassRefractionShaderString)
        assertNotNull(shader.getBuilder(), "GlassRefractionShaderString (with SurfaceProfileCurves) must compile")
    }

    @Test
    fun dispersionShaderCompiles() {
        val shader = PlatformRuntimeShader.compile(GlassDispersionShaderString)
        assertNotNull(shader.getBuilder(), "GlassDispersionShaderString (with SurfaceProfileCurves) must compile")
    }

    @Test
    fun everySurfaceProfileHasADistinctShaderIndex() {
        val indices = SurfaceProfile.entries.map { it.shaderIndex }
        assertEquals(indices.size, indices.distinct().size, "every SurfaceProfile must map to a distinct shader index")
    }

    @Test
    fun standardIsTheFirstOrdinalSoItStaysTheDefault() {
        assertEquals(0f, SurfaceProfile.Standard.shaderIndex)
    }
}
