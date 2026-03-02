/*
 * Copyright 2024 Phil Burk, Mobileer Inc
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

import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.util.PseudoRandom
import kotlin.test.Test
import kotlin.test.assertEquals

class TestFixedRateStereoWriter : NonRealTimeTestCase() {

    @Test
    fun testReadThenWrite() {
        val numFrames = 256

        // Create a stereo source sample with predictable random values in [-1, 1].
        // Stereo means 2 channels interleaved, so buffer size = numFrames * 2.
        val random = PseudoRandom(98765)
        val sourceData = FloatArray(numFrames * 2) { (random.nextRandomDouble() * 2.0 - 1.0).toFloat() }
        val sourceSample = FloatSample(sourceData, 2)
        sourceSample.frameRate = synthesisEngine.frameRate.toDouble()

        // Create an empty stereo destination sample.
        val destSample = FloatSample(numFrames, 2)
        destSample.frameRate = synthesisEngine.frameRate.toDouble()

        // Set up stereo reader and writer.
        val reader = FixedRateStereoReader()
        val writer = FixedRateStereoWriter()

        synthesisEngine.add(reader)
        synthesisEngine.add(writer)

        // Connect reader output channels to writer input channels.
        reader.output.connect(0, writer.input, 0)
        reader.output.connect(1, writer.input, 1)

        // Queue the source sample for reading and the dest sample for writing.
        reader.dataQueue.queue(sourceSample)
        writer.dataQueue.queue(destSample)

        synthesisEngine.start()
        writer.start()

        // Wait long enough for all 256 frames to be processed.
        val durationSeconds = (numFrames.toDouble() / synthesisEngine.frameRate) + 0.050
        checkSleepUntil(durationSeconds)

        synthesisEngine.stop()

        // Compare both channels of source and destination buffers.
        // Stereo samples are interleaved: [L0, R0, L1, R1, ...]
        for (i in 0 until numFrames * 2) {
            assertEquals(
                sourceSample.buffer[i],
                destSample.buffer[i],
                "Sample mismatch at interleaved index $i"
            )
        }
    }
}
