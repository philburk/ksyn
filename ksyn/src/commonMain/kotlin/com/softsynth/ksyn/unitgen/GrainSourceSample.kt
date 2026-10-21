/*
 * Copyright 2009 Phil Burk, Mobileer Inc
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
import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.util.PseudoRandom

/**
 * A [GrainSource] that plays a [FloatSample]. The position ranges from -1.0 to +1.0,
 * mapping to the start and end of the sample respectively, with 0.0 being the center.
 *
 * Internal fractional frame indexing uses 64-bit [Double] precision so that slow playback rates
 * or large samples do not suffer from 32-bit float precision loss.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
class GrainSourceSample(sample: FloatSample? = null) : GrainCommon(), GrainSource {
    private var sample: FloatSample? = null

    /** Fractional frame index into [sample], in the range `0.0 .. (numFrames - 1)`. */
    var phase: Double = 0.0
        private set

    var phaseIncrement: Double = 1.0
        private set

    private var position: Double = 0.0
    private var positionRange: Double = 0.0
    private var maxFrameIndex: Double = 0.0
    private val random = PseudoRandom()

    init {
        if (sample != null) {
            setSample(sample)
        }
    }

    override fun next(): AudioSample {
        val s = sample ?: return 0.0f
        if (s.numFrames <= 0) return 0.0f
        val currentPhase = phase.coerceIn(0.0, maxFrameIndex)
        val out = s.interpolate(currentPhase)
        phase = (currentPhase + phaseIncrement).coerceIn(0.0, maxFrameIndex)
        return out
    }

    override fun setRate(rate: Double) {
        val s = sample
        val currentFrameRate = if (frameRate > 0.0f) frameRate.toDouble() else DEFAULT_FRAME_RATE
        val sampleRate = if (s != null && s.frameRate > 0.0) s.frameRate else currentFrameRate
        phaseIncrement = rate * sampleRate / currentFrameRate
    }

    fun setSample(sample: FloatSample?) {
        this.sample = sample
        maxFrameIndex = if (sample != null && sample.numFrames > 1) {
            (sample.numFrames - 1).toDouble()
        } else {
            0.0
        }
    }

    fun getSample(): FloatSample? = sample

    /**
     * Set the starting position of the grain in the range -1.0 (start of sample)
     * to +1.0 (end of sample).
     */
    fun setPosition(position: Double) {
        this.position = position
        val normalized = (position.coerceIn(-1.0, 1.0) * 0.5) + 0.5
        phase = normalized * maxFrameIndex
    }

    fun setPositionRange(positionRange: Double) {
        this.positionRange = positionRange
    }

    override fun reset() {
        val randomOffset = if (positionRange != 0.0) {
            positionRange * random.nextRandomDouble()
        } else {
            0.0
        }
        val pos = (position + randomOffset).coerceIn(-1.0, 1.0)
        val normalized = (pos * 0.5) + 0.5
        phase = normalized * maxFrameIndex
    }

    companion object {
        private const val DEFAULT_FRAME_RATE = 44100.0
    }
}
