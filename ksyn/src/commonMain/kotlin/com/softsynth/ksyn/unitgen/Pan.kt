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
import com.softsynth.ksyn.ports.UnitOutputPort

/**
 * Pan unit. The profile is constant amplitude and not constant energy.
 * <P>
 * Takes an input and pans it between two outputs based on value of pan. When pan is -1, output[0]
 * is input, and output[1] is zero. When pan is 0, output[0] and output[1] are both input/2. When
 * pan is +1, output[0] is zero, and output[1] is input.
 * <P>
 *
 * @author (C) 1997 Phil Burk, SoftSynth.com
 * @see Select
 */
class Pan : UnitGenerator() {
    val input: UnitInputPort
    /**
     * Pan control varies from -1.0 for full left to +1.0 for full right. Set to 0.0 for center.
     */
    val pan: UnitInputPort
    val output: UnitOutputPort

    init {
        input = UnitInputPort("Input")
        addPort(input)
        pan = UnitInputPort("Pan")
        pan.setup(-1.0, 0.0, 1.0)
        addPort(pan)
        output = UnitOutputPort(2, "Output")
        addPort(output)
    }

    override fun generate() {
        val inputs = input.getValues()
        val panPtr = pan.getValues()
        val outputs0 = output.getValues(0)
        val outputs1 = output.getValues(1)

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            val gainB = (panPtr[i] * 0.5f) + 0.5f /* Scale and offset to 0.0 to 1.0 */
            val gainA = 1.0f - gainB
            val inVal = inputs[i]
            
            outputs0[i] = inVal * gainA
            outputs1[i] = inVal * gainB
        }
    }
}
