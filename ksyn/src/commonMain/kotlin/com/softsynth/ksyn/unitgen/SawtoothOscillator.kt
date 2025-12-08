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

/**
 * Simple sawtooth oscillator.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
class SawtoothOscillator : UnitOscillator() {

    override fun generate() {
        val frequencies: DoubleArray = frequency.getValues()
        val amplitudes: DoubleArray = amplitude.getValues()
        val outputs: DoubleArray = output.getValues()

        // Variables have a single value.
        var currentPhase: Double = phase.getValue(0) // TODO support no partNum

        for (i in 0..<Synthesizer.FRAMES_PER_BLOCK) {
            /* Generate sawtooth phasor to provide phase for sine generation. */
            val phaseIncrement: Double = convertFrequencyToPhaseIncrement(frequencies[i])
            currentPhase = incrementWrapPhase(currentPhase, phaseIncrement)
            outputs[i] = currentPhase * amplitudes[i]
        }

        // Value needs to be saved for next time.
        phase.set(0, currentPhase)
    }
}
