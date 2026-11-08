/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.debloat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VectorIconsTest {
    private val ns = "http://schemas.android.com/apk/res/android"

    // aapt2 dump xmltree of PQ84P01's GamePluginTrigger icon and the drawables it refers to (paths shortened).
    private val bundle = """
        == root
        N: android=$ns (line=2)
          E: adaptive-icon (line=2)
              E: background (line=3)
                A: $ns:drawable(0x01010199)=@0x7f0806f8
              E: foreground (line=4)
                A: $ns:drawable(0x01010199)=@0x7f0806f9
        == 0x7f0806f8 color #ff4688f2
        == 0x7f0806f9 xml
        N: android=$ns (line=2)
          E: inset (line=2)
            A: $ns:inset(0x010104b5)=0.300000%
              E: layer-list (line=6)
                  E: item (line=7)
                      E: vector (line=8)
                        A: $ns:viewportWidth(0x01010402)=70
                        A: $ns:viewportHeight(0x01010403)=70
                          E: group (line=9)
                            A: $ns:translateX(0x010104b9)=5
                              E: path (line=13)
                                A: $ns:fillColor(0x01010404)=@0x0106000b
                                A: $ns:pathData(0x01010405)="M0,0L10,10Z" (Raw: "M0,0L10,10Z")
                              E: path (line=14)
                                A: $ns:fillColor(0x01010404)=#80ff0000
                                A: $ns:fillType(0x0101051e)=1
                                A: $ns:pathData(0x01010405)="M1,1L2,2Z" (Raw: "M1,1L2,2Z")
                  E: item (line=20)
                    A: $ns:drawable(0x01010199)=@0x7f08075a
        == 0x7f08075a img ab-12.img
    """.trimIndent()

    @Test
    fun anAdaptiveIconBecomesItsLayersInOrder() {
        val icon = VectorIcons.parse(bundle)!!
        assertTrue(icon.adaptive)
        val (bg, vector, bitmap) = icon.layers
        assertEquals(VectorIcons.Layer.Fill(0xFF4688F2L), bg)
        assertEquals(VectorIcons.Layer.Bitmap("ab-12.img", 0.3f), bitmap)
        val svg = (vector as VectorIcons.Layer.Svg).svg
        assertEquals(0.3f, vector.inset)
        assertTrue("viewBox=\"0 0 70.0 70.0\"" in svg, svg)
        assertTrue("translate(5.0 0.0)" in svg, svg)
        assertTrue("d=\"M0,0L10,10Z\" fill=\"#ffffff\" fill-opacity=\"1.0\"" in svg, svg)
        assertTrue("fill=\"#ff0000\" fill-opacity=\"${128 / 255f}\" fill-rule=\"evenodd\"" in svg, svg)
    }

    @Test
    fun aBundleWithoutADrawableIsNoIcon() {
        assertNull(VectorIcons.parse("== root\nN: android=$ns (line=2)\n  E: shape (line=2)\n"))
        assertNull(VectorIcons.parse(""))
    }
}
