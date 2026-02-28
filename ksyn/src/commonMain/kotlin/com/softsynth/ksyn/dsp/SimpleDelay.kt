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
 * Delay line based on a circular buffer.
 */
class SimpleDelay(length: Int) {
    private val mBuffer: FloatArray = FloatArray(length)
    private var mCursor: Int = 0

    /**
     * Read a value from the delay line.
     * @param position positive delay in frames
     * @return delayed value
     */
    fun read(position: Int): Float {
        var index = mCursor - position
        if (index < 0) {
            index += mBuffer.size
        }
        return mBuffer[index]
    }

    /**
     * Write a new value to the head of the delay line.
     * This does not advance the cursor.
     * @param input sample value
     */
    fun write(input: Float) {
        mBuffer[mCursor] = input
    }

    /**
     * Advance the cursor position. Wrap around in a circle.
     */
    fun advance() {
        mCursor++
        if (mCursor >= mBuffer.size) mCursor = 0
    }

    /**
     * Add a new value and return the oldest value in the delay line.
     * @param input sample value
     * @return oldest value
     */
    fun process(input: Float): Float {
        val output = mBuffer[mCursor]
        write(input)
        advance()
        return output
    }
}
