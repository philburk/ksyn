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
 * PanControl unit.
 * <P>
 * Generates control signals that can be used to control a mixer or the amplitude ports of two
 * units.
 * 
 * <PRE>
 * temp = (pan * 0.5) + 0.5;
 * output[0] = temp;
 * output[1] = 1.0 - temp;
 * </PRE>
 * <P>
 * 
 * @author (C) 1997-2009 Phil Burk, SoftSynth.com
 */
class PanControl : UnitGenerator() {
    val pan: UnitInputPort
    val output: UnitOutputPort

    /* Define Unit Ports used by connect() and set(). */
    init {
        pan = UnitInputPort("Pan")
        addPort(pan)
        output = UnitOutputPort(2, "Output")
        addPort(output)
    }

    override fun generate() {
        val panPtr = pan.getValues()
        val output0s = output.getValues(0)
        val output1s = output.getValues(1)

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            val gainB = (panPtr[i] * 0.5f) + 0.5f /* Scale and offset to 0.0 to 1.0 */
            output0s[i] = 1.0f - gainB
            output1s[i] = gainB
        }
    }
}
