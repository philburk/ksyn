/*
 * Copyright 2024 Phil Burk, Mobileer Inc
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
import kotlin.test.assertTrue
import kotlin.math.abs

class TestPitchToFrequency {
    @Test
    fun testConversion() {
        val p2f = PitchToFrequency()
        val epsilon = 0.01

        // Test Concert A (MIDI 69)
        p2f.input.set(69.0)
        p2f.generate()
        var frequency = p2f.output.getValue(0).toDouble()
        assertTrue(abs(frequency - 440.0) < epsilon, "Pitch 69 should be 440Hz, got $frequency")

        // Test Octave Above (MIDI 81)
        p2f.input.set(81.0)
        p2f.generate()
        frequency = p2f.output.getValue(0).toDouble()
        assertTrue(abs(frequency - 880.0) < epsilon, "Pitch 81 should be 880Hz, got $frequency")

        // Test Octave Below (MIDI 57)
        p2f.input.set(57.0)
        p2f.generate()
        frequency = p2f.output.getValue(0).toDouble()
        assertTrue(abs(frequency - 220.0) < epsilon, "Pitch 57 should be 220Hz, got $frequency")

        // Test Middle C (MIDI 60)
        p2f.input.set(60.0)
        p2f.generate()
        frequency = p2f.output.getValue(0).toDouble()
        // 440 * 2^(-9/12) ≈ 261.625565
        assertTrue(abs(frequency - 261.625) < epsilon, "Pitch 60 should be approx 261.625Hz, got $frequency")
    }
}
