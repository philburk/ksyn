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
 * Provides access to one specific channel of the audio input. For ChannelIn to work you must call
 * the Synthesizer start() method with numInputChannels &gt; 0.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
class ChannelIn(channelIndex: Int = 0) : UnitGenerator() {
    val output: UnitOutputPort
    var channelIndex: Int = channelIndex

    init {
        output = UnitOutputPort("Output")
        addPort(output)
    }

    override fun generate() {
        val outputs = output.getValues(0)
        
        // Ensure the engine is running and we can fetch the buffer
        synthesisEngine?.let { engine ->
            try {
                val buffer = engine.getInputBuffer(channelIndex)
                for (i in 0 until outputs.size) {
                    outputs[i] = buffer[i].toFloat()
                }
            } catch (e: Exception) {
                // If audio input is not configured, output silence.
                for (i in 0 until outputs.size) {
                    outputs[i] = 0.0f
                }
            }
        } ?: run {
            for (i in 0 until outputs.size) {
                outputs[i] = 0.0f
            }
        }
    }
}
