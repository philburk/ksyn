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
import com.softsynth.ksyn.KSyn
import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.toSample
import com.softsynth.ksyn.util.PseudoRandom

/**
 * RedNoise unit. This unit interpolates straight line segments between pseudo-random numbers to
 * produce "red" noise. It is a grittier alternative to the white generator WhiteNoise. It is also
 * useful as a slowly changing random control generator for natural sounds. Frequency port controls
 * the number of times per second that a new random number is chosen.
 *
 * @author (C) 1997 Phil Burk, SoftSynth.com
 * @see WhiteNoise
 */
class RedNoise : UnitOscillator() {
    private val randomNum: PseudoRandom
    protected var previousNoise = KSyn.ZERO
    protected var currentNoise = KSyn.ZERO

    /* Define Unit Ports used by connect() and set(). */
    init {
        randomNum = PseudoRandom()
    }

    override fun generate() {
        val amplitudes = amplitude.getValues()
        val frequencies = frequency.getValues()
        val outputs = output.getValues()
        var currentPhase: Double = phase.getValue(0).toDouble()
        var phaseIncrement: Double
        var currOutput: AudioSample

        for (i in 0..< Synthesizer.FRAMES_PER_BLOCK) {
            // compute phase
            phaseIncrement = frequencies[i] * framePeriod

            // verify that phase is within minimums and is not negative
            if (phaseIncrement < 0.0) {
                phaseIncrement = 0.0 - phaseIncrement
            }
            if (phaseIncrement > 1.0) {
                phaseIncrement = 1.0
            }

            currentPhase += phaseIncrement

            // calculate new random whenever phase passes 1.0
            if (currentPhase > 1.0) {
                previousNoise = currentNoise
                currentNoise = randomNum.nextRandomSample()
                // reset phase for interpolation
                currentPhase -= 1.0
            }

            // interpolate current
            currOutput = previousNoise + (currentPhase.toSample() * (currentNoise - previousNoise))
            outputs[i] = currOutput * amplitudes[i]
        }

        // store new phase
        phase.set(0, currentPhase)
    }
}
