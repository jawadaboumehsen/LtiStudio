/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.debloat

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.ide.lti.core.domain.debloat.VectorIcons
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import java.io.File

/**
 * Draws a `.vec` bundle ([VectorIcons]) at [px] pixels: its layers in order, an adaptive icon cut to its middle 72
 * of 108 units in a rounded square as a launcher shows it. Null when the bundle holds no drawable.
 */
internal fun drawVectorIcon(bundle: File, px: Int): ImageBitmap? {
    val icon = VectorIcons.parse(bundle.readText()) ?: return null
    val surface = Surface.makeRasterN32Premul(px, px)
    val canvas = surface.canvas
    val size = px.toFloat()
    val box = if (icon.adaptive) {
        val scale = size / ADAPTIVE_SHOWN
        val margin = (ADAPTIVE_FULL - ADAPTIVE_SHOWN) / 2 * scale
        canvas.clipRRect(RRect.makeXYWH(0f, 0f, size, size, size * CORNER), true)
        Rect.makeXYWH(-margin, -margin, ADAPTIVE_FULL * scale, ADAPTIVE_FULL * scale)
    } else {
        Rect.makeWH(size, size)
    }
    icon.layers.forEach { layer ->
        val d = box.width * layer.inset
        val r = Rect.makeLTRB(box.left + d, box.top + d, box.right - d, box.bottom - d)
        when (layer) {
            is VectorIcons.Layer.Fill -> canvas.drawRect(r, Paint().apply { color = layer.argb.toInt() })

            is VectorIcons.Layer.Bitmap -> runCatching {
                canvas.drawImageRect(Image.makeFromEncoded(File(bundle.parentFile, layer.file).readBytes()), r)
            }

            is VectorIcons.Layer.Svg -> runCatching {
                val dom = SVGDOM(Data.makeFromBytes(layer.svg.encodeToByteArray()))
                dom.setContainerSize(r.width, r.height)
                canvas.save()
                canvas.translate(r.left, r.top)
                dom.render(canvas)
                canvas.restore()
            }
        }
    }
    return surface.makeImageSnapshot().toComposeImageBitmap()
}

private const val ADAPTIVE_FULL = 108f
private const val ADAPTIVE_SHOWN = 72f
private const val CORNER = 0.22f
