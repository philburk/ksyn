/*
 * Copyright 2022 Phil Burk
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

package com.softsynth.ksyn.dsp

/**
 * Allpass filter with a delay of [length] samples:
 *
 * <pre>
 * H(z) = (coefficient + z^-length) / (1 + coefficient * z^-length)
 * </pre>
 *
 * With a long length this is a Schroeder allpass used for reverb diffusion.
 * With length = 1 it is a first-order allpass that can be used for fractional delay tuning.
 * Its phase delay at DC is (1 - coefficient) / (1 + coefficient) samples.
 *
 * @param length delay in samples, at least 1
 * @param coefficient should be between -1.0 and 1.0 for stability
 */
class AllPassDelay(length: Int, coefficient: Float = 0.0f) {
    private val mBuffer: FloatArray = FloatArray(length)
    private var mCursor: Int = 0

    /** Can be changed at any time, for example to retune a fractional delay. */
    var coefficient: Float = coefficient

    val length: Int
        get() = mBuffer.size

    fun process(input: Float): Float {
        val z = mBuffer[mCursor]
        val x = input - (z * coefficient)
        mBuffer[mCursor] = x
        mCursor++
        if (mCursor >= mBuffer.size) mCursor = 0
        return z + (x * coefficient)
    }

    /** Set the internal state to zero. */
    fun clear() {
        mBuffer.fill(0.0f)
    }
}
