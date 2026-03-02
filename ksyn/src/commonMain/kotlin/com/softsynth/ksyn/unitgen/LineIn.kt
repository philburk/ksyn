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

import com.softsynth.ksyn.ports.UnitOutputPort

/**
 * External audio input is sent to the output of this unit. The LineIn provides a stereo signal
 * containing channels 0 and 1. For LineIn to work you must call the Synthesizer start() method with
 * numInputChannels &gt; 0.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
class LineIn : UnitGenerator() {
    val output: UnitOutputPort

    init {
        output = UnitOutputPort(2, "Output")
        addPort(output)
    }

    override fun generate() {
        val outputs0 = output.getValues(0)
        val outputs1 = output.getValues(1)
        
        synthesisEngine?.let { engine ->
            try {
                val buffer0 = engine.getInputBuffer(0)
                val buffer1 = engine.getInputBuffer(1)
                for (i in 0 until outputs0.size) {
                    outputs0[i] = buffer0[i]
                    outputs1[i] = buffer1[i]
                }
            } catch (e: Exception) {
                for (i in 0 until outputs0.size) {
                    outputs0[i] = 0.0
                    outputs1[i] = 0.0
                }
            }
        } ?: run {
            for (i in 0 until outputs0.size) {
                outputs0[i] = 0.0
                outputs1[i] = 0.0
            }
        }
    }
}
