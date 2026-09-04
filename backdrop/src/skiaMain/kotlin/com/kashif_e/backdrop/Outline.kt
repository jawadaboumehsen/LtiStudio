package com.kashif_e.backdrop

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.Rect

internal actual fun Canvas.clipOutline(outline: Outline, path: Path?) {
    val skiaCanvas = this.nativeCanvas
    when (outline) {
        is Outline.Rectangle -> {
            val r = outline.rect
            skiaCanvas.clipRect(Rect.makeLTRB(r.left, r.top, r.right, r.bottom), ClipMode.INTERSECT, true)
        }
        is Outline.Rounded -> {
            val p = path ?: Path()
            p.rewind()
            p.addRoundRect(outline.roundRect)
            skiaCanvas.clipPath(p.asSkiaPath(), ClipMode.INTERSECT, true)
        }
        is Outline.Generic -> {
            skiaCanvas.clipPath(outline.path.asSkiaPath(), ClipMode.INTERSECT, true)
        }
    }
}
