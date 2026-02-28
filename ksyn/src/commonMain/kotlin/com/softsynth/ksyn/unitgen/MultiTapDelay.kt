/*
 * Copyright 2023 Phil Burk
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
import com.softsynth.ksyn.dsp.SimpleDelay
import com.softsynth.ksyn.ports.UnitInputPort
import kotlin.math.max
import kotlin.math.min

/**
 * Delay with multiple read positions and associated gains.
 */
class MultiTapDelay(
    private val mPositions: IntArray,
    private val mGains: FloatArray,
    maxPreDelayFrames: Int
) : UnitFilter() {

    /** Pre-delay time in milliseconds. */
    val preDelayMillis: UnitInputPort
    
    private val mMaxPreDelayFrames: Int = max(1, maxPreDelayFrames)
    private val mPreDelay: SimpleDelay = SimpleDelay(mMaxPreDelayFrames)
    private val mDelay: SimpleDelay

    init {
        preDelayMillis = UnitInputPort("PreDelayMillis")
        
        // TODO handle unknown frame rate better
        val maxMillis = maxPreDelayFrames * 1000.0 / 44100.0 
        preDelayMillis.setup(0.0, min(10.0, maxMillis), maxMillis)
        addPort(preDelayMillis)

        var maxPosition = 0
        for (position in mPositions) {
            maxPosition = max(maxPosition, position)
        }
        mDelay = SimpleDelay(maxPosition)
    }

    override fun generate() {
        val inputs = input.getValues()
        val outputs = output.getValues()

        val preDelayMS = preDelayMillis.getValues()[0].toDouble()
        val frameRateLocal = synthesisEngine?.frameRate ?: 44100.0f
        var preDelayFrames = (preDelayMS * 0.001 * frameRateLocal).toInt()
        preDelayFrames = max(1, min(mMaxPreDelayFrames, preDelayFrames))

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            mPreDelay.write(inputs[i])
            mDelay.write(mPreDelay.read(preDelayFrames))
            mPreDelay.advance()
            
            var sum = 0.0f
            for (tap in mPositions.indices) {
                sum += mDelay.read(mPositions[tap]) * mGains[tap]
            }
            mDelay.advance()
            outputs[i] = sum // mix taps
        }
    }
}
