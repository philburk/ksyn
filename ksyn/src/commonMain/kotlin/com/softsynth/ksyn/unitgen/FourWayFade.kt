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
 * FourWayFade unit.
 * <P>
 * Mix inputs 0-3 based on the value of two fade ports. You can think of the four inputs arranged
 * clockwise as follows.
 * </P>
 *
 * <PRE>
 *      input[0] ---- input[1]
 *        |             |
 *        |             |
 *        |             |
 *      input[3] ---- input[2]
 * </PRE>
 *
 * The "fade" port has two parts. Fade[0] fades between the pair of inputs (0,3) and the pair of
 * inputs (1,2). Fade[1] fades between the pair of inputs (0,1) and the pair of inputs (3,2).
 *
 * <PRE>
 *    Fade[0]    Fade[1]    Output
 *      -1         -1       Input[3]
 *      -1         +1       Input[0]
 *      +1         -1       Input[2]
 *      +1         +1       Input[1]
 *
 *
 *      -----Fade[0]-----&gt;
 *
 *         A
 *         |
 *         |
 *      Fade[1]
 *         |
 *         |
 * </PRE>
 * <P>
 *
 * @author (C) 1997-2009 Phil Burk, Mobileer Inc
 */
class FourWayFade : UnitGenerator() {
    val input: UnitInputPort
    val fade: UnitInputPort
    val output: UnitOutputPort

    /* Define Unit Ports used by connect() and set(). */
    init {
        input = UnitInputPort(4, "Input")
        addPort(input)
        fade = UnitInputPort(2, "Fade")
        addPort(fade)
        output = UnitOutputPort()
        addPort(output)
    }

    override fun generate() {
        val inputAs = input.getValues(0)
        val inputBs = input.getValues(1)
        val inputCs = input.getValues(2)
        val inputDs = input.getValues(3)
        
        val fadeLRs = fade.getValues(0)
        val fadeFBs = fade.getValues(1)
        val outputs = output.getValues(0)

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            // Scale and offset to 0.0 to 1.0 range.
            val gainLR = (fadeLRs[i] * 0.5f) + 0.5f
            val temp = 1.0f - gainLR
            val mixFront = (inputAs[i] * temp) + (inputBs[i] * gainLR)
            val mixBack = (inputDs[i] * temp) + (inputCs[i] * gainLR)

            val gainFB = (fadeFBs[i] * 0.5f) + 0.5f
            outputs[i] = (mixBack * (1.0f - gainFB)) + (mixFront * gainFB)
        }
    }
}
