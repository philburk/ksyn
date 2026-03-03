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

import com.softsynth.ksyn.AudioBuffer

/**
 * Store an envelope as a series of line segments. Each line is described as a duration and a target
 * value. The envelope can be played using a VariableRateMonoReader. Here is an example that
 * generates an envelope that looks like a traditional ADSR envelope.
 * 
 * ```kotlin
 * 	// Create an amplitude envelope and fill it with data.
 * 	val ampData = doubleArrayOf(
 * 		0.02, 0.9, // duration,value pair 0, "attack"
 * 		0.10, 0.5, // pair 1, "decay"
 * 		0.50, 0.0  // pair 2, "release"
 * 	)
 * 	val ampEnvelope = SegmentedEnvelope( ampData )
 * 	
 * 	// Hang at end of decay segment to provide a "sustain" segment.
 * 	ampEnvelope.sustainBegin = 1
 * 	ampEnvelope.sustainEnd = 1
 * 	
 * 	// Play the envelope using queue so that it uses the sustain and release information.
 * 	val ampEnv = VariableRateMonoReader()
 * 	synth.add( ampEnv )
 * 	ampEnv.dataQueue.queue( ampEnvelope )
 * ```
 * 
 * As an alternative you could use an EnvelopeDAHDSR.
 * 
 * @author Phil Burk (C) 2010 Mobileer Inc
 */
class SegmentedEnvelope(maxFrames: Int) : SequentialDataCommon() {
    private var buffer: AudioBuffer = AudioBuffer(0)

    init {
        allocate(maxFrames)
    }

    constructor(pairs: DoubleArray) : this(pairs.size / 2) {
        write(pairs)
    }

    constructor(pairs: FloatArray) : this(pairs.size / 2) {
        write(pairs)
    }

    fun allocate(maxFrames: Int) {
        buffer = AudioBuffer(maxFrames * 2)
        this.maxFrames = maxFrames
        this._numFrames = 0
    }

    /**
     * Write frames of envelope data. A frame consists of a duration and a value.
     * 
     * @param startFrame Index of frame in envelope to write to.
     * @param data Pairs of duration and value.
     * @param startIndex Index of frame in data[] to read from.
     * @param numToWrite Number of frames (pairs) to write.
     */
    fun write(startFrame: Int, data: DoubleArray, startIndex: Int, numToWrite: Int) {
        for (i in 0 until (numToWrite * 2)) {
            buffer[startFrame * 2 + i] = data[startIndex * 2 + i].toFloat()
        }
        if ((startFrame + numToWrite) > _numFrames) {
            _numFrames = startFrame + numToWrite
        }
    }

    fun write(startFrame: Int, data: FloatArray, startIndex: Int, numToWrite: Int) {
        data.copyInto(buffer, startFrame * 2, startIndex * 2, startIndex * 2 + numToWrite * 2)
        if ((startFrame + numToWrite) > _numFrames) {
            _numFrames = startFrame + numToWrite
        }
    }

    fun read(startFrame: Int, data: DoubleArray, startIndex: Int, numToRead: Int) {
        for (i in 0 until (numToRead * 2)) {
            data[startIndex * 2 + i] = buffer[startFrame * 2 + i].toDouble()
        }
    }

    fun read(startFrame: Int, data: FloatArray, startIndex: Int, numToRead: Int) {
        buffer.copyInto(data, startIndex * 2, startFrame * 2, startFrame * 2 + numToRead * 2)
    }

    fun write(data: DoubleArray) {
        write(0, data, 0, data.size / 2)
    }

    fun write(data: FloatArray) {
        write(0, data, 0, data.size / 2)
    }

    fun read(data: DoubleArray) {
        read(0, data, 0, data.size / 2)
    }

    fun read(data: FloatArray) {
        read(0, data, 0, data.size / 2)
    }

    /** Read the value of an envelope, not the duration. */
    override fun readDouble(index: Int): Double {
        return buffer[(index * 2) + 1].toDouble()
    }

    override fun writeDouble(index: Int, value: Double) {
        buffer[(index * 2) + 1] = value.toFloat()
        if ((index + 1) > _numFrames) {
            _numFrames = index + 1
        }
    }

    override fun readSample(index: Int): AudioSample {
        return buffer[(index * 2) + 1]
    }

    override fun writeSample(index: Int, value: AudioSample) {
        buffer[(index * 2) + 1] = value
        if ((index + 1) > _numFrames) {
            _numFrames = index + 1
        }
    }

    // TODO review rate vs period. Maybe rename this method to getScaledRate()? What does this really do?
    override fun getRateScaler(index: Int, synthesisPeriod: Double): Double {
        var duration = buffer[index * 2].toDouble()
        if (duration < synthesisPeriod) {
            duration = synthesisPeriod
        }
        return 1.0 / duration
    }

    override val channelsPerFrame: Int
        get() = 1
}
