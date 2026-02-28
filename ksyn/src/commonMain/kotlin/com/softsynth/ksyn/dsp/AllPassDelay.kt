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

class AllPassDelay(length: Int, private var mCoefficient: Float) {
    private val mBuffer: FloatArray = FloatArray(length)
    private var mCursor: Int = 0

    fun process(input: Float): Float {
        val z = mBuffer[mCursor]
        val x = input - (z * mCoefficient)
        mBuffer[mCursor] = x
        mCursor++
        if (mCursor >= mBuffer.size) mCursor = 0
        return z + (x * mCoefficient)
    }
}
