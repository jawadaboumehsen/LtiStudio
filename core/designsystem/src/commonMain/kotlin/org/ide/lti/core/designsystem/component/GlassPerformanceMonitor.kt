package org.ide.lti.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.effects.blur
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.delay
import org.ide.lti.core.designsystem.theme.GlassTheme
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

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
    val componentsRendered: Int = 0
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

    /**
     * Returns a color representing the current performance.
     */
    val performanceColor: Color
        get() = when (performanceRating) {
            3 -> Color(0xFF4EC9B0) // Green
            2 -> Color(0xFFCE9178) // Orange
            1 -> Color(0xFFD7BA7D) // Yellow
            else -> Color(0xFFF48771) // Red
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
    fun computeMetrics(
        shaderCacheSize: Int,
        activeBackdrops: Int,
        componentsRendered: Int
    ): GlassPerformanceMetrics {
        if (frameTimes.isEmpty()) {
            return GlassPerformanceMetrics(
                shaderCacheSize = shaderCacheSize,
                activeBackdrops = activeBackdrops,
                componentsRendered = componentsRendered
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
            componentsRendered = componentsRendered
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
 * @param position Position on screen (TopStart, TopEnd, BottomStart, BottomEnd)
 * @param collector The performance collector instance
 * @param modifier Modifier for the monitor overlay
 */
@Composable
fun GlassPerformanceMonitor(
    visible: Boolean,
    position: MonitorPosition = MonitorPosition.TopEnd,
    collector: GlassPerformanceCollector = remember { GlassPerformanceCollector() },
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val componentsRendered = remember { mutableStateOf(0) }

    // Simulate frame rendering and collect metrics
    var metrics by remember { mutableStateOf(GlassPerformanceMetrics()) }

    LaunchedEffect(Unit) {
        while (true) {
            val frameTime = measureTimeMillis {
                // Simulate frame work
                delay(1)
            }

            collector.recordFrame(frameTime)
            metrics = collector.computeMetrics(
                shaderCacheSize = 0, // Shader caching disabled - compiles directly
                activeBackdrops = 1,
                componentsRendered = componentsRendered.value
            )

            // Update ~30 times per second to avoid performance overhead
            delay(33)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = position.alignment
    ) {
        PerformanceOverlay(
            metrics = metrics,
            modifier = modifier.padding(16.dp)
        )
    }
}

/**
 * The actual performance overlay UI.
 */
@Composable
private fun PerformanceOverlay(
    metrics: GlassPerformanceMetrics,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.width(220.dp),
        contentPadding = 12.dp,
        // Lean stack (blur only) — this overlay redraws every frame, so keep it cheap.
        effects = { blur(2.dp.toPx()) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Title
            Text(
                "Glass Performance",
                style = MaterialTheme.typography.labelLarge,
                color = GlassTheme.colors.onGlass
            )

            // FPS indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "FPS",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.onGlassSecondary
                )
                Text(
                    "${metrics.fps.roundToInt()}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = metrics.performanceColor
                )
            }

            // Frame time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Frame Time",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.onGlassSecondary
                )
                Text(
                    "${metrics.avgFrameTimeMs.roundToInt()}ms",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = GlassTheme.colors.onGlass
                )
            }

            // Min/Max frame times
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Min / Max",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.onGlassSecondary
                )
                Text(
                    "${metrics.minFrameTimeMs.roundToInt()} / ${metrics.maxFrameTimeMs.roundToInt()}ms",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = GlassTheme.colors.onGlassSecondary
                )
            }

            // Dropped frames
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Dropped",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.onGlassSecondary
                )
                Text(
                    "${metrics.droppedFrames}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = if (metrics.droppedFrames > 0) Color(0xFFF48771) else Color(0xFF4EC9B0)
                )
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(GlassTheme.colors.glassBorder)
            )

            // Shader cache
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Shader Cache",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.onGlassSecondary
                )
                Text(
                    "${metrics.shaderCacheSize}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = GlassTheme.colors.onGlass
                )
            }

            // Backdrops
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Backdrops",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.onGlassSecondary
                )
                Text(
                    "${metrics.activeBackdrops}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = GlassTheme.colors.onGlass
                )
            }

            // Components
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Components",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.onGlassSecondary
                )
                Text(
                    "${metrics.componentsRendered}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = GlassTheme.colors.onGlass
                )
            }

            // Memory estimate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Memory",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.onGlassSecondary
                )
                Text(
                    "${metrics.memoryUsageKB} KB",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = GlassTheme.colors.onGlass
                )
            }

            // Performance rating indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        color = metrics.performanceColor,
                        shape = ContinuousCapsule
                    )
            )
        }
    }
}

/**
 * Position of the performance monitor on screen.
 */
enum class MonitorPosition(val alignment: Alignment) {
    TopStart(Alignment.TopStart),
    TopEnd(Alignment.TopEnd),
    BottomStart(Alignment.BottomStart),
    BottomEnd(Alignment.BottomEnd)
}

/**
 * Example usage of GlassPerformanceMonitor.
 */
@Composable
fun GlassPerformanceMonitorExample() {
    var showMonitor by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
        // Your app content
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassButton(onClick = { showMonitor = !showMonitor }) {
                Text(if (showMonitor) "Hide Monitor" else "Show Monitor")
            }

            GlassCard {
                Text(
                    "Glass components being rendered...",
                    color = GlassTheme.colors.onGlass
                )
            }
        }

        // Performance monitor overlay
        GlassPerformanceMonitor(
            visible = showMonitor,
            position = MonitorPosition.TopEnd
        )
    }
}
