package com.softsynth.ksyn.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softsynth.ksyn.unitgen.ScopeProbe
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.exp
import kotlin.time.TimeSource

private val CHANNEL_COLORS = listOf(
    Color(0xFF4FC3F7), // cyan-blue  — channel 0
    Color(0xFFFFB74D), // amber      — channel 1
    Color(0xFF81C784), // green      — channel 2
    Color(0xFFE57373), // coral red  — channel 3
    Color(0xFFCE93D8), // purple     — channel 4
    Color(0xFFFFF176), // yellow     — channel 5
)

private val COLOR_BG    = Color(0xFF0A0A1A)
private val COLOR_GRID  = Color(0x33FFFFFF)
private val COLOR_ZERO  = Color(0x66FFFFFF)
private val COLOR_AXIS  = Color(0x66FFFFFF)
private val COLOR_LABEL = Color(0xCCFFFFFF)

/**
 * Peak-hold auto-ranger for the vertical axis.
 *
 * Tracks the absolute peak so the range stays symmetric around zero. The peak jumps up
 * immediately when a frame exceeds it, so the trace is never clipped. When the signal
 * shrinks, the peak holds for [holdSeconds] and then decays exponentially toward the
 * current frame's peak with time constant [releaseSeconds].
 */
private class ScopeAutoRange(
    private val holdSeconds: Float,
    private val releaseSeconds: Float,
) {
    var peak = 0f
        private set
    private var age = 0f
    private var initialized = false

    fun update(framePeak: Float, dtSeconds: Float) {
        if (!initialized || framePeak >= peak) {
            peak = framePeak
            age = 0f
            initialized = true
            return
        }
        age += dtSeconds
        if (age > holdSeconds) {
            val decay = exp(-dtSeconds / releaseSeconds)
            peak = framePeak + (peak - framePeak) * decay
        }
    }
}

/**
 * Oscilloscope display for a [ScopeProbe].
 *
 * Each channel is drawn in a distinct color (see [CHANNEL_COLORS]).
 * The Y axis is auto-ranged symmetrically around zero, so 0.0 is always centered.
 * It expands instantly to fit the frame being displayed,
 * holds for [holdTimeSeconds] when the signal gets quieter, then shrinks slowly over
 * roughly [releaseTimeSeconds]. The visible span never drops below [minRange], which limits
 * the maximum gain so that quiet noise stays small. The range is rounded out to "nice"
 * values and labelled on a vertical axis.
 * The display refreshes at approximately [refreshRateMs] milliseconds.
 *
 * @param probe             The [ScopeProbe] to display.
 * @param refreshRateMs     Poll interval in milliseconds (default 100 ms ≈ 10 fps).
 * @param holdTimeSeconds   How long a peak holds the range before it starts to shrink.
 * @param releaseTimeSeconds Time constant for shrinking the range after the hold.
 * @param minRange          Smallest vertical span displayed, i.e. the maximum gain.
 */
@Composable
fun Oscilloscope(
    probe: ScopeProbe,
    modifier: Modifier = Modifier,
    refreshRateMs: Long = 100L,
    holdTimeSeconds: Float = 2.0f,
    releaseTimeSeconds: Float = 5.0f,
    minRange: Float = 0.1f,
) {
    var displayData by remember(probe) {
        mutableStateOf(Array(probe.numChannels) { FloatArray(probe.displayBufferSize) })
    }
    var rangePeak by remember { mutableFloatStateOf(1f) }
    val autoRange = remember(probe, holdTimeSeconds, releaseTimeSeconds) {
        ScopeAutoRange(holdTimeSeconds, releaseTimeSeconds)
    }
    val textMeasurer = rememberTextMeasurer()

    // Poll the probe for newly captured frames.
    LaunchedEffect(probe, autoRange) {
        var lastMark = TimeSource.Monotonic.markNow()
        while (true) {
            delay(refreshRateMs)
            if (probe.isDisplayReady) {
                // Snapshot all channels before clearing the ready flag.
                val snapshot = Array(probe.numChannels) { ch ->
                    probe.displayBuffer[ch].copyOf()
                }
                probe.isDisplayReady = false   // re-arm: volatile write acts as barrier

                // Range from the frame we are about to draw, so it can never be clipped.
                var framePeak = 0f
                for (samples in snapshot) {
                    for (s in samples) {
                        val a = abs(s)
                        if (a > framePeak) framePeak = a
                    }
                }
                val now = TimeSource.Monotonic.markNow()
                val dtSeconds = (now - lastMark).inWholeMicroseconds / 1_000_000f
                lastMark = now
                autoRange.update(framePeak, dtSeconds)

                rangePeak = autoRange.peak
                displayData = snapshot
            }
        }
    }

    // Symmetric around zero, with a minimum span to limit the maximum gain.
    val halfSpan = maxOf(rangePeak, minRange * 0.5f)
    val scale = calculateExpandedNiceScale(-halfSpan, halfSpan, targetTicks = 4)

    val data    = displayData
    val bufSize = probe.displayBufferSize

    Canvas(modifier = modifier.background(COLOR_BG)) {
        val labelStyle = TextStyle(color = COLOR_LABEL, fontSize = 10.sp)
        val labels = scale.ticks.map { textMeasurer.measure(formatNiceNumber(it, scale.step), labelStyle) }
        val tickLength = 4.dp.toPx()
        val labelGap = 2.dp.toPx()
        val maxLabelWidth = labels.maxOfOrNull { it.size.width } ?: 0
        val plotLeft = maxLabelWidth + labelGap + tickLength
        // Leave room so the top and bottom labels are not cut off.
        val halfLabelHeight = (labels.firstOrNull()?.size?.height ?: 0) / 2f
        val plotTop = halfLabelHeight
        val plotBottom = size.height - halfLabelHeight
        val plotW = size.width - plotLeft
        val plotH = plotBottom - plotTop
        if (plotW <= 1f || plotH <= 1f) return@Canvas

        val range = (scale.max - scale.min).let { if (it < 1e-6f) 1f else it }
        fun toPixelY(value: Float): Float = plotBottom - (value - scale.min) / range * plotH

        // Grid lines, tick marks and labels at each nice value.
        scale.ticks.forEachIndexed { index, v ->
            val y = toPixelY(v)
            val isZero = kotlin.math.abs(v) < scale.step * 1e-3f
            drawLine(
                if (isZero) COLOR_ZERO else COLOR_GRID,
                Offset(plotLeft, y), Offset(size.width, y), strokeWidth = 1f
            )
            drawLine(COLOR_AXIS, Offset(plotLeft - tickLength, y), Offset(plotLeft, y), strokeWidth = 1f)
            val layout = labels[index]
            drawText(
                layout,
                topLeft = Offset(
                    plotLeft - tickLength - labelGap - layout.size.width,
                    y - layout.size.height / 2f
                )
            )
        }
        drawLine(COLOR_AXIS, Offset(plotLeft, plotTop), Offset(plotLeft, plotBottom), strokeWidth = 1f)

        // Waveform path for each channel.
        for (ch in 0 until probe.numChannels) {
            val samples = data[ch]
            if (samples.isEmpty() || bufSize < 2) continue
            val color = CHANNEL_COLORS[ch % CHANNEL_COLORS.size]
            val path = Path()
            for (i in 0 until bufSize) {
                val x = plotLeft + i.toFloat() / (bufSize - 1) * plotW
                val y = toPixelY(samples[i]).coerceIn(plotTop, plotBottom)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = color, style = Stroke(width = 1.5f))
        }
    }
}
