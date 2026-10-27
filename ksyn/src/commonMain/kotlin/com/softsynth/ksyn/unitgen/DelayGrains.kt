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

/**
 * A [GrainFarm] that records incoming audio from its [input] port into an internal circular
 * delay line and uses that delay line as the source material for its grains.
 *
 * Call [allocateDelay] to set the size of the delay line, and [allocate] to create the grains,
 * before starting the synth. The output is silent until the delay line is allocated.
 *
 * <pre><code>
 *    synth.add(delayGrains = DelayGrains())
 *    delayGrains.allocateDelay(2 * frameRate) // up to 2 seconds of delay
 *    delayGrains.allocate(NUM_GRAINS)
 * </code></pre>
 *
 * [delay] and [delayRange] are specified in seconds, from `0.0` to the length of the delay line.
 * Each grain starts at `delay + delayRange * random(-1.0..+1.0)`, clamped to the valid delay range.
 */
class DelayGrains : GrainFarm(), UnitSink {

    override var input = UnitInputPort("Input")

    /** Delay time in seconds before reading a grain (`0.0` = newest audio). */
    val delay = UnitInputPort("Delay", 0.25)

    /** Additive random range in seconds around [delay] when starting each grain. */
    val delayRange = UnitInputPort("DelayRange", 0.1)

    /** Null until [allocateDelay] is called. */
    var delayBuffer: GrainDelayBuffer? = null
        private set

    init {
        addPort(input)
        addPort(delay)
        addPort(delayRange)
    }

    /**
     * Allocate memory for the delay line. For a 2 second delay at 44100 Hz you will need
     * 88200 frames. This sets the maximum of the [delay] and [delayRange] ports using the
     * current frame rate, or 44100 if the unit has not been added to a synth.
     *
     * @param numFrames longest delay in frames
     */
    fun allocateDelay(numFrames: Int) {
        // A delay of zero reads the newest frame, one frame behind the cursor, and the
        // longest delay is one frame less than the buffer, so add two frames.
        // Each grain picks up the new buffer in setupGrain() when it next starts.
        delayBuffer = GrainDelayBuffer(numFrames + 2)

        val frameRateLocal = (synthesisEngine?.frameRate ?: 44100).toDouble()
        val maxDelaySeconds = numFrames / frameRateLocal
        val defaultDelay = (0.25).coerceAtMost(maxDelaySeconds * 0.5)
        val defaultRange = (0.1).coerceAtMost(maxDelaySeconds * 0.25)
        delay.setup(0.0, defaultDelay, maxDelaySeconds)
        delayRange.setup(0.0, defaultRange, maxDelaySeconds * 0.5)
        duration.setup(0.001, 0.020, maxDelaySeconds * 0.5)
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
            val randomizedDelay = (baseDelay + range * randomizer.nextRandomTriangle()).coerceAtLeast(0.0)
            delaySource.setDelay(randomizedDelay)
        }
        super.setupGrain(grain, i)
    }

    override fun onFrame(frameIndex: Int) {
        delayBuffer?.write(input.getValues()[frameIndex])
    }

    fun clear() {
        delayBuffer?.clear()
    }
}
