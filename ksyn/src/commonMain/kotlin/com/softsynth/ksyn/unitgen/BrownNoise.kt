/*
 * Copyright 1997 Phil Burk, Mobileer Inc
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

import com.softsynth.ksyn.AudioSample
import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.util.PseudoRandom

/**
 * BrownNoise unit. This unit uses a pseudo-random number generator to produce a noise related to
 * Brownian Motion. A DC blocker is used to prevent runaway drift.
 *
 * <pre>
 * <code>
 * output = (previous * (1.0 - damping)) + (random * amplitude)
 * </code>
 * </pre>
 *
 * The output drifts quite a bit and will generally exceed the range of +/1 amplitude.
 *
 * @author (C) 1997-2011 Phil Burk, Mobileer Inc
 * @see WhiteNoise
 * @see RedNoise
 * @see PinkNoise
 */
class BrownNoise : UnitGenerator(), UnitSource {
    private val randomNum: PseudoRandom

    /** * Increasing the damping will effectively increase the cutoff
     * frequency of a high pass filter that is used to block DC bias.
     * Warning: setting this too close to zero can result in very large output values.
     */
    var damping: UnitInputPort
    var amplitude: UnitInputPort
    var output: UnitOutputPort

    private var previous: AudioSample = 0.0f

    init {
        randomNum = PseudoRandom()

        addPort(UnitInputPort("Damping").also {
            damping = it
            // Setup defaults: min, val, max
            damping.setup(0.0001, 0.01, 0.1)
        })

        addPort(UnitInputPort("Amplitude", UnitOscillator.DEFAULT_AMPLITUDE).also {
            amplitude = it
        })

        addPort(UnitOutputPort("Output").also { output = it })
    }

    override fun generate() {
        val amplitudes = amplitude.getValues()
        val outputs = output.getValues()

        // Grab the first value for damping ( Control Rate optimization similar to original Java)
        val damper: AudioSample = 1.0f - damping.getValues()[0]

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            val r = randomNum.nextRandomSample() * amplitudes[i]

            // IIR Filter logic
            previous = (damper * previous) + r

            // Store result
            outputs[i] = previous.toFloat()
        }
    }

    override fun getOutputPort(): UnitOutputPort {
        return output
    }
}
