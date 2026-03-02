/*
 * Copyright 2010 Phil Burk, Mobileer Inc
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
import com.softsynth.ksyn.ports.UnitOutputPort

/**
 * Simple stereo sample player. Play one sample per audio frame with no interpolation.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
class FixedRateStereoReader : SequentialDataReader() {
    init {
        output = UnitOutputPort(2, "Output")
        addPort(output)
        dataQueue.numChannels = 2
    }

    override fun generate() {
        val amplitudes = amplitude.getValues()
        val output0s = output.getValues(0)
        val output1s = output.getValues(1)

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            if (dataQueue.hasMore()) {
                dataQueue.beginFrame(synthesisEngine!!.framePeriod)
                var fdata = dataQueue.readCurrentChannelDouble(0)
                val amp = amplitudes[i].toDouble()
                output0s[i] = (fdata * amp).toSample()
                fdata = dataQueue.readCurrentChannelDouble(1)
                output1s[i] = (fdata * amp).toSample()
                dataQueue.endFrame()
            } else {
                output0s[i] = 0.0f.toSample()
                output1s[i] = 0.0f.toSample()
                if (dataQueue.testAndClearAutoStop()) {
                    autoStop()
                }
            }
            dataQueue.firePendingCallbacks()
        }
    }
}
