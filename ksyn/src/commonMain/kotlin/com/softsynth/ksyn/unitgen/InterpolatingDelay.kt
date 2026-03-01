/*
 * Copyright 1997 Phil Burk, Mobileer Inc
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
import com.softsynth.ksyn.ports.UnitInputPort

/**
 * InterpolatingDelay
 * <P>
 * InterpolatingDelay provides a variable time delay with an input and output. The internal data
 * format is 32-bit floating point. The amount of delay can be varied from 0.0 to a time in seconds
 * corresponding to the numFrames allocated. The fractional delay values are calculated by linearly
 * interpolating between adjacent values in the delay line.
 * <P>
 * This unit can be used to implement time varying delay effects such as a flanger or a chorus. It
 * can also be used to implement physical models of acoustic instruments, or other tunable delay
 * based resonant systems.
 * <P>
 * 
 * @author (C) 1997-2011 Phil Burk, Mobileer Inc
 * @see Delay
 */

class InterpolatingDelay : UnitFilter() {
    /**
     * Delay time in seconds. This value will converted to frames and clipped between zero and the
     * numFrames value passed to allocate(). The minimum and default delay time is 0.0.
     */
    val delay: UnitInputPort
    
    private var buffer: FloatArray = FloatArray(0)
    private var cursor: Int = 0
    private var numFrames: Int = 0

    init {
        delay = UnitInputPort("Delay")
        addPort(delay)
    }

    /**
     * Allocate memory for the delay buffer. For a 2 second delay at 44100 Hz sample rate you will
     * need at least 88200 samples.
     * 
     * @param numFrames size of the float array to hold the delayed samples
     */
    fun allocate(numFrames: Int) {
        this.numFrames = numFrames
        // Allocate extra frame for guard point to speed up interpolation.
        buffer = FloatArray(numFrames + 1)
        cursor = 0
    }

    override fun generate() {
        if (numFrames == 0) return // not allocated
        
        val inputs = input.getValues()
        val outputs = output.getValues()
        val delays = delay.getValues()
        
        val frameRateLocal = (synthesisEngine?.frameRate ?: 44100.0).toFloat()
        
        var cur = cursor
        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            // This should be at the beginning of the loop
            // because the guard point should == buffer[0].
            if (cur == numFrames) {
                // Write guard point! Must allocate one extra sample.
                buffer[numFrames] = inputs[i]
                cur = 0
            }

            buffer[cur] = inputs[i]

            /* Convert delay time to a clipped frame offset. */
            var delayFrames = delays[i] * frameRateLocal

            // Clip to zero delay.
            if (delayFrames <= 0.0f) {
                outputs[i] = buffer[cur]
            } else {
                // Clip to maximum delay.
                if (delayFrames >= numFrames.toFloat()) {
                    delayFrames = (numFrames - 1).toFloat()
                }

                // Calculate fractional index into delay buffer.
                var readIndex = cur - delayFrames
                if (readIndex < 0.0f) {
                    readIndex += numFrames.toFloat()
                }
                
                // setup for interpolation.
                // We know readIndex is > 0 so we do not need to call floor().
                val iReadIndex = readIndex.toInt()
                val frac = readIndex - iReadIndex

                // Get adjacent values relying on guard point to prevent overflow.
                val val0 = buffer[iReadIndex]
                val val1 = buffer[iReadIndex + 1]

                // Interpolate new value.
                outputs[i] = val0 + (frac * (val1 - val0))
            }

            cur += 1
        }
        cursor = cur
    }
}
