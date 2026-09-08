package com.softsynth.ksyn.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softsynth.ksyn.data.SegmentedEnvelope
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round

private const val MIN_DURATION = 0.001f

private const val MIN_ZOOM_TIME = 0.1f
private const val MAX_ZOOM_TIME = 500f
private const val MIN_ZOOM_VALUE = 1.0f
private const val MAX_ZOOM_VALUE = 10000f

private data class EnvFrame(val duration: Float, val value: Float)
private data class DragReadout(
    val time: Float,
    val value: Float,
    val px: Float,
    val py: Float,
    val isBelow: Boolean = false
)

private enum class EditMode(val label: String) {
    Points("Points"),
    SustainLoop("Sustain"),
    ReleaseLoop("Release")
}

private val COLOR_BG          = Color(0xFF141426)
private val COLOR_PLOT_BG     = Color(0xFF1A1A2E)
private val COLOR_GRID        = Color(0x28FFFFFF)
private val COLOR_AXIS        = Color(0x66FFFFFF)
private val COLOR_LINE        = Color(0xFF4FC3F7)
private val COLOR_VERTEX_FILL = Color(0xFFFF7043)
private val COLOR_SUSTAIN_BG  = Color(0x33FFCA28)
private val COLOR_SUSTAIN_BAR = Color(0xAAFFCA28)
private val COLOR_RELEASE_BG  = Color(0x33CE93D8)
private val COLOR_RELEASE_BAR = Color(0xAACE93D8)

private fun formatNumber(value: Float, decimals: Int): String {
    var factor = 1f
    repeat(decimals) { factor *= 10f }
    val rounded = round(value * factor) / factor
    val str = rounded.toString()
    if (decimals <= 0) {
        return str.substringBefore('.')
    }
    val parts = str.split('.')
    val intPart = parts[0]
    val fracPart = if (parts.size > 1) parts[1] else ""
    val paddedFrac = fracPart.padEnd(decimals, '0').take(decimals)
    return "$intPart.$paddedFrac"
}

/**
 * Paul Heckbert's "Nice Numbers" algorithm for finding clean, comfortable graph tick intervals.
 */
private fun niceNum(range: Double, round: Boolean): Double {
    val exponent = kotlin.math.floor(kotlin.math.log10(range))
    val fraction = range / 10.0.pow(exponent)
    val niceFraction: Double = if (round) {
        if (fraction < 1.5) 1.0
        else if (fraction < 3.0) 2.0
        else if (fraction < 7.0) 5.0
        else 10.0
    } else {
        if (fraction <= 1.0) 1.0
        else if (fraction <= 2.0) 2.0
        else if (fraction <= 5.0) 5.0
        else 10.0
    }
    return niceFraction * 10.0.pow(exponent)
}

private data class NiceScale(val min: Float, val max: Float, val step: Float, val ticks: List<Float>)

private fun calculateNiceScale(min: Float, max: Float, targetTicks: Int = 4): NiceScale {
    val range = (max - min).toDouble()
    if (range <= 0.0) {
        return NiceScale(min, max, 1f, listOf(min))
    }
    val step = niceNum(range / targetTicks.toDouble(), round = true).toFloat()
    if (step <= 0f) {
        return NiceScale(min, max, 1f, listOf(min, max))
    }

    val eps = step * 0.001f
    val ticks = mutableListOf<Float>()
    var t = (kotlin.math.floor(min / step) * step)
    while (t <= max + eps) {
        if (t >= min - eps) {
            ticks.add(t)
        }
        t += step
    }
    return NiceScale(min, max, step, ticks)
}

private fun formatNiceNumber(value: Float, step: Float): String {
    val roundedVal = round(value * 10000f) / 10000f
    val v = if (abs(roundedVal) < 1e-6f) 0f else roundedVal
    val decimals = when {
        step >= 1f -> 0
        step >= 0.1f -> 1
        step >= 0.01f -> 2
        else -> 3
    }
    return formatNumber(v, decimals)
}

private fun formatDragTime(time: Float, step: Float): String {
    val roundedVal = round(time * 100000f) / 100000f
    val v = if (abs(roundedVal) < 1e-6f) 0f else roundedVal
    val decimals = when {
        step >= 50f -> 0
        step >= 5f -> 1
        step >= 0.05f -> 2 // Provides 2 decimals (e.g. 0.25s) when step is ~0.1-2s
        step >= 0.005f -> 3
        else -> 4
    }
    return "${formatNumber(v, decimals)}s"
}

private fun formatDragValue(value: Float, step: Float): String {
    val roundedVal = round(value * 100000f) / 100000f
    val v = if (abs(roundedVal) < 1e-6f) 0f else roundedVal
    val decimals = when {
        step >= 50f -> 0
        step >= 5f -> 1
        step >= 0.05f -> 2
        step >= 0.005f -> 3
        else -> 4
    }
    return formatNumber(v, decimals)
}

private fun progressToScale(p: Float, min: Float, max: Float): Float {
    return min * (max / min).toDouble().pow(p.toDouble()).toFloat()
}

private fun scaleToProgress(value: Float, min: Float, max: Float): Float {
    if (value <= min) return 0f
    if (value >= max) return 1f
    return (ln(value / min) / ln(max / min)).coerceIn(0f, 1f)
}

/**
 * An interactive breakpoint editor for a SegmentedEnvelope.
 *
 * Editing modes available via toolbar:
 *  - Points: drag vertices to edit duration/value; Shift+click deletes; click empty space inserts.
 *  - Sustain: drag sideways to set sustainBegin/sustainEnd frame indices.
 *  - Release: drag sideways to set releaseBegin/releaseEnd frame indices.
 *
 * Loop regions are visualised as a tinted rectangle (begin≠end) or a full-height bar (begin==end).
 *
 * @param envelope         The SegmentedEnvelope to edit. Modified in place on every gesture.
 * @param minValue         Minimum displayable value (maps to the bottom edge).
 * @param maxValue         Default maximum displayable value (maps to the top edge).
 * @param maxTime          Default total visible time in seconds (maps to the right edge).
 * @param showToolbar      Whether to show the mode-selection toolbar.
 * @param showZoomControls Whether to show the zoom faders for expanding time and value range.
 * @param onChanged        Optional callback triggered whenever the envelope changes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SegmentedEnvelopeEditor(
    envelope: SegmentedEnvelope,
    modifier: Modifier = Modifier,
    minValue: Float = 0f,
    maxValue: Float = 1f,
    maxTime: Float = 2f,
    showToolbar: Boolean = true,
    showZoomControls: Boolean = true,
    onChanged: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val vertexRadius = with(density) { 8.dp.toPx() }
    val hitRadius    = with(density) { 22.dp.toPx() }
    val textMeasurer = rememberTextMeasurer()

    val leftMargin   = with(density) { 50.dp.toPx() }
    val rightMargin  = with(density) { 16.dp.toPx() }
    val topMargin    = with(density) { 14.dp.toPx() }
    val bottomMargin = with(density) { 20.dp.toPx() }

    val frames: SnapshotStateList<EnvFrame> = remember {
        mutableStateListOf<EnvFrame>().also { list ->
            val n = envelope.numFrames
            if (n > 0) {
                val data = FloatArray(n * 2)
                envelope.read(data)
                for (i in 0 until n) list.add(EnvFrame(data[i * 2], data[i * 2 + 1]))
            }
        }
    }

    /**
     * Note that the End value is the frame index of the frame just past the end of the loop.
     * The number of frames included in the loop is (End - Begin).
     * So to hold at N set Begin and End to (N+1)
     * To loop over points 1,2, set Begin=1 and End=3
     */
    val ENV_OFFSET = 1
    var editMode     by remember { mutableStateOf(EditMode.Points) }
    var sustainBegin by remember { mutableIntStateOf(envelope.sustainBegin) }
    var sustainEnd   by remember { mutableIntStateOf(envelope.sustainEnd) }
    var releaseBegin by remember { mutableIntStateOf(envelope.releaseBegin) }
    var releaseEnd   by remember { mutableIntStateOf(envelope.releaseEnd) }
    // Raw pixel x-range of the in-progress loop drag; null when no drag is active.
    var loopDragRect by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var dragReadout  by remember { mutableStateOf<DragReadout?>(null) }
    var showHint     by remember { mutableStateOf(true) }

    // Range zoom state
    val maxZoomT = maxOf(MAX_ZOOM_TIME, maxTime * 2f)
    val maxZoomV = maxOf(MAX_ZOOM_VALUE, maxValue * 2f)

    var zoomXProgress by remember {
        var initialT = 0f
        frames.forEach { initialT += it.duration }
        val targetT = maxOf(maxTime, initialT)
        mutableStateOf(scaleToProgress(targetT, MIN_ZOOM_TIME, maxZoomT))
    }

    var zoomYProgress by remember {
        val maxFrameVal = frames.maxOfOrNull { it.value } ?: maxValue
        val targetV = maxOf(maxValue, maxFrameVal)
        mutableStateOf(scaleToProgress(targetV, MIN_ZOOM_VALUE, maxZoomV))
    }

    val currentMaxTime = progressToScale(zoomXProgress, MIN_ZOOM_TIME, maxZoomT)
    val currentMaxValue = progressToScale(zoomYProgress, MIN_ZOOM_VALUE, maxZoomV)

    val timeScale = calculateNiceScale(0f, currentMaxTime, targetTicks = 4)
    val valScale = calculateNiceScale(minValue, currentMaxValue, targetTicks = 4)

    fun toPixelX(time: Float, width: Float): Float {
        val plotW = maxOf(1f, width - leftMargin - rightMargin)
        return leftMargin + (time / currentMaxTime).coerceIn(0f, 1f) * plotW
    }

    fun toPixelY(value: Float, height: Float): Float {
        val plotH = maxOf(1f, height - topMargin - bottomMargin)
        val range = (currentMaxValue - minValue).coerceAtLeast(0.0001f)
        val norm = ((value - minValue) / range).coerceIn(0f, 1f)
        return topMargin + (1f - norm) * plotH
    }

    fun toTime(px: Float, width: Float): Float {
        val plotW = maxOf(1f, width - leftMargin - rightMargin)
        val norm = ((px - leftMargin) / plotW).coerceIn(0f, 1f)
        return norm * currentMaxTime
    }

    fun toValue(py: Float, height: Float): Float {
        val plotH = maxOf(1f, height - topMargin - bottomMargin)
        val norm = 1f - ((py - topMargin) / plotH).coerceIn(0f, 1f)
        return minValue + norm * (currentMaxValue - minValue)
    }

    fun accumulatedTimes(): List<Float> {
        var t = 0f
        return frames.map { f -> t += f.duration; t }
    }

    /** Index of the next frame beyond the pixel [px]. */
    fun nextFrameIndex(px: Float, accTimes: List<Float>, width: Float): Int {
        if (accTimes.isEmpty()) return -1
        val t = toTime(px, width)
        accTimes.forEachIndexed { i, accT ->
            if (accT > t) {
                return i
            }
        }
        return accTimes.size
    }

    fun writeBack() {
        val n = frames.size
        if (n > envelope.maxFrames) envelope.allocate(n + 4)
        else envelope.setNumFrames(0)
        val data = FloatArray(n * 2)
        frames.forEachIndexed { i, f -> data[i * 2] = f.duration; data[i * 2 + 1] = f.value }
        envelope.write(data)
        envelope.sustainBegin = sustainBegin
        envelope.sustainEnd   = sustainEnd
        envelope.releaseBegin = releaseBegin
        envelope.releaseEnd   = releaseEnd
        onChanged?.invoke()
    }

    Column(modifier = modifier) {

        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(COLOR_BG)
                .pointerInput(editMode, currentMaxTime, currentMaxValue) {
                    awaitEachGesture {
                        var downChange: PointerInputChange? = null
                        var isShiftHeld = false
                        while (downChange == null) {
                            val evt = awaitPointerEvent()
                            val candidate = evt.changes.firstOrNull { !it.previousPressed && it.pressed }
                            if (candidate != null) {
                                isShiftHeld = evt.keyboardModifiers.isShiftPressed
                                downChange = candidate
                            }
                        }
                        val down = downChange!!
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val plotRight = w - rightMargin
                        val plotBottom = h - bottomMargin

                        when (editMode) {

                            // ── Points mode: insert / drag / shift-delete vertices ──────────
                            EditMode.Points -> {
                                val pos      = down.position
                                val accTimes = accumulatedTimes()

                                val hitRadius2 = hitRadius * hitRadius
                                var activeIndex = -1
                                var minDist2 = Float.MAX_VALUE
                                accTimes.forEachIndexed { i, t ->
                                    val vx = toPixelX(t, w)
                                    val vy = toPixelY(frames[i].value, h)
                                    val dx = pos.x - vx
                                    val dy = pos.y - vy
                                    val d2 = dx * dx + dy * dy
                                    if (d2 <= hitRadius2 && d2 < minDist2) { minDist2 = d2; activeIndex = i }
                                }

                                if (isShiftHeld && activeIndex >= 0) {
                                    showHint = false
                                    frames.removeAt(activeIndex)
                                    writeBack()
                                    down.consume()
                                    return@awaitEachGesture
                                }

                                if (activeIndex < 0) {
                                    val inPlot = pos.x in (leftMargin - hitRadius)..(plotRight + hitRadius) &&
                                                 pos.y in (topMargin - hitRadius)..(plotBottom + hitRadius)
                                    if (!inPlot) {
                                        down.consume()
                                        return@awaitEachGesture
                                    }

                                    showHint = false
                                    val clickTime  = toTime(pos.x, w)
                                    val clickValue = toValue(pos.y, h)
                                    val segIdx = accTimes.indexOfFirst { it >= clickTime }
                                        .let { if (it < 0) frames.size else it }
                                    if (segIdx >= frames.size) {
                                        val prevT = if (accTimes.isEmpty()) 0f else accTimes.last()
                                        frames.add(EnvFrame((clickTime - prevT).coerceAtLeast(MIN_DURATION), clickValue))
                                        activeIndex = frames.size - 1
                                    } else {
                                        val prevT     = if (segIdx == 0) 0f else accTimes[segIdx - 1]
                                        val segEndT   = accTimes[segIdx]
                                        val newDur    = (clickTime - prevT).coerceAtLeast(MIN_DURATION)
                                        val followDur = (segEndT - clickTime).coerceAtLeast(MIN_DURATION)
                                        frames[segIdx] = frames[segIdx].copy(duration = followDur)
                                        frames.add(segIdx, EnvFrame(newDur, clickValue))
                                        activeIndex = segIdx
                                    }
                                    writeBack()
                                }

                                down.consume()

                                val initialAcc = accumulatedTimes()
                                val gap = 6.dp.toPx()
                                val boxH = 22.dp.toPx()
                                val hysteresis = 20.dp.toPx()
                                var isBelow = false

                                if (activeIndex in initialAcc.indices) {
                                    val initT = initialAcc[activeIndex]
                                    val initV = frames[activeIndex].value
                                    val initPx = toPixelX(initT, w)
                                    val initPy = toPixelY(initV, h)
                                    val spaceAbove = initPy - vertexRadius - boxH - gap
                                    isBelow = spaceAbove < topMargin
                                    dragReadout = DragReadout(initT, initV, initPx, initPy, isBelow = isBelow)
                                }

                                do {
                                    val event  = awaitPointerEvent()
                                    val change = event.changes.find { it.id == down.id } ?: break
                                    if (change.pressed) {
                                        showHint = false
                                        val newPos   = change.position
                                        val newTime  = toTime(newPos.x, w)
                                        val newValue = toValue(newPos.y, h)
                                        val curAcc   = accumulatedTimes()
                                        val prevT    = if (activeIndex == 0) 0f else curAcc[activeIndex - 1]
                                        val nextT    = if (activeIndex >= curAcc.size - 1) currentMaxTime
                                                       else curAcc[activeIndex + 1] - MIN_DURATION
                                        val clampedTime = newTime.coerceIn(prevT + MIN_DURATION, nextT)
                                        frames[activeIndex] = EnvFrame(
                                            clampedTime - prevT,
                                            newValue
                                        )
                                        writeBack()
                                        val newPx = toPixelX(clampedTime, w)
                                        val newPy = toPixelY(newValue, h)
                                        val spaceAbove = newPy - vertexRadius - boxH - gap
                                        if (!isBelow) {
                                            if (spaceAbove < topMargin) {
                                                isBelow = true
                                            }
                                        } else {
                                            if (spaceAbove >= topMargin + hysteresis) {
                                                isBelow = false
                                            }
                                        }
                                        dragReadout = DragReadout(clampedTime, newValue, newPx, newPy, isBelow = isBelow)
                                        change.consume()
                                    }
                                } while (event.changes.any { it.id == down.id && it.pressed })

                                dragReadout = null
                            }

                            // ── Loop modes: drag to select, apply indices on release ────────
                            EditMode.SustainLoop, EditMode.ReleaseLoop -> {
                                val accTimes = accumulatedTimes()
                                if (accTimes.isEmpty()) return@awaitEachGesture

                                val anchorX = down.position.x
                                loopDragRect = Pair(anchorX, anchorX)
                                down.consume()

                                var currentX = anchorX
                                do {
                                    val event  = awaitPointerEvent()
                                    val change = event.changes.find { it.id == down.id } ?: break
                                    if (change.pressed) {
                                        currentX = change.position.x
                                        loopDragRect = Pair(anchorX, currentX)
                                        change.consume()
                                    }
                                } while (event.changes.any { it.id == down.id && it.pressed })

                                val x1 = minOf(anchorX, currentX)
                                val x2 = maxOf(anchorX, currentX)
                                val beginIdx = nextFrameIndex(x1, accTimes, w)
                                val endIdx   = nextFrameIndex(x2, accTimes, w)
                                if (beginIdx >= 0 && endIdx >= 0) {
                                    val left  = minOf(beginIdx, endIdx)
                                    val right = maxOf(beginIdx, endIdx)
                                    var begin = -1
                                    var end = -1
                                    if (right - left == 1) {
                                        begin = right; end = right
                                    } else if ((right - left) > 1) {
                                        begin = left; end = right
                                    }
                                    if (editMode == EditMode.SustainLoop) {
                                        sustainBegin = begin; sustainEnd = end
                                    } else {
                                        releaseBegin = begin; releaseEnd = end
                                    }
                                    writeBack()
                                }
                                loopDragRect = null
                            }
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val plotLeft = leftMargin
            val plotTop = topMargin
            val plotRight = w - rightMargin
            val plotBottom = h - bottomMargin
            val plotW = plotRight - plotLeft
            val plotH = plotBottom - plotTop
            val accTimes = accumulatedTimes()

            // ── Background of plotting area ───────────────────────────────────
            drawRect(COLOR_PLOT_BG, topLeft = Offset(plotLeft, plotTop), size = Size(plotW, plotH))

            // ── Grid lines & ticks for Value (Y-axis) using Nice Numbers ──────
            for (v in valScale.ticks) {
                val py = toPixelY(v, h)
                if (v > minValue + 0.0001f && v < currentMaxValue - 0.0001f) {
                    drawLine(COLOR_GRID, Offset(plotLeft, py), Offset(plotRight, py), strokeWidth = 1f)
                }
                drawLine(COLOR_AXIS, Offset(plotLeft - 4.dp.toPx(), py), Offset(plotLeft, py), strokeWidth = 1.5f)
                val label = formatNiceNumber(v, valScale.step)
                val textLayout = textMeasurer.measure(
                    label,
                    TextStyle(color = Color(0xCCFFFFFF), fontSize = 10.sp)
                )
                drawText(
                    textLayout,
                    topLeft = Offset(
                        plotLeft - 6.dp.toPx() - textLayout.size.width,
                        py - textLayout.size.height / 2f
                    )
                )
            }

            // ── Grid lines & ticks for Time (X-axis) using Nice Numbers ───────
            for (t in timeScale.ticks) {
                val px = toPixelX(t, w)
                if (t > 0.0001f && t < currentMaxTime - 0.0001f) {
                    drawLine(COLOR_GRID, Offset(px, plotTop), Offset(px, plotBottom), strokeWidth = 1f)
                }
                drawLine(COLOR_AXIS, Offset(px, plotBottom), Offset(px, plotBottom + 4.dp.toPx()), strokeWidth = 1.5f)
                val label = "${formatNiceNumber(t, timeScale.step)}s"
                val textLayout = textMeasurer.measure(
                    label,
                    TextStyle(color = Color(0xCCFFFFFF), fontSize = 10.sp)
                )
                drawText(
                    textLayout,
                    topLeft = Offset(
                        px - textLayout.size.width / 2f,
                        plotBottom + 3.dp.toPx()
                    )
                )
            }

            // ── Axis border lines ─────────────────────────────────────────────
            drawLine(COLOR_AXIS, Offset(plotLeft, plotTop), Offset(plotLeft, plotBottom), strokeWidth = 1.5f)
            drawLine(COLOR_AXIS, Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), strokeWidth = 1.5f)

            // ── Axis titles ───────────────────────────────────────────────────
            val valTitle = textMeasurer.measure("Val", TextStyle(color = Color(0x88FFFFFF), fontSize = 9.sp))
            drawText(valTitle, topLeft = Offset(plotLeft - valTitle.size.width - 5.dp.toPx(), plotTop - 11.dp.toPx()))

            // ── Loop region visualisation (drawn before the line) ─────────────
            fun drawLoopRegion(begin: Int, end: Int, bgColor: Color, barColor: Color) {
                if (begin < 0 || end < 0 || begin > accTimes.size || end > accTimes.size) return
                if (begin == end) {
                    // Single-frame hold → full-height vertical bar
                    val x = toPixelX(accTimes[begin - ENV_OFFSET], w)
                    drawLine(barColor, Offset(x, plotTop), Offset(x, plotBottom), strokeWidth = 3f)
                } else {
                    // Multi-frame range → tinted background rectangle
                    val x1 = if (begin < ENV_OFFSET) plotLeft else toPixelX(accTimes[begin - ENV_OFFSET], w)
                    val x2 = toPixelX(accTimes[end - ENV_OFFSET], w)
                    drawRect(bgColor, topLeft = Offset(x1, plotTop), size = Size(x2 - x1, plotH))
                    // Border line at right edge
                    drawLine(barColor, Offset(x2, plotTop), Offset(x2, plotBottom), strokeWidth = 2f)
                }
            }

            // Release drawn first so sustain renders on top if they overlap
            drawLoopRegion(releaseBegin, releaseEnd, COLOR_RELEASE_BG, COLOR_RELEASE_BAR)
            drawLoopRegion(sustainBegin, sustainEnd, COLOR_SUSTAIN_BG, COLOR_SUSTAIN_BAR)

            if (accTimes.isNotEmpty()) {
                // ── Envelope line ─────────────────────────────────────────────
                val startValue = 0f.coerceIn(minValue, currentMaxValue)
                val path = Path().apply {
                    moveTo(toPixelX(0f, w), toPixelY(startValue, h))
                    accTimes.forEachIndexed { i, t ->
                        lineTo(toPixelX(t, w), toPixelY(frames[i].value, h))
                    }
                }
                drawPath(
                    path,
                    color = COLOR_LINE,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // ── Vertex circles ────────────────────────────────────────────
                accTimes.forEachIndexed { i, t ->
                    val cx = toPixelX(t, w)
                    val cy = toPixelY(frames[i].value, h)
                    drawCircle(COLOR_VERTEX_FILL, radius = vertexRadius, center = Offset(cx, cy))
                    drawCircle(Color.White, radius = vertexRadius, center = Offset(cx, cy),
                        style = Stroke(width = 2f))
                }
            }

            // ── In-progress loop drag selection (drawn on top) ────────────────
            loopDragRect?.let { (ax, bx) ->
                val left  = minOf(ax, bx).coerceIn(plotLeft, plotRight)
                val right = maxOf(ax, bx).coerceIn(plotLeft, plotRight)
                drawRect(Color(0x44FFFFFF), topLeft = Offset(left, plotTop), size = Size(right - left, plotH))
                drawRect(Color.White, topLeft = Offset(left, plotTop), size = Size(right - left, plotH),
                    style = Stroke(width = 1.5f))
            }

            // ── Initial hint banner (top right) ──────────────────────────────
            if (showHint) {
                val hintText = "SHIFT+CLICK to delete points"
                val textLayout = textMeasurer.measure(
                    hintText,
                    TextStyle(color = Color(0xFFFFD54F), fontSize = 11.sp)
                )
                val padH = 8.dp.toPx()
                val padV = 5.dp.toPx()
                val boxW = textLayout.size.width + padH * 2
                val boxH = textLayout.size.height + padV * 2
                val boxLeft = plotRight - boxW - 4.dp.toPx()
                val boxTop = plotTop + 4.dp.toPx()

                drawRoundRect(
                    color = Color(0xE0242438),
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0x88FFB74D),
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(width = 1f)
                )
                drawText(
                    textLayout,
                    topLeft = Offset(boxLeft + padH, boxTop + padV)
                )
            }

            // ── Dragging readout popup ────────────────────────────────────────
            dragReadout?.let { info ->
                val timeLabel = formatDragTime(info.time, timeScale.step)
                val valLabel = formatDragValue(info.value, valScale.step)
                val text = "t: $timeLabel, v: $valLabel"
                val textLayout = textMeasurer.measure(
                    text,
                    TextStyle(color = Color.White, fontSize = 11.sp)
                )
                val padH = 8.dp.toPx()
                val padV = 4.dp.toPx()
                val boxW = textLayout.size.width + padH * 2
                val boxH = textLayout.size.height + padV * 2

                val gap = 6.dp.toPx()
                var boxTop = if (info.isBelow) {
                    info.py + vertexRadius + gap
                } else {
                    info.py - vertexRadius - boxH - gap
                }
                if (boxTop < plotTop) {
                    boxTop = info.py + vertexRadius + gap
                }
                val boxLeft = (info.px - boxW / 2f).coerceIn(plotLeft, plotRight - boxW)

                drawRoundRect(
                    color = Color(0xEE1A1A2E),
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xAA4FC3F7),
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = Stroke(width = 1.5f)
                )
                drawText(
                    textLayout,
                    topLeft = Offset(boxLeft + padH, boxTop + padV)
                )
            }
        }

        // ── Controls: Toolbar and Zoom Controls (single row with flow wrapping) ─
        if (showToolbar || showZoomControls) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(COLOR_BG)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                if (showToolbar) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(EditMode.Points, EditMode.SustainLoop, EditMode.ReleaseLoop).forEach { mode ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                RadioButton(
                                    selected = editMode == mode,
                                    onClick  = { editMode = mode },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF81D4FA),
                                        unselectedColor = Color(0xFF90A4AE)
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text     = mode.label,
                                    style    = MaterialTheme.typography.bodySmall,
                                    color    = Color(0xFFEEEEEE)
                                )
                            }
                        }

                        fun loopStr(begin: Int, end: Int) =
                            if (begin < 0 || end < 0) "none" else "$begin–$end"

                        Text(
                            text  = "S:${loopStr(sustainBegin, sustainEnd)}  R:${loopStr(releaseBegin, releaseEnd)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB0BEC5),
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }

                if (showZoomControls) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Time:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE0E0E0)
                        )
                        Spacer(Modifier.width(4.dp))
                        Slider(
                            value = zoomXProgress,
                            onValueChange = { zoomXProgress = it },
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF81D4FA),
                                activeTrackColor = Color(0xFF4FC3F7),
                                inactiveTrackColor = Color(0x44FFFFFF)
                            ),
                            modifier = Modifier.width(64.dp).height(24.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${formatNiceNumber(currentMaxTime, timeScale.step)}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF81D4FA),
                            modifier = Modifier.width(46.dp)
                        )

                        Spacer(Modifier.width(8.dp))

                        Text(
                            text = "Value:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE0E0E0)
                        )
                        Spacer(Modifier.width(4.dp))
                        Slider(
                            value = zoomYProgress,
                            onValueChange = { zoomYProgress = it },
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFFB74D),
                                activeTrackColor = Color(0xFFFF9800),
                                inactiveTrackColor = Color(0x44FFFFFF)
                            ),
                            modifier = Modifier.width(64.dp).height(24.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = formatNiceNumber(currentMaxValue, valScale.step),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFB74D),
                            modifier = Modifier.width(44.dp)
                        )
                    }
                }
            }
        }
    }
}
