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
import com.softsynth.ksyn.ports.UnitOutputPort

/**
 * Multi-channel mixer with mono output and master amplitude.
 * 
 * @author Phil Burk (C) 2014 Mobileer Inc
 * @see MixerMonoRamped
 * @see MixerStereo
 */
open class MixerMono(numInputs: Int) : UnitGenerator(), UnitSink, UnitSource {
    val input: UnitInputPort
    /**
     * Linear gain for the corresponding input.
     */
    val gain: UnitInputPort
    /**
     * Master gain control.
     */
    val amplitude: UnitInputPort
    val output: UnitOutputPort

    init {
        input = UnitInputPort(numInputs, "Input")
        addPort(input)
        gain = UnitInputPort(numInputs, "Gain", 1.0)
        addPort(gain)
        amplitude = UnitInputPort("Amplitude", 1.0)
        addPort(amplitude)
        output = UnitOutputPort(numOutputs, "Output")
        addPort(output)
    }

    override fun generate() {
        val amplitudes = amplitude.getValues(0)
        val outputs = output.getValues(0)

        // Reset outputs to zero first
        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            outputs[i] = 0f
        }

        for (n in 0 until input.numParts) {
            val inputs = input.getValues(n)
            val gains = gain.getValues(n)
            for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
                outputs[i] += inputs[i] * gains[i]
            }
        }

        // Apply master amplitude
        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            outputs[i] *= amplitudes[i]
        }
    }
    
    open val numOutputs: Int
        get() = 1

    override fun getInputPort(): UnitInputPort {
        return input
    }

    override fun getOutputPort(): UnitOutputPort {
        return output
    }
    
    override fun getUnitGenerator(): UnitGenerator {
        return this
    }
}
