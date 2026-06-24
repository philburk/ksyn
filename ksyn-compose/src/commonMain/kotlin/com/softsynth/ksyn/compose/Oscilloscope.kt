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
import com.softsynth.ksyn.unitgen.ScopeProbe
import kotlinx.coroutines.delay

private val CHANNEL_COLORS = listOf(
    Color(0xFF4FC3F7), // cyan-blue  — channel 0
    Color(0xFFFFB74D), // amber      — channel 1
    Color(0xFF81C784), // green      — channel 2
    Color(0xFFE57373), // coral red  — channel 3
    Color(0xFFCE93D8), // purple     — channel 4
    Color(0xFFFFF176), // yellow     — channel 5
)

private val COLOR_BG   = Color(0xFF0A0A1A)
private val COLOR_GRID = Color(0x33FFFFFF)

/**
 * Oscilloscope display for a [ScopeProbe].
 *
 * Each channel is drawn in a distinct color (see [CHANNEL_COLORS]).
 * The Y axis is auto-ranged from the probe's detected signal minimum and maximum.
 * The display refreshes at approximately [refreshRateMs] milliseconds.
 *
 * @param probe        The [ScopeProbe] to display.
 * @param refreshRateMs  Poll interval in milliseconds (default 100 ms ≈ 10 fps).
 */
@Composable
fun Oscilloscope(
    probe: ScopeProbe,
    modifier: Modifier = Modifier,
    refreshRateMs: Long = 100L,
) {
    var displayData by remember(probe) {
        mutableStateOf(Array(probe.numChannels) { FloatArray(probe.displayBufferSize) })
    }
    var rangeMin by remember { mutableFloatStateOf(-1f) }
    var rangeMax by remember { mutableFloatStateOf(1f) }

    // Poll the probe for newly captured frames.
    LaunchedEffect(probe) {
        while (true) {
            delay(refreshRateMs)
            if (probe.isDisplayReady) {
                // Snapshot all channels before clearing the ready flag.
                val snapshot = Array(probe.numChannels) { ch ->
                    probe.displayBuffer[ch].copyOf()
                }
                probe.isDisplayReady = false   // re-arm: volatile write acts as barrier
                rangeMin = probe.autoRangeMin
                rangeMax = probe.autoRangeMax
                displayData = snapshot
            }
        }
    }

    val data    = displayData
    val bufSize = probe.displayBufferSize

    Canvas(modifier = modifier.background(COLOR_BG)) {
        val w = size.width
        val h = size.height
        val range = (rangeMax - rangeMin).let { if (it < 1e-6f) 1f else it }

        // Horizontal grid lines at 25 %, 50 %, 75 % height.
        for (line in 1..3) {
            val y = h * line / 4f
            drawLine(COLOR_GRID, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        // Waveform path for each channel.
        for (ch in 0 until probe.numChannels) {
            val samples = data[ch]
            if (samples.isEmpty() || bufSize < 2) continue
            val color = CHANNEL_COLORS[ch % CHANNEL_COLORS.size]
            val path = Path()
            for (i in 0 until bufSize) {
                val x = i.toFloat() / (bufSize - 1) * w
                val normalized = (samples[i] - rangeMin) / range
                val y = (h - normalized * h).coerceIn(0f, h)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = color, style = Stroke(width = 1.5f))
        }
    }
}
