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
import kotlin.math.abs

/**
 * Sawtooth DPW oscillator (a sawtooth with reduced aliasing).
 * Based on a paper by Antti Huovilainen and Vesa Valimaki:
 * http://www.scribd.com/doc/33863143/New-Approaches-to-Digital-Subtractive-Synthesis
 *
 * @author Phil Burk and Lisa Tolentino (C) 2009 Mobileer Inc
 */
class SawtoothOscillatorDPW : UnitOscillator() {
    private var z1: Double = 0.0
    private var z2: Double = 0.0

    override fun generate() {
        val frequencies = frequency.getValues()
        val amplitudes = amplitude.getValues()
        val outputs = output.getValues()

        var currentPhase = phase.getValue(0).toDouble()

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            /* Generate raw sawtooth phaser. */
            val phaseIncrement = convertFrequencyToPhaseIncrement(frequencies[i].toDouble())
            currentPhase = incrementWrapPhase(currentPhase, phaseIncrement)

            /* Square the raw sawtooth. */
            val squared = currentPhase * currentPhase
            // Differentiate using a delayed value.
            val diffed = squared - z2
            z2 = z1
            z1 = squared

            /* Calculate scaling based on phaseIncrement */
            var pinc = phaseIncrement
            if (pinc < 0.0) {
                pinc = -pinc
            }

            // If the frequency is very low then just use the raw sawtooth.
            // This avoids divide by zero problems and scaling problems.
            val dpw = if (pinc < VERY_LOW_FREQUENCY) {
                currentPhase
            } else {
                diffed * 0.25 / pinc
            }

            outputs[i] = (amplitudes[i].toDouble() * dpw).toSample()
        }

        phase.set(0, currentPhase)
    }

    companion object {
        private const val VERY_LOW_FREQUENCY = 2.0 * 0.1 / 44100.0
    }
}
