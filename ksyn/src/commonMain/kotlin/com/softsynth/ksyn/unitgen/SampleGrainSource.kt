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
 * A GrainSource that plays a FloatSample. The position ranges from -1.0 to 1.0,
 * mapping to the start and end of the sample respectively, with 0.0 being the centre.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
class SampleGrainSource : GrainCommon(), GrainSource {
    private var sample: FloatSample? = null
    /** Position in the sample, ranges from -1.0 to 1.0. */
    private var position: Double = 0.0
    private var positionRange: Double = 0.0
    private var phase: Double = 0.0  // ranges from 0.0 to 1.0
    private var phaseIncrement: Double = 0.0
    private var numFramesGuarded: Int = 0
    private val random = PseudoRandom()

    companion object {
        private const val MAX_PHASE = 0.9999999999
    }

    override fun next(): AudioSample {
        phase += phaseIncrement
        if (phase > MAX_PHASE) {
            phase = MAX_PHASE
        }
        val fractionalIndex = (phase * numFramesGuarded).toFloat()
        return sample?.interpolate(fractionalIndex) ?: 0.0f
    }

    override fun setRate(rate: Double) {
        val s = sample ?: return
        phaseIncrement = rate * s.frameRate / (frameRate * numFramesGuarded)
    }

    fun setSample(sample: FloatSample) {
        this.sample = sample
        numFramesGuarded = sample.numFrames - 1
    }

    fun setPosition(position: Double) {
        this.position = position
    }

    override fun reset() {
        val randomPosition = position + (positionRange * (random.nextRandomDouble() - 0.5))
        phase = (randomPosition * 0.5) + 0.5
        phase = phase.coerceIn(0.0, MAX_PHASE)
    }

    fun setPositionRange(positionRange: Double) {
        this.positionRange = positionRange
    }
}
