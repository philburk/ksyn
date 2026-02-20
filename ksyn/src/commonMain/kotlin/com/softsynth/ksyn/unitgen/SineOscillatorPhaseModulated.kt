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

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort
import kotlin.math.PI
import kotlin.math.sin
import com.softsynth.ksyn.toSample

/**
 * Sine oscillator with a phase modulation input. Phase modulation is similar to frequency
 * modulation but is easier to use in some ways.
 * 
 * <pre>
 * output = sin(PI * (phase + modulation))
 * </pre>
 * 
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
class SineOscillatorPhaseModulated : SineOscillator {
    val modulation = UnitInputPort("Modulation")

    constructor() : super() {
        addPort(modulation)
    }

    override fun generate() {
        val frequencies = frequency.getValues()
        val amplitudes = amplitude.getValues()
        val outputs = output.getValues()
        val modulations = modulation.getValues()
        var currentPhase = phase.getValue(0).toDouble()

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            /* Generate sawtooth phaser to provide phase for sine generation. */
            val phaseIncrement = convertFrequencyToPhaseIncrement(frequencies[i].toDouble())
            currentPhase = incrementWrapPhase(currentPhase, phaseIncrement)
            val modulatedPhase = currentPhase + modulations[i].toDouble()
            val value = sin(modulatedPhase * PI)
            
            outputs[i] = (value * amplitudes[i].toDouble()).toSample()
        }

        phase.set(0, currentPhase)
    }
}
