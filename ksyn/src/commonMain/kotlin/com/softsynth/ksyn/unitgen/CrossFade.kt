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
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.toSample

/**
 * Linear CrossFade between parts of the input.
 * <P>
 * Mix input[0] and input[1] based on the value of "fade". When fade is -1, output is all input[0].
 * When fade is 0, output is half input[0] and half input[1]. When fade is +1, output is all
 * input[1].
 * <P>
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 * @version 016
 * @see Pan
 */
class CrossFade : UnitGenerator() {
    val input = UnitInputPort(2, "Input")
    val fade = UnitInputPort("Fade")
    val output = UnitOutputPort()

    init {
        addPort(input)
        addPort(fade)
        fade.setup(-1.0, 0.0, 1.0)
        addPort(output)
    }

    override fun generate() {
        val input0s = input.getValues(0)
        val input1s = input.getValues(1)
        val fades = fade.getValues()
        val outputs = output.getValues()

        for (i in 0..<Synthesizer.FRAMES_PER_BLOCK) {
            // Scale and offset to 0.0 to 1.0 range.
            val gain = (fades[i] * 0.5f) + 0.5f
            outputs[i] = ((input0s[i] * (1.0f - gain)) + (input1s[i] * gain)).toSample()
        }
    }
}
