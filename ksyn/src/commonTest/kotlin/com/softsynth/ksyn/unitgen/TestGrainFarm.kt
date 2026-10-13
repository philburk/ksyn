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

import com.softsynth.ksyn.KSyn
import kotlin.test.Test
import kotlin.test.assertEquals

class TestGrainFarm {

    private fun makeGrains(count: Int) = Array(count) { Grain(GrainSourceSine(), RaisedCosineEnvelope()) }

    /**
     * Grains turn their duration into frames, and sample grains their pitch, using the frame
     * rate, so they must use the rate the synth actually runs at.
     */
    @Test
    fun testGrainsUseTheFrameRateTheSynthRunsAt() {
        // Allocate before the synth starts, as an app does when it builds a patch before audio
        // starts. Only 44100 is known then.
        val farm = GrainFarm()
        val grains = makeGrains(4)
        farm.setGrainArray(grains)
        farm.density.set(0.5)

        val synth = KSyn.createSynthesizer()
        synth.add(farm)
        synth.start(48000, 0, 2)

        repeat(2000) { farm.generate() }

        for (grain in grains) {
            assertEquals(48000.0f, grain.frameRate, "a grain is still timed at the old frame rate")
        }
    }

    private fun peakOutput(farm: GrainFarm, blocks: Int): Float {
        var peak = 0.0f
        repeat(blocks) {
            farm.generate()
            for (value in farm.output.getValues()) peak = maxOf(peak, kotlin.math.abs(value))
        }
        return peak
    }

    @Test
    fun testGrainsComeBackWhenDensityRisesFromZero() {
        // For example an envelope on the density port that falls to zero and starts again.
        val farm = GrainFarm()
        farm.allocate(8)
        farm.density.set(0.0)
        assertEquals(0.0f, peakOutput(farm, 2000), "no grains at density zero")

        farm.density.set(0.5)
        // Grains are 5 ms apart at most at this density, so they should be back within 0.1 s.
        val peak = peakOutput(farm, 600)
        kotlin.test.assertTrue(peak > 0.01f, "still silent after density rose from zero")
    }

    @Test
    fun testGrainsFollowAChangeOfFrameRate() {
        // Restarting audio on another device can change the frame rate under a running farm.
        val farm = GrainFarm()
        val grains = makeGrains(4)
        farm.density.set(0.5)

        val first = KSyn.createSynthesizer()
        first.add(farm)
        first.start(44100, 0, 2)
        farm.setGrainArray(grains)
        repeat(2000) { farm.generate() }
        first.remove(farm)
        farm.synthesisEngine = null

        val second = KSyn.createSynthesizer()
        second.add(farm)
        second.start(96000, 0, 2)
        repeat(2000) { farm.generate() }

        for (grain in grains) {
            assertEquals(96000.0f, grain.frameRate)
        }
    }
}
