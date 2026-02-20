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

import com.softsynth.ksyn.engine.MultiTable
import com.softsynth.ksyn.ports.UnitInputPort

/**
 * Oscillator that can change its shape from sine to sawtooth to pulse.
 *
 * @author Phil Burk (C) 2016 Mobileer Inc
 */
class MorphingOscillatorBL : PulseOscillatorBL() {
    /**
     * Controls the shape of the waveform.
     * The shape varies continuously from a sine wave at -1.0,
     * to a sawtooth at 0.0 to a pulse wave at 1.0.
     */
    val shape = UnitInputPort("Shape")

    init {
        addPort(shape)
        shape.setup(-1.0, 0.0, 1.0)
    }

    override fun generateBL(multiTable: MultiTable, currentPhase: Double, positivePhaseIncrement: Double, flevel: Double, i: Int): Double {
        val shapes = shape.getValues()
        val s = shapes[i].toDouble()

        if (s < 0.0) {
            // Squeeze flevel towards the pure sine table.
            val squeezedLevel = flevel + flevel * s
            return multiTable.calculateSawtooth(currentPhase, positivePhaseIncrement, squeezedLevel)
        } else {
            val widths = width.getValues()
            var w = widths[i]
            w = if (w > 0.999f) 0.999f else (if (w < -0.999f) -0.999f else w)

            val val1 = multiTable.calculateSawtooth(currentPhase, positivePhaseIncrement, flevel)
            // Generate second sawtooth so we can add them together.
            var phase2 = currentPhase + 1.0 - w // 180 degrees out of phase
            if (phase2 >= 1.0) {
                phase2 -= 2.0
            }
            val val2 = multiTable.calculateSawtooth(phase2, positivePhaseIncrement, flevel)

            /*
             * Need to adjust amplitude based on positive phaseInc. little less than half at
             * Nyquist/2.0!
             */
            val scale = 1.0 - positivePhaseIncrement
            return scale * (val1 - ((val2 + w) * s)) // apply shape morphing
        }
    }
}
