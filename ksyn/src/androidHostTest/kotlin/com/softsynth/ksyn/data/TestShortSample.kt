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

package com.softsynth.ksyn.data

import kotlin.test.Test
import kotlin.test.assertEquals

class TestShortSample {

    @Test
    fun testBytes() {
        val bar = byteArrayOf(18, -3)
        val s = ((bar[0].toInt() shl 8) or (bar[1].toInt() and 0xFF)).toShort()
        assertEquals(0x12FD.toShort(), s, "A")
    }

    @Test
    fun testReadWrite() {
        val data = shortArrayOf(123, 456, -789, 111, 20000, -32768, 32767, 0, 9876)
        val sample = ShortSample(data.size, 1)
        assertEquals(data.size, sample.numFrames, "Sample numFrames")

        // Write and read entire sample.
        sample.write(data)
        val buffer = ShortArray(data.size)
        sample.read(buffer)

        for (i in data.indices) {
            assertEquals(data[i], buffer[i], "read = write")
        }

        // Write and read part of an array.
        val partial = shortArrayOf(333, 444, 555, 666, 777)

        sample.write(2, partial, 1, 3)
        sample.read(1, buffer, 1, 5)

        for (i in data.indices) {
            if (i in 2..4) {
                assertEquals(partial[i - 1], buffer[i], "partial")
            } else {
                assertEquals(data[i], buffer[i], "read = write")
            }
        }
    }
}
