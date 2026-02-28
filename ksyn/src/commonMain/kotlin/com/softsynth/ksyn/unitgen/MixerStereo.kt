/*
 * Copyright 2014 Phil Burk, Mobileer Inc
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

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.data.AudioSample
import kotlin.math.sin
import kotlin.math.PI

/**
 * Mixer with monophonic inputs and two channels of output. Each signal can be panned left or right
 * using an equal power curve. The "left" signal will be on output part zero. The "right" signal
 * will be on output part one.
 * 
 * @author Phil Burk (C) 2014 Mobileer Inc
 * @see MixerMono
 * @see MixerStereoRamped
 */
open class MixerStereo(numInputs: Int) : MixerMono(numInputs) {
    /**
     * Set to -1.0 for all left channel, 0.0 for center, or +1.0 for all right. Or anywhere in
     * between.
     */
    val pan: UnitInputPort
    protected val panTrackers: Array<PanTracker>

    class PanTracker {
        var previousPan: AudioSample = Float.MAX_VALUE // so we update immediately
        var leftGain: AudioSample = 0.0f
        var rightGain: AudioSample = 0.0f

        fun update(pan: AudioSample) {
            if (pan != previousPan) {
                // native sin() range is 0 to 2PI for full cycle.
                // We want a quarter cycle. So map -1.0 to +1.0 into 0.0 to 0.5 (as a fraction of PI)
                val phase = pan * 0.25f + 0.25f
                leftGain = sin((0.5f - phase) * PI).toFloat() // Equivalent to JSyn fastSin 
                rightGain = sin(phase * PI).toFloat()
                previousPan = pan
            }
        }
    }

    init {
        pan = UnitInputPort(numInputs, "Pan", 0.0)
        pan.setup(-1.0, 0.0, 1.0)
        addPort(pan)
        panTrackers = Array(numInputs) { PanTracker() }
    }

    override val numOutputs: Int
        get() = 2

    override fun generate() {
        val amplitudes = amplitude.getValues(0)
        val outputs0 = output.getValues(0)
        val outputs1 = output.getValues(1)

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            var sum0 = 0.0f
            var sum1 = 0.0f
            for (n in 0 until input.numParts) {
                val inputs = input.getValues(n)
                val gains = gain.getValues(n)
                val pans = pan.getValues(n)
                val tracker = panTrackers[n]
                tracker.update(pans[i])
                
                val scaledInput = inputs[i] * gains[i]
                sum0 += scaledInput * tracker.leftGain
                sum1 += scaledInput * tracker.rightGain
            }
            val amp = amplitudes[i]
            outputs0[i] = sum0 * amp
            outputs1[i] = sum1 * amp
        }
    }
}
