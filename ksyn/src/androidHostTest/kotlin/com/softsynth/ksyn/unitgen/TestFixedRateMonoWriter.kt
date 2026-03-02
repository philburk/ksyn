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

class TestFixedRateMonoWriter : NonRealTimeTestCase() {

    @Test
    fun testReadThenWrite() {
        val numFrames = 256

        // Create a source sample filled with predictable random values in [-1, 1].
        val random = PseudoRandom(12345)
        val sourceData = FloatArray(numFrames) { (random.nextRandomDouble() * 2.0 - 1.0).toFloat() }
        val sourceSample = FloatSample(sourceData)
        sourceSample.frameRate = synthesisEngine.frameRate.toDouble()

        // Create an empty destination sample with the same size.
        val destSample = FloatSample(numFrames)
        destSample.frameRate = synthesisEngine.frameRate.toDouble()

        // Set up reader and writer.
        val reader = FixedRateMonoReader()
        val writer = FixedRateMonoWriter()

        synthesisEngine.add(reader)
        synthesisEngine.add(writer)

        // Connect reader output to writer input.
        reader.output.connect(writer.input)

        // Queue the source sample for reading and the dest sample for writing.
        reader.dataQueue.queue(sourceSample)
        writer.dataQueue.queue(destSample)

        synthesisEngine.start()
        writer.start()

        // Wait long enough for all 256 frames to be processed.
        // At 44100 Hz: 256 frames ~ 5.8ms, so wait at least 50ms to be safe.
        val durationSeconds = (numFrames.toDouble() / synthesisEngine.frameRate) + 0.050
        checkSleepUntil(durationSeconds)

        synthesisEngine.stop()

        // Compare the source and destination buffers.
        for (i in 0 until numFrames) {
            assertEquals(
                sourceSample.buffer[i],
                destSample.buffer[i],
                "Sample mismatch at frame $i"
            )
        }
    }
}
