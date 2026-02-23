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

class TestGrainFarm : NonRealTimeTestCase() {

    @Test
    fun testAllocationAndGenerate() {
        val grainFarm = GrainFarm()
        synthesisEngine.add(grainFarm)

        grainFarm.allocate(8)

        grainFarm.duration.set(0.05)
        grainFarm.density.set(0.5) // Lots of gaps
        grainFarm.rate.set(440.0) // A4
        grainFarm.amplitude.set(0.8)

        synthesisEngine.start()
        grainFarm.start()

        checkSleepUntil(0.2)
        
        // As long as it generates without throwing exceptions and finishes gracefully, 
        // the fundamental architecture of the stochastic scheduler is validated.

        var maxAmp = 0.0f
        for (i in 0..100) {
            val v = grainFarm.output.value
            if (v > maxAmp) {
                maxAmp = v
            }
            checkSleepUntil(synthesisEngine.currentTime + 0.005)
        }

        assertTrue(maxAmp > 0.0f, "GrainFarm should produce some non-zero audio output")

        synthesisEngine.stop()
    }
}
