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

/**
 * This unit performs a signed subtraction on its two inputs.
 *
 * <pre>
 * output = inputA - inputB
</pre> *
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 * @version 016
 * @see MultiplyAdd
 * @see Subtract
 */
class Subtract : UnitBinaryOperator() {
    override fun generate() {
        val aValues = inputA.getValues()
        val bValues = inputB.getValues()
        val outputs = output.getValues()

        for (i in 0..<Synthesizer.FRAMES_PER_BLOCK) {
            outputs[i] = aValues[i] - bValues[i]
        }
    }
}
