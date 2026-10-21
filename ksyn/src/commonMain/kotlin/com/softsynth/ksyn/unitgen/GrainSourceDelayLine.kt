/*
 * Copyright 2026 Phil Burk, Mobileer Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.data.AudioSample

/**
 * A circular delay buffer with a guard point for fast linear interpolation, used by
 * [DelayGrains] and [GrainSourceDelayLine].
 *
 * @param numFrames number of frames in the delay line (must be >= 2)
 */
class GrainDelayBuffer(numFrames: Int) {
    val numFrames: Int = maxOf(2, numFrames)
    // Allocate one extra guard frame so buffer[this.numFrames] == buffer[0].
    val buffer: FloatArray = FloatArray(this.numFrames + 1)
    var cursor: Int = 0
        private set

    fun write(input: AudioSample) {
        if (cursor == numFrames) {
            buffer[numFrames] = input
            cursor = 0
        }
        buffer[cursor] = input
        if (cursor == 0) {
            buffer[numFrames] = input
        }
        cursor += 1
    }

    fun clear() {
        buffer.fill(0.0f)
        cursor = 0
    }

    /**
     * Read an interpolated sample at [delayFrames] behind the current [cursor].
     * [delayFrames] is clamped to `[1.0, numFrames - 1.0]`.
     */
    fun readInterpolated(delayFrames: Double): AudioSample {
        val maxDelay = (numFrames - 1).toDouble()
        val clampedDelay = delayFrames.coerceIn(1.0, maxDelay)
        var readIndex = cursor.toDouble() - clampedDelay
        if (readIndex < 0.0) {
            readIndex += numFrames.toDouble()
        }
        val iReadIndex = readIndex.toInt()
        val frac = (readIndex - iReadIndex).toFloat()
        val val0 = buffer[iReadIndex]
        val val1 = buffer[iReadIndex + 1]
        return val0 + frac * (val1 - val0)
    }
}

/**
 * A [GrainSource] that reads from a [GrainDelayBuffer].
 *
 * The starting delay of each grain is set in seconds via [setDelay].
 */
class GrainSourceDelayLine(
    private var delayBuffer: GrainDelayBuffer? = null
) : GrainCommon(), GrainSource {

    /**
     * Current delay distance in frames behind the write cursor.
     * Ranges from `1.0` (newest sample) to `numFrames - 1.0` (oldest sample).
     */
    var delayFrames: Double = 1.0
        private set

    var phaseIncrement: Double = 1.0
        private set

    private var delaySeconds: Double = 0.0

    fun setDelayBuffer(delayBuffer: GrainDelayBuffer?) {
        this.delayBuffer = delayBuffer
    }

    fun getDelayBuffer(): GrainDelayBuffer? = delayBuffer

    override fun next(): AudioSample {
        val buf = delayBuffer ?: return 0.0f
        val maxDelay = (buf.numFrames - 1).toDouble()
        val currentDelay = delayFrames.coerceIn(1.0, maxDelay)
        val out = buf.readInterpolated(currentDelay)
        // As the write cursor advances by +1.0 frame and the grain advances by +phaseIncrement
        // frames in the recorded material, the distance behind the write cursor changes by
        // (1.0 - phaseIncrement).
        delayFrames = (currentDelay + (1.0 - phaseIncrement)).coerceIn(1.0, maxDelay)
        return out
    }

    override fun setRate(rate: Double) {
        phaseIncrement = rate
    }

    /**
     * Set the starting delay of the grain in seconds (`>= 0.0`), clamped to the delay buffer size.
     */
    fun setDelay(delaySeconds: Double) {
        this.delaySeconds = delaySeconds
        val buf = delayBuffer ?: return
        val currentFrameRate = if (frameRate > 0.0f) frameRate.toDouble() else DEFAULT_FRAME_RATE
        val maxDelay = (buf.numFrames - 1).toDouble()
        // A delay of 0.0s corresponds to 1.0 frame behind the cursor (the most recently written frame).
        delayFrames = (delaySeconds * currentFrameRate + 1.0).coerceIn(1.0, maxDelay)
    }

    override fun reset() {
        setDelay(delaySeconds)
    }

    companion object {
        private const val DEFAULT_FRAME_RATE = 44100.0
    }
}
