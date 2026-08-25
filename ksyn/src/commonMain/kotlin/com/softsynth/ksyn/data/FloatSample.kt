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
 * Store multi-channel floating point audio data in an interleaved buffer. The values are stored as
 * 32-bit floats.
 *
 * @author Phil Burk (C) 2010 Mobileer Inc
 */
open class FloatSample() : AudioSampleData(), Function {
    lateinit var buffer: FloatArray
        private set

    /** Constructor for mono samples. */
    constructor(numFrames: Int) : this() {
        allocate(numFrames, 1)
    }

    /** Constructor for mono samples with data. */
    constructor(data: FloatArray) : this() {
        allocate(data.size, 1)
        write(data)
    }

    /** Constructor for multi-channel samples with data. */
    constructor(data: FloatArray, channelsPerFrame: Int) : this() {
        allocate(data.size / channelsPerFrame, channelsPerFrame)
        write(data)
    }

    /**
     * Create a silent sample with enough memory to hold the audio data.
     */
    constructor(numFrames: Int, channelsPerFrame: Int) : this() {
        allocate(numFrames, channelsPerFrame)
    }

    override fun allocate(numFrames: Int, channelsPerFrame: Int) {
        buffer = FloatArray(numFrames * channelsPerFrame)
        this.maxFrames = numFrames
        this.setNumFrames(numFrames)
        this.setChannelsPerFrame(channelsPerFrame)
    }

    /**
     * Note that in a stereo sample, a frame has two values.
     *
     * @param startFrame index of frame in the sample
     * @param data data to be written
     * @param startIndex index of first value in array
     * @param numFrames
     */
    fun write(startFrame: Int, data: FloatArray, startIndex: Int, numFrames: Int) {
        val numSamplesToWrite = numFrames * channelsPerFrame
        val firstSampleIndexToWrite = startFrame * channelsPerFrame
        data.copyInto(buffer, firstSampleIndexToWrite, startIndex, startIndex + numSamplesToWrite)
    }

    /**
     * Note that in a stereo sample, a frame has two values.
     *
     * @param startFrame index of frame in the sample
     * @param data array to receive the data from the sample
     * @param startIndex index of first location in array to start filling
     * @param numFrames
     */
    fun read(startFrame: Int, data: FloatArray, startIndex: Int, numFrames: Int) {
        val numSamplesToRead = numFrames * channelsPerFrame
        val firstSampleIndexToRead = startFrame * channelsPerFrame
        buffer.copyInto(data, startIndex, firstSampleIndexToRead, firstSampleIndexToRead + numSamplesToRead)
    }

    /**
     * Write the entire array to the sample. The sample data must have already been allocated with
     * enough room to contain the data.
     *
     * @param data
     */
    fun write(data: FloatArray) {
        write(0, data, 0, data.size / channelsPerFrame)
    }

    fun read(data: FloatArray) {
        read(0, data, 0, data.size / channelsPerFrame)
    }

    override fun readSample(index: Int): AudioSample {
        return buffer[index]
    }

    override fun writeSample(index: Int, value: AudioSample) {
        buffer[index] = value
    }

    /**
     * Interpolate between two adjacent samples.
     * Note that this will only work for mono, single channel samples.
     */
    fun interpolate(fractionalIndex: AudioSample): AudioSample {
        val index = fractionalIndex.toInt()
        val phase = (fractionalIndex - index).toFloat()
        val source = buffer[index]
        val target = buffer[index + 1]
        return ((target - source) * phase + source)
    }

    override fun evaluate(input: Double): Double {
        var normalizedInput = (input + 1.0) * 0.5
        if (normalizedInput < 0.0) normalizedInput = 0.0
        else if (normalizedInput > 1.0) normalizedInput = 1.0
        val fractionalIndex = (numFrames - 1.01) * normalizedInput
        return interpolate(fractionalIndex.toFloat()).toDouble()
    }

    /**
     * If this sample is multi-channel, returns a new mono FloatSample with channels mixed down (averaged).
     * If already mono, returns this sample.
     */
    fun toMono(): FloatSample {
        if (channelsPerFrame == 1) return this
        val mono = FloatSample(numFrames).apply {
            frameRate = this@FloatSample.frameRate
            sustainBegin = this@FloatSample.sustainBegin
            sustainEnd = this@FloatSample.sustainEnd
            releaseBegin = this@FloatSample.releaseBegin
            releaseEnd = this@FloatSample.releaseEnd
        }
        val ch = channelsPerFrame
        for (i in 0 until numFrames) {
            var sum = 0.0f
            for (c in 0 until ch) {
                sum += readSample(i * ch + c)
            }
            mono.writeSample(i, sum / ch)
        }
        return mono
    }
}
