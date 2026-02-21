/*
 * Copyright 2010 Phil Burk, Mobileer Inc
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

package com.softsynth.ksyn.data

/**
 * Evaluate a Function by interpolating between the values in a table. This can be used for
 * wavetable lookup or waveshaping.
 *
 * @author Phil Burk (C) 2010 Mobileer Inc
 */
class DoubleTable : Function {
    lateinit var table: DoubleArray
        private set

    constructor(numFrames: Int) {
        allocate(numFrames)
    }

    constructor(data: DoubleArray) {
        allocate(data.size)
        write(data)
    }

    constructor(shortSample: ShortSample) {
        if (shortSample.channelsPerFrame != 1) {
            throw RuntimeException("DoubleTable can only be built from mono samples.")
        }
        val buffer = ShortArray(256)
        var framesLeft = shortSample.numFrames
        allocate(framesLeft)
        var cursor = 0
        while (framesLeft > 0) {
            var numTransfer = framesLeft
            if (numTransfer > buffer.size) {
                numTransfer = buffer.size
            }
            shortSample.read(cursor, buffer, 0, numTransfer)
            write(cursor, buffer, 0, numTransfer)
            cursor += numTransfer
            framesLeft -= numTransfer
        }
    }

    fun allocate(numFrames: Int) {
        table = DoubleArray(numFrames)
    }

    fun length(): Int {
        return table.size
    }

    fun write(data: DoubleArray) {
        write(0, data, 0, data.size)
    }

    fun write(startFrame: Int, data: ShortArray, startIndex: Int, numFrames: Int) {
        for (i in 0 until numFrames) {
            table[startFrame + i] = data[startIndex + i] * (1.0 / 32768.0)
        }
    }

    fun write(startFrame: Int, data: DoubleArray, startIndex: Int, numFrames: Int) {
        for (i in 0 until numFrames) {
            table[startFrame + i] = data[startIndex + i]
        }
    }

    /**
     * Treat the double array as a lookup table with a domain of -1.0 to 1.0. If the input is out of
     * range then the output will clip to the end values.
     *
     * @param input
     * @return interpolated value from table
     */
    override fun evaluate(input: Double): Double {
        val interp: Double
        if (input < -1.0) {
            interp = table[0]
        } else if (input < 1.0) {
            val fractionalIndex = (table.size - 1) * (input - (-1.0)) / 2.0
            // We don't need floor() because fractionalIndex >= 0.0
            val index = fractionalIndex.toInt()
            val fraction = fractionalIndex - index

            val s1 = table[index]
            val s2 = table[index + 1]
            interp = ((s2 - s1) * fraction) + s1
        } else {
            interp = table[table.size - 1]
        }
        return interp
    }
}
