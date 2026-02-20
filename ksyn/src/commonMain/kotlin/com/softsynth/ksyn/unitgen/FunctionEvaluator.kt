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
import com.softsynth.ksyn.ports.UnitFunctionPort
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.toSample

/**
 * Convert an input value to an output value.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 * @see com.softsynth.ksyn.data.Function
 */
class FunctionEvaluator : UnitFilter() {
    val amplitude = UnitInputPort("Amplitude", 1.0)
    val function = UnitFunctionPort("Function")

    init {
        addPort(amplitude)
        addPort(function)
    }

    override fun generate() {
        val inputs = input.getValues()
        val amplitudes = amplitude.getValues()
        val outputs = output.getValues()
        val functionObject = function.get()

        for (i in 0..<Synthesizer.FRAMES_PER_BLOCK) {
            val result = functionObject.evaluate(inputs[i].toDouble()) * amplitudes[i].toDouble()
            outputs[i] = result.toSample()
        }
    }
}
