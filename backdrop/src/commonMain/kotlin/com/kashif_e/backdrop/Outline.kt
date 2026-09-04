package com.kashif_e.backdrop

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path

/**
 * Clip the canvas to the given outline shape.
 */
internal expect fun Canvas.clipOutline(outline: Outline, path: Path?)
