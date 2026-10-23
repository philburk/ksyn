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

/**
 * Tests for the sample and delay line grain sources, and the GrainFarms that use them.
 */
class TestSampleAndDelayGrains {

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

        // Delay 0.495s (49.5 frames behind the newest) -> halfway between 50 and 51
        source.setDelay(0.495)
        assertEquals(50.5f, source.next(), 1e-4f)
    }

    /**
     * Reads must be correct after the cursor has wrapped around, including fractional reads that
     * cross the end of the buffer and use the guard frame.
     */
    @Test
    fun testGrainDelayBufferWrapsAround() {
        val numFrames = 101
        val delayBuffer = GrainDelayBuffer(numFrames)
        val delays = doubleArrayOf(1.0, 1.5, 2.0, 2.5, 50.25, 99.5, 100.0)
        // Write a ramp so the value at a delay is easy to predict, and check after every write
        // so the cursor passes every position, including the wrap.
        for (written in 1..350) {
            delayBuffer.write((written - 1).toFloat())
            if (written < numFrames) continue // the buffer is not full yet
            for (delay in delays) {
                // The newest value, written - 1, is at a delay of 1.0.
                val expected = (written - delay).toFloat()
                assertEquals(expected, delayBuffer.readInterpolated(delay), 1e-3f,
                    "after $written writes at delay $delay")
            }
        }
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

    /** Start many grains and return the range of their starting frame indices. */
    private fun startingFrames(position: Double, positionRange: Double): ClosedFloatingPointRange<Double> {
        val numFrames = 1001
        val sample = FloatSample(FloatArray(numFrames) { it.toFloat() })
        val farm = SampleGrains()
        farm.setSample(sample)
        farm.position.set(position)
        farm.positionRange.set(positionRange)
        val source = GrainSourceSample(sample)
        val grain = Grain(source, RaisedCosineEnvelope())
        var lowest = Double.MAX_VALUE
        var highest = -Double.MAX_VALUE
        repeat(2000) {
            farm.setupGrain(grain, 0)
            lowest = minOf(lowest, source.phase)
            highest = maxOf(highest, source.phase)
        }
        return lowest..highest
    }

    @Test
    fun testSampleGrainsPositionRange() {
        // Position 0.0 is the middle of the 1001 frame sample, frame 500.
        val fixed = startingFrames(0.0, 0.0)
        assertEquals(500.0, fixed.start, 1e-9, "no spread without a position range")
        assertEquals(500.0, fixed.endInclusive, 1e-9, "no spread without a position range")

        // A range of 0.2 is +/- 0.2 in position, which is +/- 100 frames.
        val spread = startingFrames(0.0, 0.2)
        assertTrue(spread.start >= 400.0 && spread.endInclusive <= 600.0,
            "grains should start within +/- the position range, got $spread")
        assertTrue(spread.start < 420.0 && spread.endInclusive > 580.0,
            "grains should spread across most of the position range, got $spread")

        // Near the end the spread is clamped to the sample.
        val clamped = startingFrames(0.95, 0.2)
        assertTrue(clamped.endInclusive <= 1000.0, "grains should not start past the end, got $clamped")
        assertEquals(1000.0, clamped.endInclusive, 1e-9, "some grains should be clamped to the end")
    }

    @Test
    fun testDelayGrainsProducesOutputFromInput() {
        val synth = KSyn.createSynthesizer()
        val farm = DelayGrains()
        farm.allocateDelay(4410) // 0.1 seconds at 44100 Hz
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

    /**
     * Step the input from zero to a constant. At a rate of 1.0 a grain started with a delay of
     * D seconds keeps reading audio from D seconds ago, so the output must stay silent for D
     * seconds after the step, and then the grains should pick up the input.
     */
    @Test
    fun testDelayGrainsDelaysTheInput() {
        val frameRate = 44100 // used when the farm is not in a synth
        val delaySeconds = 0.1
        val farm = DelayGrains()
        farm.allocateDelay(frameRate / 2) // 0.5 seconds
        farm.allocate(8)
        farm.density.set(1.0) // grains back to back
        farm.duration.set(0.02)
        farm.delay.set(delaySeconds)
        farm.delayRange.set(0.0)
        farm.rateRange.set(0.0)

        val output = ArrayList<Float>()
        fun run(blocks: Int) = repeat(blocks) {
            farm.generate()
            for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) output.add(farm.output.getValues()[i])
        }

        run(200) // let grains start while the input is silent
        val stepFrame = output.size
        farm.input.set(0.5)
        run(1000)

        val firstSound = output.indexOfFirst { it != 0.0f }
        assertTrue(firstSound >= 0, "grains never picked up the input")
        val delayFrames = (delaySeconds * frameRate).toInt()
        val latency = firstSound - stepFrame
        assertTrue(latency >= delayFrames - 1,
            "output started $latency frames after the input, before the $delayFrames frame delay")
        // A new grain starts within one grain duration of the delay.
        assertTrue(latency <= delayFrames + (0.03 * frameRate).toInt(),
            "output started $latency frames after the input, long after the $delayFrames frame delay")
    }

    /** allocateDelay() takes the longest delay in frames, like InterpolatingDelay.allocate(). */
    @Test
    fun testDelayGrainsAllocateDelay() {
        val frameRate = 44100 // used when the farm is not in a synth
        val numFrames = 66150 // 1.5 seconds
        val farm = DelayGrains()
        farm.allocateDelay(numFrames)
        assertEquals(1.5, farm.delay.maximum.toDouble(), 1e-6)
        assertEquals(0.75, farm.delayRange.maximum.toDouble(), 1e-6)

        // The longest delay just fits in the buffer.
        val source = GrainSourceDelayLine(farm.delayBuffer)
        source.frameRate = frameRate.toFloat()
        source.setDelay(1.5)
        assertEquals(numFrames + 1.0, source.delayFrames, 1e-6, "the longest delay was clamped")

        // It can be allocated again with a different size.
        farm.allocateDelay(3 * frameRate)
        assertEquals(3.0, farm.delay.maximum.toDouble(), 1e-6)
    }

    /** The port limits in seconds use the rate the synth runs at. */
    @Test
    fun testDelayGrainsAllocateDelayUsesTheSynthFrameRate() {
        val synth = KSyn.createSynthesizer()
        val farm = DelayGrains()
        synth.add(farm)
        synth.start(48000, 0, 2)
        farm.allocateDelay(48000)
        assertEquals(1.0, farm.delay.maximum.toDouble(), 1e-6)
        synth.stop()
    }

    /** Like InterpolatingDelay, the farm is silent until its delay line is allocated. */
    @Test
    fun testDelayGrainsIsSilentUntilAllocated() {
        val farm = DelayGrains()
        farm.allocate(8)
        farm.density.set(1.0)
        farm.input.set(0.5)
        var peak = 0.0f
        repeat(200) {
            farm.generate()
            for (v in farm.output.getValues()) peak = maxOf(peak, abs(v))
        }
        assertEquals(0.0f, peak, "no delay line, so there is nothing to play")
    }
}
