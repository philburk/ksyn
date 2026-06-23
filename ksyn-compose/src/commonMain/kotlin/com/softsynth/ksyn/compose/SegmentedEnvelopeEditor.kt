package com.softsynth.ksyn.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.softsynth.ksyn.data.SegmentedEnvelope

private const val MIN_DURATION = 0.001f

private data class EnvFrame(val duration: Float, val value: Float)

/**
 * An interactive breakpoint editor for a SegmentedEnvelope.
 *
 * Displays the envelope as connected line segments with draggable vertex circles.
 * Tapping an empty area inserts a new breakpoint; dragging a vertex updates the
 * associated frame's duration (X axis = accumulated time) and value (Y axis).
 *
 * @param envelope  The SegmentedEnvelope to edit. Modified in place on every gesture.
 * @param minValue  Minimum displayable value (maps to the bottom edge).
 * @param maxValue  Maximum displayable value (maps to the top edge).
 * @param maxTime   Total visible time in seconds (maps to the right edge).
 */
@Composable
fun SegmentedEnvelopeEditor(
    envelope: SegmentedEnvelope,
    modifier: Modifier = Modifier,
    minValue: Float = 0f,
    maxValue: Float = 1f,
    maxTime: Float = 2f,
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

    // Pixel ↔ data coordinate conversions (close over stable params, Unit key is fine)
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

    fun writeBack() {
        val n = frames.size
        if (n > envelope.maxFrames) envelope.allocate(n + 4)
        else envelope.setNumFrames(0)
        val data = FloatArray(n * 2)
        frames.forEachIndexed { i, f -> data[i * 2] = f.duration; data[i * 2 + 1] = f.value }
        envelope.write(data)
    }

    Canvas(
        modifier = modifier
            .background(Color(0xFF1A1A2E))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pos  = down.position
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val accTimes = accumulatedTimes()

                    // ── Hit-test: find closest vertex within hitRadius ──────────────────
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

                    // ── Insert a new frame if no vertex was hit ────────────────────────
                    if (activeIndex < 0) {
                        val clickTime  = toTime(pos.x, w)
                        val clickValue = toValue(pos.y, h)

                        // Index of the first frame whose accumulated time >= clickTime
                        val segIdx = accTimes.indexOfFirst { it >= clickTime }
                            .let { if (it < 0) frames.size else it }

                        if (segIdx >= frames.size) {
                            // Click is after all existing frames – append
                            val prevT = if (accTimes.isEmpty()) 0f else accTimes.last()
                            frames.add(EnvFrame((clickTime - prevT).coerceAtLeast(MIN_DURATION), clickValue))
                            activeIndex = frames.size - 1
                        } else {
                            // Click is inside segment [segIdx-1 .. segIdx] – split it
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

                    // ── Drag the active vertex ─────────────────────────────────────────
                    do {
                        val event  = awaitPointerEvent()
                        val change = event.changes.find { it.id == down.id } ?: break
                        if (change.pressed) {
                            val newPos   = change.position
                            val newTime  = toTime(newPos.x, w)
                            val newValue = toValue(newPos.y, h)

                            val curAcc = accumulatedTimes()
                            val prevT  = if (activeIndex == 0) 0f else curAcc[activeIndex - 1]
                            val nextT  = if (activeIndex >= curAcc.size - 1) maxTime
                                        else curAcc[activeIndex + 1] - MIN_DURATION
                            val clampedT = newTime.coerceIn(prevT + MIN_DURATION, nextT)

                            frames[activeIndex] = EnvFrame(clampedT - prevT, newValue)
                            writeBack()
                            change.consume()
                        }
                    } while (event.changes.any { it.id == down.id && it.pressed })
                }
            }
    ) {
        val w = size.width
        val h = size.height

        // ── Horizontal grid lines at 25 % intervals ───────────────────────────
        val gridColor  = Color(0x33FFFFFF)
        val valueRange = maxValue - minValue
        for (step in 1..3) {
            val v  = minValue + valueRange * step / 4f
            val py = toPixelY(v, h)
            drawLine(gridColor, Offset(0f, py), Offset(w, py), strokeWidth = 1f)
        }

        val accTimes = accumulatedTimes()
        if (frames.isNotEmpty()) {
            // ── Envelope line (starts from implicit t=0 at minValue or 0) ─────
            val startValue = 0f.coerceIn(minValue, maxValue)
            val path = Path().apply {
                moveTo(toPixelX(0f, w), toPixelY(startValue, h))
                accTimes.forEachIndexed { i, t ->
                    lineTo(toPixelX(t, w), toPixelY(frames[i].value, h))
                }
            }
            drawPath(
                path,
                color = Color(0xFF4FC3F7),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // ── Vertex circles ─────────────────────────────────────────────────
            accTimes.forEachIndexed { i, t ->
                val cx = toPixelX(t, w)
                val cy = toPixelY(frames[i].value, h)
                drawCircle(Color(0xFFFF7043), radius = vertexRadius, center = Offset(cx, cy))
                drawCircle(Color.White, radius = vertexRadius, center = Offset(cx, cy),
                    style = Stroke(width = 2f))
            }
        }
    }
}
