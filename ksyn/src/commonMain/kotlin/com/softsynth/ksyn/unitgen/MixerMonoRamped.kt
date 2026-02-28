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
 * Similar to MixerMono but the gain and amplitude ports are smoothed using short linear ramps. So
 * you can control them with knobs and not hear any zipper noise.
 * 
 * @author Phil Burk (C) 2014 Mobileer Inc
 */
class MixerMonoRamped(numInputs: Int) : MixerMono(numInputs) {
    private val unzippers: Array<Unzipper> = Array(numInputs) { Unzipper() }
    private val amplitudeUnzipper = Unzipper()

    override fun generate() {
        val amplitudes = amplitude.getValues(0)
        val outputs = output.getValues(0)

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            var sum = 0.0f
            for (n in 0 until input.numParts) {
                val inputs = input.getValues(n)
                val gains = gain.getValues(n)
                val smoothGain = unzippers[n].smooth(gains[i])
                sum += inputs[i] * smoothGain
            }
            outputs[i] = sum * amplitudeUnzipper.smooth(amplitudes[i])
        }
    }
}
