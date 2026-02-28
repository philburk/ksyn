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
import kotlin.test.assertTrue

class TestPitchDetector : NonRealTimeTestCase() {

    @Test
    fun testSineWaveDetection() {
        val osc = SineOscillator()
        val detector = PitchDetector()
        
        synthesisEngine.add(osc)
        synthesisEngine.add(detector)
        
        val targetFreq = 440.0
        osc.frequency.set(targetFreq)
        osc.amplitude.set(1.0)
        
        osc.output.connect(detector.input)
        
        synthesisEngine.start()
        osc.start()
        detector.start()
        
        // Wait for enough cycles. The AutoCorrelator window is max 54ms at 44100.
        // Wait for 200ms to allow a full valid correlation update.
        checkSleepUntil(synthesisEngine.currentTime + 0.2)
        
        val freq = detector.frequency.value
        val conf = detector.confidence.value
        
        assertEquals(targetFreq.toFloat(), freq, 5.0f, "detected frequency")
        // Confidence should be high
        assertTrue(conf > 0.8f, "detection confidence should be high")
    }
}
