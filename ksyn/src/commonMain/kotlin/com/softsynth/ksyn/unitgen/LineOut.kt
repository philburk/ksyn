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

/**
 * Input audio is mixed into the primary output buffer.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
class LineOut : UnitGenerator(), UnitSink {
    override var input: UnitInputPort

    init {
        addPort(UnitInputPort(2, "Input").also { input = it })
    }

    override fun generate() {
        val inputs0 = input.getValues(0)
        val inputs1 = input.getValues(1)
        val buffer0 = synthesisEngine!!.getOutputBuffer(0)
        val buffer1 = synthesisEngine!!.getOutputBuffer(1)
        for (i in 0..< Synthesizer.FRAMES_PER_BLOCK) {
            buffer0[i] += inputs0[i]
            buffer1[i] += inputs1[i]
        }
    }

    override val isStartRequired: Boolean
        /**
         * This unit won't do anything unless you start() it.
         */
        get() = true

}
