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

import com.softsynth.ksyn.ports.UnitInputPort
import kotlin.math.ceil

/**
 * A [GrainFarm] that records incoming audio from its [input] port into an internal circular
 * delay line and uses that delay line as the source material for its grains.
 *
 * [delay] and [delayRange] are specified in seconds (`0.0` to [maxDelaySeconds]).
 * Each grain starts at `delay + delayRange * random(-1.0..+1.0)`, clamped to the valid delay range.
 *
 * The delay line is allocated so that [maxDelaySeconds] fits at frame rates up to
 * [MAX_FRAME_RATE], so the delays in seconds do not depend on the rate the synth runs at.
 *
 * @param maxDelaySeconds longest delay supported
 */
class DelayGrains(
    maxDelaySeconds: Double = DEFAULT_MAX_DELAY_SECONDS
) : GrainFarm(), UnitSink {

    override var input = UnitInputPort("Input")

    /** Delay time in seconds before reading a grain (`0.0` = newest audio). */
    val delay = UnitInputPort("Delay", 0.25)

    /** Additive random range in seconds around [delay] when starting each grain. */
    val delayRange = UnitInputPort("DelayRange", 0.1)

    /** Longest delay supported, in seconds. */
    var maxDelaySeconds: Double = maxDelaySeconds
        private set

    var delayBuffer: GrainDelayBuffer = GrainDelayBuffer(framesFor(maxDelaySeconds))
        private set

    init {
        addPort(input)
        addPort(delay)
        addPort(delayRange)
        updateDelayPortBounds()
    }

    private fun updateDelayPortBounds() {
        val defaultDelay = (0.25).coerceAtMost(maxDelaySeconds * 0.5)
        val defaultRange = (0.1).coerceAtMost(maxDelaySeconds * 0.25)
        delay.setup(0.0, defaultDelay, maxDelaySeconds)
        delayRange.setup(0.0, defaultRange, maxDelaySeconds * 0.5)
    }

    /**
     * Allocate [numGrains] grains and resize the internal delay buffer to hold [maxDelaySeconds].
     */
    fun allocate(numGrains: Int, maxDelaySeconds: Double) {
        this.maxDelaySeconds = maxDelaySeconds
        delayBuffer = GrainDelayBuffer(framesFor(maxDelaySeconds))
        updateDelayPortBounds()
        allocate(numGrains)
    }

    override fun allocate(numGrains: Int) {
        val grainArray = Array(numGrains) {
            Grain(GrainSourceDelayLine(delayBuffer), RaisedCosineEnvelope())
        }
        setGrainArray(grainArray)
    }

    override fun setupGrain(grain: Grain, i: Int) {
        val delaySource = grain.source as? GrainSourceDelayLine
        if (delaySource != null) {
            delaySource.setDelayBuffer(delayBuffer)
            val baseDelay = delay.getValues()[i].toDouble()
            val range = delayRange.getValues()[i].toDouble()
            val randomizedDelay = (baseDelay + range * randomizer.nextRandomDouble()).coerceAtLeast(0.0)
            delaySource.setDelay(randomizedDelay)
        }
        super.setupGrain(grain, i)
    }

    override fun onFrame(frameIndex: Int) {
        delayBuffer.write(input.getValues()[frameIndex])
    }

    fun clear() {
        delayBuffer.clear()
    }

    companion object {
        const val DEFAULT_MAX_DELAY_SECONDS = 2.0

        /** The delay line is allocated so that the maximum delay fits up to this frame rate. */
        const val MAX_FRAME_RATE = 96000.0

        /**
         * Frames needed to delay by [seconds] at [MAX_FRAME_RATE]. A delay of zero reads the
         * newest frame, 1 frame behind the cursor, and the longest delay is numFrames - 1.
         */
        private fun framesFor(seconds: Double): Int = ceil(seconds * MAX_FRAME_RATE).toInt() + 2
    }
}
