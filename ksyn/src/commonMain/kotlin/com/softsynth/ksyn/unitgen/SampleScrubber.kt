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
import com.softsynth.ksyn.ports.UnitInputPort

/**
 * Unit generator that scrubs through a [FloatSample] by combining a smoothed [position]
 * with an audio-rate [input] signal.
 *
 * The audio-rate [input] signal (which may have a very small amplitude, e.g. 0.0001 to 0.1)
 * is added to a smoothed [position] (-1.0 to +1.0) using 64-bit [Double] precision to prevent
 * floating-point quantization when indexing into large samples.
 *
 * Changes to [position] are smoothed internally using a 64-bit linear ramp controlled by [time]
 * (in seconds) to avoid zipper noise and aliasing.
 */
class SampleScrubber(
    sample: FloatSample? = null
) : UnitFilter() {

    /** Center position in the sample, from -1.0 (start) to +1.0 (end). */
    val position = UnitInputPort("Position", 0.0)

    /** Time in seconds for [position] changes to linearly ramp to their target. */
    val time = UnitInputPort("Time", 0.05)

    /** Output amplitude scaler. */
    val amplitude = UnitInputPort("Amplitude", 1.0)

    private var sample: FloatSample? = sample

    // Internal 64-bit linear ramp state for smoothing position changes.
    private var currentPosition: Double = 0.0
    private var sourcePosition: Double = 0.0
    private var targetPosition: Double = 0.0
    private var rampPhase: Double = 1.0
    private var timeHeld: Double = -1.0
    private var rampRate: Double = 1.0

    init {
        addPort(position)
        addPort(time)
        addPort(amplitude)

        position.setup(-1.0, 0.0, 1.0)
        time.setup(0.001, 0.05, 1.0)
        amplitude.setup(0.0, 0.8, 1.0)
    }

    fun setSample(sample: FloatSample?) {
        this.sample = sample
    }

    fun getSample(): FloatSample? = sample

    override fun generate() {
        val outputs = output.getValues()
        val s = sample
        if (s == null || s.numFrames <= 0) {
            for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
                outputs[i] = 0.0f
            }
            return
        }

        val inputs = input.getValues()
        val amplitudes = amplitude.getValues()
        val targetPos = position.getValues()[0].toDouble()
        val currentTime = time.getValues()[0].toDouble()

        if (targetPos != targetPosition) {
            sourcePosition = currentPosition
            rampPhase = 0.0
            targetPosition = targetPos
        }

        if (currentTime != timeHeld) {
            rampRate = convertTimeToRate(currentTime)
            timeHeld = currentTime
        }

        val maxFrameIndex = (s.numFrames - 1).toDouble()

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            if (currentPosition != targetPosition) {
                if (rampPhase < 1.0) {
                    currentPosition = sourcePosition + rampPhase * (targetPosition - sourcePosition)
                    rampPhase += rampRate
                } else {
                    currentPosition = targetPosition
                }
            }

            // Add the smoothed position and the audio-rate input in 64-bit Double precision!
            val combinedPos = (currentPosition + inputs[i].toDouble()).coerceIn(-1.0, 1.0)
            val normalized = (combinedPos * 0.5) + 0.5
            val fractionalIndex = normalized * maxFrameIndex

            outputs[i] = s.interpolate(fractionalIndex) * amplitudes[i]
        }
    }
}
