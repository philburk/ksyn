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

/**
 * This unit multiplies its two inputs. <br></br>
 *
 * <pre>
 * output = inputA * inputB
</pre> *
 *
 * <br></br>
 * Note that some units have an amplitude port, which controls an internal multiply. So you may not
 * need this unit.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 * @version 016
 * @see MultiplyAdd
 * @see Subtract
 */
class Multiply : UnitBinaryOperator {
    constructor()

    /** Connect a to inputA and b to inputB.  */
    constructor(a: UnitOutputPort, b: UnitOutputPort) {
        a.connect(inputA)
        b.connect(inputB)
    }

    /** Connect a to inputA and b to inputB and connect output to c.  */
    constructor(a: UnitOutputPort, b: UnitOutputPort, c: UnitInputPort) : this(a, b) {
        output.connect(c)
    }

    override fun generate() {
        val aValues = inputA.getValues()
        val bValues = inputB.getValues()
        val outputs = output.getValues()
        for (i in 0..< Synthesizer.FRAMES_PER_BLOCK) {
            outputs[i] = aValues[i] * bValues[i]
        }
    }
}
