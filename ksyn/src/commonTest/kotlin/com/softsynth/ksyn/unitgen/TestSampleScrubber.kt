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

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.data.FloatSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestSampleScrubber {

    @Test
    fun testSampleScrubberDoublePrecisionIndexing() {
        // Create a large 1,000,001-frame sine-wave sample with a short period (4 frames)
        // so adjacent samples differ by 1.0f.
        // Near the end of this array (index ~950,000), a 32-bit float index has an ULP step size
        // of 0.0625 frames (losing the lower 19 bits of fractional precision).
        // With 64-bit Double fractional indexing, tiny input variations (e.g. 1e-9 in [-1, 1],
        // or 0.0005 frames) still interpolate smoothly without quantization staircase steps.
        val numFrames = 1_000_001
        val data = FloatArray(numFrames) { i ->
            when (i and 3) {
                0 -> 0.0f
                1 -> 1.0f
                2 -> 0.0f
                else -> -1.0f
            }
        }
        val sample = FloatSample(data)

        // Set ports BEFORE adding to a started engine (or let engine process commands)
        // because UnitPort.set() queues a command when synthesisEngine is attached.
        val scrubber = SampleScrubber(sample)
        scrubber.position.set(0.9)
        scrubber.time.set(0.00001)
        scrubber.input.set(0.0)

        // Let the internal linear ramp settle to 0.9
        repeat(10) { scrubber.generate() }
        val baseOut = scrubber.output.getValues()[Synthesizer.FRAMES_PER_BLOCK - 1]

        // Now apply a tiny input delta (1e-8 in normalized [-1, 1] space, which is 0.005 frames,
        // well below the 0.0625-frame ULP of a 32-bit float index at 950,000!)
        scrubber.input.set(1.0e-8)
        scrubber.generate()
        val shiftedOut = scrubber.output.getValues()[0]

        assertTrue(shiftedOut > baseOut, "SampleScrubber should resolve sub-ULP input offsets even in a 1M frame sample")
    }

    /**
     * A change of position should ramp linearly over the time port, starting where it was,
     * rather than jumping.
     */
    @Test
    fun testPositionRampsOverTime() {
        // A ramp sample, so the output is the frame index being read.
        val numFrames = 1001
        val sample = FloatSample(FloatArray(numFrames) { it.toFloat() })
        val scrubber = SampleScrubber(sample)
        val rampSeconds = 0.01 // 441 frames at the default 44100
        val rampFrames = rampSeconds * 44100.0
        scrubber.time.set(rampSeconds)
        scrubber.amplitude.set(1.0) // the default is 0.8
        scrubber.input.set(0.0)
        scrubber.position.set(0.0) // the middle, frame 500

        val output = ArrayList<Float>()
        fun run(blocks: Int) = repeat(blocks) {
            scrubber.generate()
            for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) output.add(scrubber.output.getValues()[i])
        }

        run(4)
        assertEquals(500.0f, output.last(), 1e-3f, "should start in the middle")

        val start = output.size
        scrubber.position.set(1.0) // the end, frame 1000
        run(80)

        assertEquals(500.0f, output[start], 1e-3f, "should not jump when the position changes")
        // Halfway through the ramp it should be halfway to the end.
        val halfway = start + (rampFrames / 2).toInt()
        assertEquals(750.0f, output[halfway], 2.0f, "should be halfway after half of the ramp time")
        // Each frame should move by the same amount.
        val step = 500.0 / rampFrames
        for (i in start + 1 until start + rampFrames.toInt()) {
            assertEquals(step, (output[i] - output[i - 1]).toDouble(), 1e-3, "uneven step at frame ${i - start}")
        }
        assertEquals(1000.0f, output.last(), 1e-3f, "should reach the end after the ramp time")
    }
}
