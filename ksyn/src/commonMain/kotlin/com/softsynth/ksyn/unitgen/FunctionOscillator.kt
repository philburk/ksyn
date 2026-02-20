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
import com.softsynth.ksyn.ports.UnitFunctionPort
import com.softsynth.ksyn.toSample

/**
 * Oscillator that uses a Function object to define the waveform. Note that a DoubleTable can be
 * used as the Function.
 * 
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
class FunctionOscillator : UnitOscillator() {
    val function = UnitFunctionPort("Function")

    init {
        addPort(function)
    }

    override fun generate() {
        val frequencies = frequency.getValues()
        val amplitudes = amplitude.getValues()
        val outputs = output.getValues()

        val functionObject = function.get()

        var currentPhase = phase.getValue(0).toDouble()

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            // Generate sawtooth phasor to provide phase for function lookup.
            val phaseIncrement = convertFrequencyToPhaseIncrement(frequencies[i].toDouble())
            currentPhase = incrementWrapPhase(currentPhase, phaseIncrement)
            
            val value = functionObject.evaluate(currentPhase)
            outputs[i] = (value * amplitudes[i].toDouble()).toSample()
        }

        phase.set(0, currentPhase)
    }
}
