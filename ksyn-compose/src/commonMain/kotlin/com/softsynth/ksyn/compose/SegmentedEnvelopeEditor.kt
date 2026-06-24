package com.softsynth.ksyn.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import com.softsynth.ksyn.data.SegmentedEnvelope
import kotlin.math.abs

private const val MIN_DURATION = 0.001f

private data class EnvFrame(val duration: Float, val value: Float)

private enum class EditMode(val label: String) {
    Points("Points"),
    SustainLoop("Sustain"),
    ReleaseLoop("Release")
}

private val COLOR_BG          = Color(0xFF1A1A2E)
private val COLOR_GRID        = Color(0x33FFFFFF)
private val COLOR_LINE        = Color(0xFF4FC3F7)
private val COLOR_VERTEX_FILL = Color(0xFFFF7043)
private val COLOR_SUSTAIN_BG  = Color(0x33FFCA28)
private val COLOR_SUSTAIN_BAR = Color(0xAAFFCA28)
private val COLOR_RELEASE_BG  = Color(0x33CE93D8)
private val COLOR_RELEASE_BAR = Color(0xAACE93D8)

/**
 * An interactive breakpoint editor for a SegmentedEnvelope.
 *
 * Three editing modes are available via an optional bottom toolbar:
 *  - Points: drag vertices to edit duration/value; Shift+click deletes; click empty space inserts.
 *  - Sustain: drag sideways to set sustainBegin/sustainEnd frame indices.
 *  - Release: drag sideways to set releaseBegin/releaseEnd frame indices.
 *
 * Loop regions are visualised as a tinted rectangle (begin≠end) or a full-height bar (begin==end).
 *
 * @param envelope    The SegmentedEnvelope to edit. Modified in place on every gesture.
 * @param minValue    Minimum displayable value (maps to the bottom edge).
 * @param maxValue    Maximum displayable value (maps to the top edge).
 * @param maxTime     Total visible time in seconds (maps to the right edge).
 * @param showToolbar Whether to show the mode-selection toolbar at the bottom.
 */
@Composable
fun SegmentedEnvelopeEditor(
    envelope: SegmentedEnvelope,
    modifier: Modifier = Modifier,
    minValue: Float = 0f,
    maxValue: Float = 1f,
    maxTime: Float = 2f,
    showToolbar: Boolean = true,
) {
    val density = LocalDensity.current
    val vertexRadius = with(density) { 8.dp.toPx() }
    val hitRadius    = with(density) { 22.dp.toPx() }

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

    fun toPixelX(time: Float,  width: Float)  = time / maxTime * width
    fun toPixelY(value: Float, height: Float) =
        height - (value - minValue) / (maxValue - minValue) * height
    fun toTime (px: Float, width: Float)  = (px / width  * maxTime).coerceIn(0f, maxTime)
    fun toValue(py: Float, height: Float) =
        (minValue + (height - py) / height * (maxValue - minValue)).coerceIn(minValue, maxValue)

    fun accumulatedTimes(): List<Float> {
        var t = 0f
        return frames.map { f -> t += f.duration; t }
    }

    /** Index of the frame whose accumulated time is nearest to pixel [px]. */
    fun nearestFrameIndex(px: Float, accTimes: List<Float>, width: Float): Int {
        if (accTimes.isEmpty()) return -1
        val t = toTime(px, width)
        var bestIdx = 0
        var bestDist = Float.MAX_VALUE
        accTimes.forEachIndexed { i, accT ->
            val d = abs(accT - t)
            if (d < bestDist) { bestDist = d; bestIdx = i }
        }
        return bestIdx
    }
    /** Index of the next frame beyond the pixel [px]. */
    fun nextFrameIndex(px: Float, accTimes: List<Float>, width: Float): Int {
        if (accTimes.isEmpty()) return -1
        val t = toTime(px, width)
        accTimes.forEachIndexed { i, accT ->
            if (accT > t) {
                return i;
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
    }

    Column(modifier = modifier) {

        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(COLOR_BG)
                .pointerInput(editMode) {
                    awaitEachGesture {
                        // Wait for the first down event, capturing keyboard modifiers.
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
                                    frames.removeAt(activeIndex)
                                    writeBack()
                                    down.consume()
                                    return@awaitEachGesture
                                }

                                if (activeIndex < 0) {
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

                                do {
                                    val event  = awaitPointerEvent()
                                    val change = event.changes.find { it.id == down.id } ?: break
                                    if (change.pressed) {
                                        val newPos   = change.position
                                        val newTime  = toTime(newPos.x, w)
                                        val newValue = toValue(newPos.y, h)
                                        val curAcc   = accumulatedTimes()
                                        val prevT    = if (activeIndex == 0) 0f else curAcc[activeIndex - 1]
                                        val nextT    = if (activeIndex >= curAcc.size - 1) maxTime
                                                       else curAcc[activeIndex + 1] - MIN_DURATION
                                        frames[activeIndex] = EnvFrame(
                                            newTime.coerceIn(prevT + MIN_DURATION, nextT) - prevT,
                                            newValue
                                        )
                                        writeBack()
                                        change.consume()
                                    }
                                } while (event.changes.any { it.id == down.id && it.pressed })
                            }

                            // ── Loop modes: drag sideways to set begin/end frame indices ───
                            EditMode.SustainLoop, EditMode.ReleaseLoop -> {
                                val accTimes = accumulatedTimes()
                                if (accTimes.isEmpty()) return@awaitEachGesture
                                val anchorIdx = nextFrameIndex(down.position.x, accTimes, w)
                                if (anchorIdx < 0) return@awaitEachGesture

                                fun applyLoop(a: Int, b: Int) {
                                    val left = minOf(a, b)
                                    val right = maxOf(a, b)
                                    var begin = -1 // no loop by default
                                    var end = -1
                                    if ((right - left) == 1) { // single hold point
                                        end = right
                                        begin = end
                                    } else if ((right - left) > 1) { // loop with multiple points
                                        end = right
                                        begin = end - (right - left)
                                    }
                                    if (editMode == EditMode.SustainLoop) {
                                        sustainBegin = begin; sustainEnd = end
                                    } else {
                                        releaseBegin = begin; releaseEnd = end
                                    }
                                    writeBack()
                                }

                                applyLoop(anchorIdx, anchorIdx)
                                down.consume()

                                var dragIdx = anchorIdx
                                do {
                                    val event  = awaitPointerEvent()
                                    val change = event.changes.find { it.id == down.id } ?: break
                                    if (change.pressed) {
                                        val newIdx = nextFrameIndex(change.position.x, accumulatedTimes(), w)
                                        if (newIdx >= 0 && newIdx != dragIdx) {
                                            dragIdx = newIdx
                                            applyLoop(anchorIdx, dragIdx)
                                        }
                                        change.consume()
                                    }
                                } while (event.changes.any { it.id == down.id && it.pressed })
                            }
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val accTimes = accumulatedTimes()

            // ── Grid lines ────────────────────────────────────────────────────
            val valueRange = maxValue - minValue
            for (step in 1..3) {
                val v  = minValue + valueRange * step / 4f
                val py = toPixelY(v, h)
                drawLine(COLOR_GRID, Offset(0f, py), Offset(w, py), strokeWidth = 1f)
            }

            // ── Loop region visualisation (drawn before the line) ─────────────
            fun drawLoopRegion(begin: Int, end: Int, bgColor: Color, barColor: Color) {
                if (begin < 0 || end < 0 || begin >= accTimes.size || end >= accTimes.size) return
                if (begin == end) {
                    // Single-frame hold → full-height vertical bar
                    val x = toPixelX(accTimes[begin - ENV_OFFSET], w)
                    drawLine(barColor, Offset(x, 0f), Offset(x, h), strokeWidth = 3f)
                } else {
                    // Multi-frame range → tinted background rectangle
                    val x1 = if (begin < ENV_OFFSET) 0f else toPixelX(accTimes[begin - ENV_OFFSET], w)
                    val x2 = toPixelX(accTimes[end - ENV_OFFSET], w)
                    drawRect(bgColor, topLeft = Offset(x1, 0f), size = Size(x2 - x1, h))
                    // Border line at right edge
                    drawLine(barColor, Offset(x2, 0f), Offset(x2, h), strokeWidth = 2f)
                }
            }

            // Release drawn first so sustain renders on top if they overlap
            drawLoopRegion(releaseBegin, releaseEnd, COLOR_RELEASE_BG, COLOR_RELEASE_BAR)
            drawLoopRegion(sustainBegin, sustainEnd, COLOR_SUSTAIN_BG, COLOR_SUSTAIN_BAR)

            if (accTimes.isNotEmpty()) {
                // ── Envelope line ─────────────────────────────────────────────
                val startValue = 0f.coerceIn(minValue, maxValue)
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
        }

        // ── Mode toolbar ───────────────────────────────────────────────────────
        if (showToolbar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(EditMode.Points, EditMode.SustainLoop, EditMode.ReleaseLoop).forEach { mode ->
                    RadioButton(
                        selected = editMode == mode,
                        onClick  = { editMode = mode }
                    )
                    Text(
                        text     = mode.label,
                        modifier = Modifier.padding(end = 16.dp),
                        style    = MaterialTheme.typography.bodySmall
                    )
                }

                // ── Loop index status ─────────────────────────────────────────
                fun loopStr(begin: Int, end: Int) =
                    if (begin < 0 || end < 0) "none" else "$begin–$end"

                Text(
                    text  = "S:${loopStr(sustainBegin, sustainEnd)}  R:${loopStr(releaseBegin, releaseEnd)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFAAAAAA),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
