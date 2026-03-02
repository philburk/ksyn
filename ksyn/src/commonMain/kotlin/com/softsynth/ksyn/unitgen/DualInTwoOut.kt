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

import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort

/**
 * This unit splits a dual (stereo) input to two discrete outputs. <br>
 * 
 * <pre>
 * outputA = input[0];
 * outputB = input[1];
 * </pre>
 * 
 * <br>
 * 
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
class DualInTwoOut : UnitGenerator() {
    val input: UnitInputPort
    val outputA: UnitOutputPort
    val outputB: UnitOutputPort

    init {
        input = UnitInputPort(2, "Input")
        addPort(input)
        
        outputA = UnitOutputPort("OutputA")
        addPort(outputA)
        
        outputB = UnitOutputPort("OutputB")
        addPort(outputB)
    }

    override fun generate() {
        val input0s = input.getValues(0)
        val input1s = input.getValues(1)
        val outputAs = outputA.getValues()
        val outputBs = outputB.getValues()

        for (i in 0 until outputAs.size) {
            outputAs[i] = input0s[i]
            outputBs[i] = input1s[i]
        }
    }
}
