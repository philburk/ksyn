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
import com.softsynth.ksyn.engine.MultiTable
import com.softsynth.ksyn.toSample
import kotlin.math.abs

/**
 * Sawtooth oscillator that uses multiple wave tables for band limiting. This requires more CPU than
 * a plain SawtoothOscillator but has less aliasing at high frequencies.
 * 
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
open class SawtoothOscillatorBL : UnitOscillator() {

    override fun generate() {
        val multiTable = MultiTable.instance
        val frequencies = frequency.getValues()
        val amplitudes = amplitude.getValues()
        val outputs = output.getValues()
        
        var currentPhase = phase.getValue(0).toDouble()
        var phaseIncrement = convertFrequencyToPhaseIncrement(frequencies[0].toDouble())
        var positivePhaseIncrement = abs(phaseIncrement)
        
        // This is very expensive so we moved it outside the loop.
        val flevel = multiTable.convertPhaseIncrementToLevel(positivePhaseIncrement)

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            phaseIncrement = convertFrequencyToPhaseIncrement(frequencies[i].toDouble())
            currentPhase = incrementWrapPhase(currentPhase, phaseIncrement)
            positivePhaseIncrement = abs(phaseIncrement)

            val valOut = generateBL(multiTable, currentPhase, positivePhaseIncrement, flevel, i)
            outputs[i] = (valOut * amplitudes[i].toDouble()).toSample()
        }

        phase.set(0, currentPhase)
    }

    protected open fun generateBL(multiTable: MultiTable, currentPhase: Double, positivePhaseIncrement: Double, flevel: Double, i: Int): Double {
        return multiTable.calculateSawtooth(currentPhase, positivePhaseIncrement, flevel)
    }
}
