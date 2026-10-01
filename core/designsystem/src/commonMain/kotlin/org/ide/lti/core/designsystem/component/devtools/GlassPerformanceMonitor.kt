/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.devtools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.primitives.GlassSurface
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import kotlin.math.roundToInt

/**
 * Performance metrics for glass rendering.
 */
@Stable
data class GlassPerformanceMetrics(
    /**
     * Current frames per second.
     */
    val fps: Float = 0f,

    /**
     * Average frame time in milliseconds.
     */
    val avgFrameTimeMs: Float = 0f,

    /**
     * Maximum frame time in the last second (worst case).
     */
    val maxFrameTimeMs: Float = 0f,

    /**
     * Minimum frame time in the last second (best case).
     */
    val minFrameTimeMs: Float = Float.MAX_VALUE,

    /**
     * Number of frames dropped in the last second.
     */
    val droppedFrames: Int = 0,

    /**
     * Current shader cache size.
     */
    val shaderCacheSize: Int = 0,

    /**
     * Number of backdrop instances currently active.
     */
    val activeBackdrops: Int = 0,

    /**
     * Total memory used by glass effects (estimated, in KB).
     */
    val memoryUsageKB: Long = 0L,

    /**
     * Number of glass components currently rendered.
     */
    val componentsRendered: Int = 0,
) {
    /**
     * Returns a performance rating based on FPS and frame times.
     * 0 = Poor, 1 = Fair, 2 = Good, 3 = Excellent
     */
    val performanceRating: Int
        get() = when {
            fps >= 55f && avgFrameTimeMs <= 18f -> 3 // Excellent
            fps >= 45f && avgFrameTimeMs <= 25f -> 2 // Good
            fps >= 30f && avgFrameTimeMs <= 35f -> 1 // Fair
            else -> 0 // Poor
        }
}

/**
 * Color representing a given [GlassPerformanceMetrics.performanceRating].
 */
@Composable
private fun performanceColorFor(rating: Int): Color {
    val diagnostics = GlassTheme.diagnosticColors
    return when (rating) {
        3 -> diagnostics.success
        2, 1 -> diagnostics.warning
        else -> diagnostics.error
    }
}

/**
 * Collector for glass performance metrics.
 */
class GlassPerformanceCollector {
    private val frameTimes = mutableListOf<Long>()
    private var lastResetTime = System.currentTimeMillis()
    private val maxSamples = 120 // ~2 seconds at 60 FPS

    /**
     * Records a frame render time.
     */
    fun recordFrame(frameTimeMs: Long) {
        frameTimes.add(frameTimeMs)
        if (frameTimes.size > maxSamples) {
            frameTimes.removeAt(0)
        }

        // Reset every second
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastResetTime >= 1000) {
            lastResetTime = currentTime
        }
    }

    /**
     * Computes current metrics from collected data.
     */
    fun computeMetrics(shaderCacheSize: Int, activeBackdrops: Int, componentsRendered: Int): GlassPerformanceMetrics {
        if (frameTimes.isEmpty()) {
            return GlassPerformanceMetrics(
                shaderCacheSize = shaderCacheSize,
                activeBackdrops = activeBackdrops,
                componentsRendered = componentsRendered,
            )
        }

        val avgFrameTime = frameTimes.average().toFloat()
        val maxFrameTime = frameTimes.maxOrNull()?.toFloat() ?: 0f
        val minFrameTime = frameTimes.minOrNull()?.toFloat() ?: 0f
        val fps = if (avgFrameTime > 0) 1000f / avgFrameTime else 0f

        // Count dropped frames (frames that took longer than 16.67ms = 60 FPS target)
        val droppedFrames = frameTimes.count { it > 16.67 }

        // Estimate memory usage (very rough approximation)
        val memoryPerBackdrop = 512L // KB per backdrop
        val memoryPerComponent = 64L // KB per glass component
        val memoryUsageKB = (activeBackdrops * memoryPerBackdrop) +
            (componentsRendered * memoryPerComponent)

        return GlassPerformanceMetrics(
            fps = fps,
            avgFrameTimeMs = avgFrameTime,
            maxFrameTimeMs = maxFrameTime,
            minFrameTimeMs = minFrameTime,
            droppedFrames = droppedFrames,
            shaderCacheSize = shaderCacheSize,
            activeBackdrops = activeBackdrops,
            memoryUsageKB = memoryUsageKB,
            componentsRendered = componentsRendered,
        )
    }

    /**
     * Clears all collected metrics.
     */
    fun reset() {
        frameTimes.clear()
        lastResetTime = System.currentTimeMillis()
    }
}

/**
 * Overlay widget that displays real-time glass performance metrics.
 *
 * This developer tool shows FPS, frame times, shader cache usage, and other
 * performance indicators. It can be toggled on/off and positioned in different
 * corners of the screen.
 *
 * @param visible Whether the monitor is visible
 * @param modifier Modifier for the monitor overlay
 * @param position Position on screen (TopStart, TopEnd, BottomStart, BottomEnd)
 * @param collector The performance collector instance
 */
@Composable
fun GlassPerformanceMonitor(
    visible: Boolean,
    modifier: Modifier = Modifier,
    position: MonitorPosition = MonitorPosition.TopEnd,
    collector: GlassPerformanceCollector = remember { GlassPerformanceCollector() },
) {
    if (!visible) return

    val componentsRendered = remember { mutableStateOf(0) }

    // Simulate frame rendering and collect metrics
    var metrics by remember { mutableStateOf(GlassPerformanceMetrics()) }

    LaunchedEffect(collector) {
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameNanos > 0L) {
                    val frameDurationNanos = frameTimeNanos - lastFrameNanos
                    val frameDurationMs = frameDurationNanos / 1_000_000L
                    collector.recordFrame(frameDurationMs)
                }
                lastFrameNanos = frameTimeNanos
            }
            metrics = collector.computeMetrics(
                // Estimated counters: SkSL RuntimeEffect shaders on Skia/Desktop are compiled
                // per-effect on demand and backdrop layers are scoped locally without a global count registry.
                shaderCacheSize = 0,
                activeBackdrops = 1,
                componentsRendered = componentsRendered.value,
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = position.alignment,
    ) {
        PerformanceOverlay(
            metrics = metrics,
            modifier = modifier.padding(Spacing.Medium),
        )
    }
}

/**
 * The actual performance overlay UI.
 */
@Composable
private fun PerformanceOverlay(metrics: GlassPerformanceMetrics, modifier: Modifier = Modifier) {
    GlassSurface(
        modifier = modifier.width(ComponentSize.PerformanceMonitorWidth),
        shape = GlassShapes.HazeCard,
        borderColor = MaterialTheme.colorScheme.outline,
        borderWidth = StrokeWidth.Hairline,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.SmallMedium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            // Title
            Text(
                "Glass Performance",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            MetricRow(
                label = "FPS",
                value = "${metrics.fps.roundToInt()}",
                valueColor = performanceColorFor(metrics.performanceRating),
            )

            MetricRow(
                label = "Frame Time",
                value = "${metrics.avgFrameTimeMs.roundToInt()}ms",
                valueColor = MaterialTheme.colorScheme.onSurface,
            )

            MetricRow(
                label = "Min / Max",
                value = "${metrics.minFrameTimeMs.roundToInt()} / ${metrics.maxFrameTimeMs.roundToInt()}ms",
                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val droppedColor = if (metrics.droppedFrames > 0) {
                GlassTheme.diagnosticColors.error
            } else {
                GlassTheme.diagnosticColors.success
            }
            MetricRow(
                label = "Dropped",
                value = "${metrics.droppedFrames}",
                valueColor = droppedColor,
            )

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StrokeWidth.Standard)
                    .background(MaterialTheme.colorScheme.outline),
            )

            MetricRow(
                label = "Shader Cache",
                value = "${metrics.shaderCacheSize}",
                valueColor = MaterialTheme.colorScheme.onSurface,
            )

            MetricRow(
                label = "Backdrops",
                value = "${metrics.activeBackdrops}",
                valueColor = MaterialTheme.colorScheme.onSurface,
            )

            MetricRow(
                label = "Components",
                value = "${metrics.componentsRendered}",
                valueColor = MaterialTheme.colorScheme.onSurface,
            )

            MetricRow(
                label = "Memory",
                value = "${metrics.memoryUsageKB} KB",
                valueColor = MaterialTheme.colorScheme.onSurface,
            )

            // Performance rating indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ComponentSize.ProgressBarHeight)
                    .background(
                        color = performanceColorFor(metrics.performanceRating),
                        shape = GlassShapes.Capsule,
                    ),
            )
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = codeFontFamily(),
            ),
            color = valueColor,
        )
    }
}

/**
 * Position of the performance monitor on screen.
 */
enum class MonitorPosition(val alignment: Alignment) {
    TopStart(Alignment.TopStart),
    TopEnd(Alignment.TopEnd),
    BottomStart(Alignment.BottomStart),
    BottomEnd(Alignment.BottomEnd),
}

/**
 * Example usage of GlassPerformanceMonitor.
 */
@Composable
fun GlassPerformanceMonitorExample(modifier: Modifier = Modifier) {
    var showMonitor by remember { mutableStateOf(true) }

    Box(modifier = modifier.fillMaxSize()) {
        // Your app content
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
        ) {
            GlassButton(onClick = { showMonitor = !showMonitor }) {
                Text(if (showMonitor) "Hide Monitor" else "Show Monitor")
            }

            GlassCard {
                Text(
                    "Glass components being rendered...",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Performance monitor overlay
        GlassPerformanceMonitor(
            visible = showMonitor,
            position = MonitorPosition.TopEnd,
        )
    }
}
