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

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.util.PseudoRandom

/**
 * WhiteNoise unit. This unit uses a pseudo-random number generator to produce white noise. The
 * energy in a white noise signal is distributed evenly across the spectrum. A new random number is
 * generated every frame.
 *
 * @author (C) 1997-2011 Phil Burk, Mobileer Inc
 * @see RedNoise
 */
class WhiteNoise : UnitGenerator(), UnitSource {
    private val randomNum: PseudoRandom
    var amplitude: UnitInputPort
    var output: UnitOutputPort

    init {
        randomNum = PseudoRandom()
        addPort(UnitInputPort("Amplitude", UnitOscillator.DEFAULT_AMPLITUDE).also {
            amplitude = it
        })
        addPort(UnitOutputPort("Output").also { output = it })
    }

    override fun generate() {
        val amplitudes = amplitude.getValues()
        val outputs = output.getValues()

        for (i in 0..< Synthesizer.FRAMES_PER_BLOCK) {
            outputs[i] = randomNum.nextRandomSample() * amplitudes[i]
        }
    }

    override fun getOutputPort(): UnitOutputPort {
        return output
    }
}
