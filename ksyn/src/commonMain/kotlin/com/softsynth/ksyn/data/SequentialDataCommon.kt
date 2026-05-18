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
 * Abstract base class for envelopes and samples that adds sustain and release loops.
 *
 * @author Phil Burk (C) 2010 Mobileer Inc
 */
abstract class SequentialDataCommon : SequentialData {
    protected var _numFrames: Int = 0
    var maxFrames: Int = 0
        protected set
        
    override var sustainBegin: Int = -1
    override var sustainEnd: Int = -1
    override var releaseBegin: Int = -1
    override var releaseEnd: Int = -1

    abstract override fun writeDouble(index: Int, value: Double)
    abstract override fun readDouble(index: Int): Double
    abstract override fun writeSample(index: Int, value: AudioSample)
    abstract override fun readSample(index: Int): AudioSample
    abstract override fun getRateScaler(index: Int, synthesisPeriod: Double): Double
    abstract override val channelsPerFrame: Int

    /**
     * Set number of frames of data. Input will be clipped to maxFrames. This is useful when
     * changing the contents of a sample or envelope.
     */
    fun setNumFrames(numFrames: Int) {
        _numFrames = if (numFrames > maxFrames) maxFrames else numFrames
    }

    override val numFrames: Int
        get() = _numFrames
}
