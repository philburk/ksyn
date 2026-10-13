/*
 * Copyright 2026 Phil Burk, Mobileer Inc
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

class TestGrainSourceSine {

    private val seconds = 10

    /**
     * Frequency in Hz, from the rising zero crossings in [seconds] of output. The crossing at
     * the very start is not seen, so this can read up to 1 / [seconds] Hz low.
     */
    private fun measureFrequency(rate: Double, frameRate: Int): Double {
        val source = GrainSourceSine()
        source.frameRate = frameRate.toFloat()
        source.setRate(rate)
        var crossings = 0
        var previous = source.next()
        repeat(frameRate * seconds - 1) {
            val value = source.next()
            if (previous < 0.0f && value >= 0.0f) crossings++
            previous = value
        }
        return crossings.toDouble() / seconds
    }

    private fun assertFrequency(expected: Double, actual: Double, message: String = "") {
        assertEquals(expected, actual, 1.5 / seconds, message)
    }

    @Test
    fun testRateOfOneIsConcertA() {
        // The same pitch whatever rate the synth runs at.
        for (frameRate in listOf(44100, 48000, 96000)) {
            assertFrequency(440.0, measureFrequency(1.0, frameRate), "at $frameRate Hz")
        }
    }

    @Test
    fun testRateScalesTheFrequency() {
        // So rate = Hz / 440 plays a given frequency.
        assertFrequency(880.0, measureFrequency(2.0, 48000))
        assertFrequency(220.0, measureFrequency(0.5, 48000))
        assertFrequency(261.6, measureFrequency(261.6256 / 440.0, 48000), "middle C")
    }

    @Test
    fun testANewSourceHasAUsableRate() {
        // A new source has no frame rate yet, so its first rate must not divide by zero.
        val source = GrainSourceSine()
        assertEquals(true, source.phaseIncrement.isFinite() && source.phaseIncrement > 0.0)
    }
}
