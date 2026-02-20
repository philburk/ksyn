/*
 * Copyright 2009 Phil Burk, Mobileer Inc
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

/**
 * Simple non-interpolating delay. The delay line must be allocated by calling allocate(n).
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 * @version 016
 * @see InterpolatingDelay
 */
class Delay : UnitFilter() {
    private var buffer: FloatArray? = null
    private var cursor: Int = 0
    private var numSamples: Int = 0

    /**
     * Allocate an internal array for the delay line.
     *
     * @param numSamples
     */
    fun allocate(numSamples: Int) {
        this.numSamples = numSamples
        buffer = FloatArray(numSamples)
    }

    override fun generate() {
        val inputs = input.getValues()
        val outputs = output.getValues()
        val myBuffer = buffer

        if (myBuffer != null && numSamples > 0) {
            for (i in 0..<Synthesizer.FRAMES_PER_BLOCK) {
                outputs[i] = myBuffer[cursor]
                myBuffer[cursor] = inputs[i]
                cursor += 1
                if (cursor >= numSamples) {
                    cursor = 0
                }
            }
        }
    }
}
