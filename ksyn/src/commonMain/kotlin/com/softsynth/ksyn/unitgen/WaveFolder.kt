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
import kotlin.math.floor
import kotlin.math.max

/**
 * Wave Folder
 *
 * Fold the input waveform by passing it through a sine function
 * and output the results in the range of -1.0 to 1.0.
 *
 * This works best if the amplitude of the input waveform is close to 1.0.
 *
 * @author Phil Burk (C) 2020 Mobileer Inc
 */
class WaveFolder : UnitFilter() {
    /**
     * The depth of the wave-folding effect.
     * At zero there should be no audible effect.
     */
    val amount: UnitInputPort

    companion object {
        private const val MAX_SCALE = 8.0
    }

    init {
        amount = UnitInputPort("Amount")
        addPort(amount)
        amount.setup(0.0, 0.0, MAX_SCALE)
    }

    override fun generate() {
        val inputs = input.getValues()
        val scales = amount.getValues()
        val outputs = output.getValues()

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            val inputValue = inputs[i].toDouble()
            // Prevent blowup near zero.
            val scaleValue = max(scales[i].toDouble(), 0.00001)

            var phase = inputValue * scaleValue

            // Clip to -1/+1 range for fastSin even for extreme ranges.
            phase = (phase + 256.0 + 1.0) * 0.5
            phase -= floor(phase)
            phase = (phase * 2.0) - 1.0

            // Fold using a sine function that ranges -1 to +1.
            var folded = SineOscillator.fastSin(phase)
            
            // Try to maintain constant amplitude at small scale value.
            // Based on sin(x) ~= x for low values of x.
            if (scaleValue < 1.0) {
                folded /= scaleValue * (((1.0 - PI) * scaleValue) + PI)
            }
            outputs[i] = folded.toFloat()
        }
    }
}
