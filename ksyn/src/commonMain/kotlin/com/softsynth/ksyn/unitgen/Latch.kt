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

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort

/**
 * Latch or hold an input value.
 * <p>
 * Pass a value unchanged if gate true, otherwise output held value.
 * <p>
 * output = ( gate &gt; 0.0 ) ? input : previous_output;
 *
 * @author (C) 1997-2010 Phil Burk, Mobileer Inc
 * @see EdgeDetector
 */
class Latch : UnitFilter() {
    var gate = UnitInputPort("Gate")
    private var held: Float = 0.0f

    /* Define Unit Ports used by connect() and set(). */
    init {
        addPort(gate)
    }

    override fun generate() {
        val inputs = input.getValues()
        val gates = gate.getValues()
        val outputs = output.getValues()

        for (i in 0..<Synthesizer.FRAMES_PER_BLOCK) {
            if (gates[i] > 0.0f) {
                held = inputs[i]
            }
            outputs[i] = held
        }
    }
}
