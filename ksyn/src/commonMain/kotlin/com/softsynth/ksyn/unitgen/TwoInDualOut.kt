/*
 * Copyright 2004 Phil Burk, Mobileer Inc
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
 * This unit combines two discrete inputs into a dual (stereo) output.
 * 
 * <pre>
 *  output[0] = inputA
 *  output[1] = inputB
 * </pre>
 * 
 * @author (C) 2004-2009 Phil Burk, SoftSynth.com
 */
class TwoInDualOut : UnitGenerator() {
    val inputA: UnitInputPort
    val inputB: UnitInputPort
    val output: UnitOutputPort

    init {
        inputA = UnitInputPort("InputA")
        addPort(inputA)
        
        inputB = UnitInputPort("InputB")
        addPort(inputB)
        
        output = UnitOutputPort(2, "Output")
        addPort(output)
    }

    override fun generate() {
        val inputAs = inputA.getValues()
        val inputBs = inputB.getValues()
        val output0s = output.getValues(0)
        val output1s = output.getValues(1)

        for (i in 0 until output0s.size) {
            output0s[i] = inputAs[i]
            output1s[i] = inputBs[i]
        }
    }
}
