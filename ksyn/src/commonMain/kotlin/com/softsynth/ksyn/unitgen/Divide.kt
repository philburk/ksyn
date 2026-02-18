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
import com.softsynth.ksyn.toSample

/**
 * This unit divides its two inputs. <br></br>
 *
 * <pre>
 * output = inputA / inputB
</pre> *
 *
 * <br></br>
 * Note that this unit is protected from dividing by zero. But you can still get some very big
 * outputs.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 * @version 016
 * @see Multiply
 * @see Subtract
 */
class Divide : UnitBinaryOperator() {

    override fun generate() {
        val aValues = inputA.getValues()
        val bValues = inputB.getValues()
        val outputs = output.getValues()
        val tiny = 0.0000001.toSample()
        val zero = 0.0.toSample()

        for (i in 0..<Synthesizer.FRAMES_PER_BLOCK) {
            /* Prevent divide by zero crash. */
            var b = bValues[i]
            if (b == zero) {
                b = tiny
            }

            outputs[i] = aValues[i] / b
        }
    }
}
