/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Isolated prototype verification test for the 4eckme / ShaderToy superquadric 3D liquid lens shader.
 *
 * Validates:
 * 1. SkSL compilation on Skia (Desktop JVM).
 * 2. True interior magnification & displacement (> 0px) through Snell's law refraction.
 * 3. Grazing Fresnel highlight peak along the 8th-order squircle rim.
 * 4. Zero distortion outside the squircle boundary.
 */
class LiquidLensShaderPrototypeTest {

    companion object {
        private const val LIQUID_LENS_SK_SL = """
        uniform shader content;
        uniform float2 resolution;
        uniform float2 thumbCenter;
        uniform float2 thumbHalfSize;
        uniform float ior;
        uniform float highlight;
        uniform float nz;
        uniform float pwr;
        uniform float3 tint;

        float superF(float2 p, float2 a) {
            float2 n = abs(p) / a;
            return pow(n.x, pwr) + pow(n.y, pwr) - 1.0;
        }

        float2 superGrad(float2 p, float2 a) {
            float2 s = sign(p);
            float2 n = abs(p) / a;
            return pwr * pow(n, float2(pwr - 1.0)) * s / a;
        }

        float3 blur9(float2 coord, float2 pixelStep) {
            float3 c = content.eval(coord).rgb * 4.0;
            c += content.eval(coord + float2( pixelStep.x, 0.0)).rgb;
            c += content.eval(coord + float2(-pixelStep.x, 0.0)).rgb;
            c += content.eval(coord + float2(0.0,  pixelStep.y)).rgb;
            c += content.eval(coord + float2(0.0, -pixelStep.y)).rgb;
            c += content.eval(coord + float2( pixelStep.x,  pixelStep.y)).rgb;
            c += content.eval(coord + float2(-pixelStep.x, -pixelStep.y)).rgb;
            c += content.eval(coord + float2( pixelStep.x, -pixelStep.y)).rgb;
            c += content.eval(coord + float2(-pixelStep.x,  pixelStep.y)).rgb;
            return c / 12.0;
        }

        half4 main(float2 fragCoord) {
            float2 dir = fragCoord - thumbCenter;
            float f = superF(dir, thumbHalfSize);
            float2 g2 = superGrad(dir, thumbHalfSize);
            float sd = f / max(length(g2), 0.0001);
            float inside = smoothstep(0.015 * thumbHalfSize.x, 0.0, sd);

            half4 base = content.eval(fragCoord);
            if (inside <= 0.0) {
                return base;
            }

            float3 normal = normalize(float3(g2.x, -g2.y, nz));
            float3 I = float3(0.0, 0.0, -1.0);
            float3 R = refract(I, normal, ior);
            float2 refrCoord = fragCoord + R.xy * (thumbHalfSize.x * 0.45);

            float3 scene = blur9(refrCoord, float2(1.5, 1.5));
            float fresnel = pow(clamp(1.0 + dot(I, normal), 0.0, 1.0), 12.0);

            float3 glass = scene * tint + fresnel * highlight;
            return half4(mix(base.rgb, glass, inside), 1.0);
        }
        """
    }

    @Test
    fun testSkSLCompilesCleanlyOnSkia() {
        val effect = RuntimeEffect.makeForShader(LIQUID_LENS_SK_SL)
        assertNotNull(effect, "SkSL shader must compile successfully on Skia runtime")
    }

    @Test
    fun testLiquidLensRefractionDisplacementAndFresnelPeak() {
        val width = 200
        val height = 100
        val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL)

        // 1. Create a high-frequency striped backdrop (vertical stripes of width 10px: 0x000000 vs 0xFFFFFF)
        val backdropBitmap = Bitmap().apply { allocPixels(info) }
        val backdropCanvas = Canvas(backdropBitmap)
        val stripePaintA = Paint().apply { color = 0xFF202020.toInt() }
        val stripePaintB = Paint().apply { color = 0xFFE0E0E0.toInt() }

        for (x in 0 until width step 10) {
            val paint = if ((x / 10) % 2 == 0) stripePaintA else stripePaintB
            backdropCanvas.drawRect(org.jetbrains.skia.Rect.makeXYWH(x.toFloat(), 0f, 10f, height.toFloat()), paint)
        }
        val backdropImage = Image.makeFromBitmap(backdropBitmap)
        val backdropShader = backdropImage.makeShader()

        // 2. Configure RuntimeShader with 4eckme liquid lens optics
        val effect = RuntimeEffect.makeForShader(LIQUID_LENS_SK_SL)
        val builder = RuntimeShaderBuilder(effect)
        val thumbCenter = floatArrayOf(100f, 50f)
        val thumbHalfSize = floatArrayOf(28f, 28f)

        builder.child("content", backdropShader)
        builder.uniform("resolution", width.toFloat(), height.toFloat())
        builder.uniform("thumbCenter", thumbCenter[0], thumbCenter[1])
        builder.uniform("thumbHalfSize", thumbHalfSize[0], thumbHalfSize[1])
        builder.uniform("ior", 0.65f) // Corrected from 10.0 to prevent Total Internal Reflection
        builder.uniform("highlight", 0.45f)
        builder.uniform("nz", 1.5f)
        builder.uniform("pwr", 8.0f)
        builder.uniform("tint", 1.05f, 1.10f, 1.25f)

        val outputBitmap = Bitmap().apply { allocPixels(info) }
        val outputCanvas = Canvas(outputBitmap)
        val paint = Paint().apply { shader = builder.makeShader() }
        outputCanvas.drawRect(org.jetbrains.skia.Rect.makeXYWH(0f, 0f, width.toFloat(), height.toFloat()), paint)

        // 3. Verify zero distortion outside the lens boundary (e.g. at x = 20, y = 50)
        val unrefractedOutside = backdropBitmap.getColor(20, 50)
        val outputOutside = outputBitmap.getColor(20, 50)
        assertEquals(
            unrefractedOutside,
            outputOutside,
            "Pixels outside the squircle lens must remain 100% identical to the base backdrop",
        )

        // 4. Verify interior refraction and displacement:
        // Inside the lens (radius ~ 15px from center), the striped boundary at x = 90 or x = 110 should be warped!
        var totalInteriorDivergence = 0.0
        var sampledPixels = 0
        for (y in 40..60) {
            for (x in 90..110) {
                val cOriginal = backdropBitmap.getColor(x, y)
                val cRefracted = outputBitmap.getColor(x, y)
                val deltaR = abs(((cOriginal ushr 16) and 0xFF) - ((cRefracted ushr 16) and 0xFF))
                val deltaG = abs(((cOriginal ushr 8) and 0xFF) - ((cRefracted ushr 8) and 0xFF))
                val deltaB = abs((cOriginal and 0xFF) - (cRefracted and 0xFF))
                totalInteriorDivergence += (deltaR + deltaG + deltaB) / (3.0 * 255.0)
                sampledPixels++
            }
        }
        val meanInteriorDiff = totalInteriorDivergence / sampledPixels
        println(
            "[PROTOTYPE VERIFICATION] Mean Interior Lens Delta: $meanInteriorDiff " +
                "(over $sampledPixels interior pixels)",
        )
        assertTrue(
            meanInteriorDiff > 0.05,
            "Interior of the 3D liquid lens must visibly refract and displace underlying stripes " +
                "(found $meanInteriorDiff)",
        )

        // 5. Verify grazing Fresnel specular highlight:
        // Along the squircle edge (at distance ~ 26px from center, e.g. at (100, 24)), normal tilts steeply,
        // causing dot(I, normal) -> 0 and fresnel -> 1.0, boosting RGB brightness.
        val rimColor = outputBitmap.getColor(100, 24)
        val rimR = ((rimColor ushr 16) and 0xFF) / 255.0f
        val rimG = ((rimColor ushr 8) and 0xFF) / 255.0f
        val rimB = (rimColor and 0xFF) / 255.0f
        val rimLuminance = 0.2126f * rimR + 0.7152f * rimG + 0.0722f * rimB

        println(
            "[PROTOTYPE VERIFICATION] Grazing Rim Luminance (Fresnel Glint): $rimLuminance (R=$rimR, G=$rimG, B=$rimB)",
        )
        assertTrue(
            rimLuminance > 0.30f,
            "Grazing rim must exhibit specular Fresnel highlight peak > 0.30 (found $rimLuminance)",
        )
    }
}
