/*
 * Copyright 2014 Phil Burk, Mobileer Inc
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
 * Similar to MixerStereo but the gain, pan and amplitude ports are smoothed using short linear
 * ramps. So you can control them with knobs and not hear any zipper noise.
 * 
 * @author Phil Burk (C) 2014 Mobileer Inc
 */
class MixerStereoRamped(numInputs: Int) : MixerStereo(numInputs) {
    private val gainUnzippers: Array<Unzipper> = Array(numInputs) { Unzipper() }
    private val panUnzippers: Array<Unzipper> = Array(numInputs) { Unzipper() }
    private val amplitudeUnzipper: Unzipper = Unzipper()

    override fun generate() {
        val amplitudes = amplitude.getValues(0)
        val outputs0 = output.getValues(0)
        val outputs1 = output.getValues(1)

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            var sum0 = 0.0f
            var sum1 = 0.0f
            
            for (n in 0 until input.numParts) {
                val inputs = input.getValues(n)
                val gains = gain.getValues(n)
                val pans = pan.getValues(n)

                val tracker = panTrackers[n]
                val smoothPan = panUnzippers[n].smooth(pans[i])
                tracker.update(smoothPan)

                val smoothGain = gainUnzippers[n].smooth(gains[i])
                val scaledInput = inputs[i] * smoothGain
                sum0 += scaledInput * tracker.leftGain
                sum1 += scaledInput * tracker.rightGain
            }
            
            val amp = amplitudeUnzipper.smooth(amplitudes[i])
            outputs0[i] = sum0 * amp
            outputs1[i] = sum1 * amp
        }
    }
}
