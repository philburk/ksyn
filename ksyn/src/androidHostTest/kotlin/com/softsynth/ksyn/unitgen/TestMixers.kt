/*
 * Copyright 2023 Phil Burk, Mobileer Inc
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

import kotlin.test.Test
import kotlin.test.assertEquals

class TestMixers : NonRealTimeTestCase() {

    @Test
    fun testMixerMono() {
        val mixer = MixerMono(2)
        synthesisEngine.add(mixer)
        
        mixer.input.set(0, 0.5f)
        mixer.gain.set(0, 0.8f) // 0.5 * 0.8 = 0.4
        
        mixer.input.set(1, -0.3f)
        mixer.gain.set(1, 0.5f) // -0.3 * 0.5 = -0.15
        
        mixer.amplitude.set(0.5) // overall amplitude
        // sum = 0.4 - 0.15 = 0.25
        // output = 0.25 * 0.5 = 0.125
        
        synthesisEngine.start()
        mixer.start()
        checkSleepUntil(synthesisEngine.currentTime + 0.01)
        
        val expected = 0.125f
        assertEquals(expected, mixer.output.value, 0.001f, "mixer mono output")
    }

    @Test
    fun testMixerStereo() {
        val mixer = MixerStereo(2)
        synthesisEngine.add(mixer)
        
        // Channel 0: Pan -1.0 (Left), input 1.0, gain 1.0
        mixer.input.set(0, 1.0f)
        mixer.gain.set(0, 1.0f)
        mixer.pan.set(0, -1.0f)
        
        // Channel 1: Pan 1.0 (Right), input 0.5, gain 1.0
        mixer.input.set(1, 0.5f)
        mixer.gain.set(1, 1.0f)
        mixer.pan.set(1, 1.0f)
        
        mixer.amplitude.set(1.0)
        
        synthesisEngine.start()
        mixer.start()
        checkSleepUntil(synthesisEngine.currentTime + 0.01)
        
        // Channel 0 left = 1.0, right = 0.0
        // Channel 1 left = 0.0, right = 0.5
        assertEquals(1.0f, mixer.output.getValue(0), 0.001f, "mixer stereo left")
        assertEquals(0.5f, mixer.output.getValue(1), 0.001f, "mixer stereo right")
    }

    @Test
    fun testMixerStereoRamped() {
        val mixer = MixerStereoRamped(2)
        synthesisEngine.add(mixer)
        
        mixer.input.set(0, 1.0f)
        mixer.gain.set(0, 1.0f)
        mixer.pan.set(0, -1.0f)
        mixer.amplitude.set(1.0)
        
        synthesisEngine.start()
        mixer.start()
        
        // Ramping takes time, check wait
        checkSleepUntil(synthesisEngine.currentTime + 0.08)
        
        assertEquals(1.0f, mixer.output.getValue(0), 0.01f, "ramped mixer stereo left")
        // TODO Add test for ramp by checking for absence of sharp edge.
    }
}
