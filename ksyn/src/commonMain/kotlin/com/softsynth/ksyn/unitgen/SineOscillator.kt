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
import com.softsynth.ksyn.toSample
import kotlin.math.PI
import kotlin.math.sin

/**
 * Sine oscillator generates a frequency controlled sine wave. It is implemented using a fast Taylor
 * expansion.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
open class SineOscillator : UnitOscillator {
    constructor()

    constructor(freq: Double) {
        frequency.set(0, freq.toFloat())
    }

    constructor(freq: Double, amp: Double) {
        frequency.set(0, freq.toFloat())
        amplitude.set(0, amp.toFloat())
    }

    override fun generate() {
        val frequencies = frequency.getValues()
        val amplitudes = amplitude.getValues()
        val outputs = output.getValues()
        var currentPhase = phase.getValue(0).toDouble()

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            /* Generate sawtooth phasor to provide phase for sine generation. */
            val phaseIncrement = convertFrequencyToPhaseIncrement(frequencies[i].toDouble())
            currentPhase = incrementWrapPhase(currentPhase, phaseIncrement)
            
            val value = fastSin(currentPhase)
            outputs[i] = (value * amplitudes[i].toDouble()).toSample()
        }

        phase.set(0, currentPhase)
    }

    companion object {
        /**
         * Calculate sine using Taylor expansion. Do not use values outside the range.
         *
         * @param currentPhase in the range of -1.0 to +1.0 for one cycle
         */
        fun fastSin(currentPhase: Double): Double {
            // Factorial constants so code is easier to read.
            val IF3 = 1.0 / (2 * 3)
            val IF5 = IF3 / (4 * 5)
            val IF7 = IF5 / (6 * 7)
            val IF9 = IF7 / (8 * 9)
            val IF11 = IF9 / (10 * 11)

            /* Wrap phase back into region where results are more accurate. */
            val yp = if (currentPhase > 0.5) {
                1.0 - currentPhase
            } else if (currentPhase < -0.5) {
                -1.0 - currentPhase
            } else {
                currentPhase
            }

            val x = yp * PI
            val x2 = x * x
            /* Taylor expansion out to x**11/11! factored into multiply-adds */
            return x * (x2 * (x2 * (x2 * (x2 * ((x2 * (-IF11)) + IF9) - IF7) + IF5) - IF3) + 1)
        }
    }
}
