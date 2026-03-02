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

/**
 * Provides access to one channel of the audio output.
 * For more than two channels you must call
 * the Synthesizer start() method with numOutputChannels &gt; 2.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
class ChannelOut(channelIndex: Int = 0) : UnitGenerator() {
    val input: UnitInputPort
    var channelIndex: Int = channelIndex

    init {
        input = UnitInputPort("Input")
        addPort(input)
    }

    /**
     * This unit won't do anything unless you start() it.
     */
    override val isStartRequired: Boolean
        get() = true

    override fun generate() {
        val inputs = input.getValues(0)
        
        synthesisEngine?.let { engine ->
            try {
                val buffer = engine.getOutputBuffer(channelIndex)
                for (i in 0 until inputs.size) {
                    buffer[i] += inputs[i]
                }
            } catch (e: Exception) {
                // Ignore if buffer not ready or out of bounds.
            }
        }
    }
}
