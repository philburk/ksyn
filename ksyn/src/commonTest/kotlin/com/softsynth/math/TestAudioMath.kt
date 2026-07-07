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

package com.softsynth.math

import kotlin.test.*
import kotlin.math.abs

class TestAudioMath {

    private val epsilon = 1.0e-6

    @BeforeTest
    fun setUp() {
        AudioMath.concertAFrequency = AudioMath.CONCERT_A_FREQUENCY
    }

    @Test
    fun testDecibels() {
        assertEquals(0.0, AudioMath.amplitudeToDecibels(1.0), epsilon)
        assertEquals(1.0, AudioMath.decibelsToAmplitude(0.0), epsilon)

        val halfAmpDB = AudioMath.amplitudeToDecibels(0.5)
        assertTrue(abs(halfAmpDB - (-6.0205999)) < 0.0001)
        assertEquals(0.5, AudioMath.decibelsToAmplitude(halfAmpDB), epsilon)

        val doubleAmpDB = AudioMath.amplitudeToDecibels(2.0)
        assertTrue(abs(doubleAmpDB - 6.0205999) < 0.0001)
        assertEquals(2.0, AudioMath.decibelsToAmplitude(doubleAmpDB), epsilon)
    }

    @Test
    fun testPitchFrequency() {
        // Concert A
        assertEquals(440.0, AudioMath.pitchToFrequency(69.0), epsilon)
        assertEquals(69.0, AudioMath.frequencyToPitch(440.0), epsilon)

        // Middle C (MIDI 60)
        val middleCFreq = AudioMath.pitchToFrequency(60.0)
        // 440 * 2^(-9/12) ≈ 261.625565
        assertTrue(abs(middleCFreq - 261.625565) < 0.0001)
        assertEquals(60.0, AudioMath.frequencyToPitch(middleCFreq), epsilon)

        // Octave above Concert A
        assertEquals(880.0, AudioMath.pitchToFrequency(81.0), epsilon)
        assertEquals(81.0, AudioMath.frequencyToPitch(880.0), epsilon)
    }

    @Test
    fun testSemitones() {
        assertEquals(1.0, AudioMath.semitonesToFrequencyScaler(0.0), epsilon)
        assertEquals(2.0, AudioMath.semitonesToFrequencyScaler(12.0), epsilon)
        assertEquals(0.5, AudioMath.semitonesToFrequencyScaler(-12.0), epsilon)
        
        val fifth = 7.0
        val fifthScaler = AudioMath.semitonesToFrequencyScaler(fifth)
        assertTrue(abs(fifthScaler - 1.498307) < 0.0001)
    }

    @Test
    fun testCustomConcertA() {
        AudioMath.concertAFrequency = 432.0
        
        assertEquals(432.0, AudioMath.pitchToFrequency(69.0), epsilon)
        assertEquals(69.0, AudioMath.frequencyToPitch(432.0), epsilon)
        
        // 432 * 2^(12/12) = 864
        assertEquals(864.0, AudioMath.pitchToFrequency(81.0), epsilon)
    }
}
