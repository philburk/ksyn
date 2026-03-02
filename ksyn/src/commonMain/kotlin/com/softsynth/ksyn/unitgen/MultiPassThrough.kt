/*
 * Copyright 2011 Phil Burk, Mobileer Inc
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

import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort

/**
 * Pass the input through to the output unchanged. This is often used for distributing a signal to
 * multiple ports inside a circuit. It can also be used as a summing node, in other words, a mixer.
 *
 * This is just like PassThrough except the input and output ports have multiple parts.
 * The default is two parts, ie. stereo.
 *
 * @author Phil Burk (C) 2016 Mobileer Inc
 */
class MultiPassThrough(val numParts: Int = 2) : UnitGenerator(), UnitSink, UnitSource {
    override val input: UnitInputPort
    override val output: UnitOutputPort

    init {
        input = UnitInputPort(numParts, "Input")
        addPort(input)
        
        output = UnitOutputPort(numParts, "Output")
        addPort(output)
    }

    override fun generate() {
        for (partIndex in 0 until numParts) {
            val inputs = input.getValues(partIndex)
            val outputs = output.getValues(partIndex)

            for (i in 0 until outputs.size) {
                outputs[i] = inputs[i]
            }
        }
    }
}
