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
import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.data.FloatSample
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestGranularAndWaveShaper {

    @Test
    fun testGrainSourceSamplePositionsAndRate() {
        val data = FloatArray(101) { i -> i.toFloat() }
        val sample = FloatSample(data)
        sample.frameRate = 44100.0

        val source = GrainSourceSample(sample)
        source.frameRate = 44100.0f
        source.setRate(1.0)

        // Position -1.0 -> start (index 0)
        source.setPosition(-1.0)
        assertEquals(0.0f, source.next(), 1e-5f)
        assertEquals(1.0f, source.next(), 1e-5f)

        // Position 0.0 -> center (index 50)
        source.setPosition(0.0)
        assertEquals(50.0f, source.next(), 1e-5f)
        assertEquals(51.0f, source.next(), 1e-5f)

        // Position +1.0 -> end (index 100), clamped on subsequent reads
        source.setPosition(1.0)
        assertEquals(100.0f, source.next(), 1e-5f)
        assertEquals(100.0f, source.next(), 1e-5f)
    }

    @Test
    fun testGrainSourceDelayLine() {
        val delayBuffer = GrainDelayBuffer(101)
        // Write 101 values: 0..100
        for (i in 0..100) {
            delayBuffer.write(i.toFloat())
        }

        val source = GrainSourceDelayLine(delayBuffer)
        source.frameRate = 100.0f // 100 frames per second so 0.01s = 1 frame
        source.setRate(1.0)

        // Delay 0.0s -> newest sample written (100.0f, 1 frame behind write cursor)
        source.setDelay(0.0)
        assertEquals(100.0f, source.next(), 1e-4f)

        // Delay 1.0s (100 frames) -> oldest sample in buffer (1.0f)
        source.setDelay(1.0)
        assertEquals(1.0f, source.next(), 1e-4f)

        // Delay 0.5s (49.5 frames behind newest) -> halfway (50.5f)
        source.setDelay(0.495)
        assertEquals(50.5f, source.next(), 1e-4f)
    }

    @Test
    fun testSampleGrainsProducesOutput() {
        val synth = KSyn.createSynthesizer()
        val farm = SampleGrains()
        val sample = FloatSample(FloatArray(1000) { 0.5f })
        farm.setSample(sample)
        farm.allocate(8)
        farm.density.set(0.8)
        farm.duration.set(0.02)
        farm.amplitude.set(0.8)
        farm.position.set(0.0)
        farm.positionRange.set(0.2)

        synth.add(farm)
        synth.start(44100, 0, 2)

        var peak = 0.0f
        repeat(200) {
            farm.generate()
            for (v in farm.output.getValues()) {
                peak = maxOf(peak, abs(v))
            }
        }
        assertTrue(peak > 0.01f, "SampleGrains should produce non-zero output")
        synth.stop()
    }

    @Test
    fun testDelayGrainsProducesOutputFromInput() {
        val synth = KSyn.createSynthesizer()
        val farm = DelayGrains(maxDelayFrames = 4410)
        farm.allocate(8)
        farm.density.set(0.8)
        farm.duration.set(0.02)
        farm.amplitude.set(0.8)
        farm.delay.set(0.05)
        farm.delayRange.set(0.01)
        farm.input.set(0.5) // Constant non-zero signal into the delay line

        synth.add(farm)
        synth.start(44100, 0, 2)

        var peak = 0.0f
        repeat(300) {
            farm.generate()
            for (v in farm.output.getValues()) {
                peak = maxOf(peak, abs(v))
            }
        }
        assertTrue(peak > 0.01f, "DelayGrains should granulate its input signal")
        synth.stop()
    }

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
}
