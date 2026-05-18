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

import com.softsynth.ksyn.AudioSample

/**
 * Interface for objects that can be read and/or written by index. The index is not stored
 * internally so they can be shared by multiple readers.
 *
 * @author Phil Burk (C) 2010 Mobileer Inc
 */
interface SequentialData {
    /**
     * Write a value at the given index.
     *
     * @param index sample index is ((frameIndex * channelsPerFrame) + channelIndex)
     * @param value the value to be written
     */
    fun writeSample(index: Int, value: AudioSample)

    /**
     * Read a value independently from the internal storage format.
     *
     * @param index sample index is ((frameIndex * channelsPerFrame) + channelIndex)
     */
    fun readSample(index: Int): AudioSample

    fun writeDouble(index: Int, value: Double)
    fun readDouble(index: Int): Double

    /**
     * @return Beginning of sustain loop or -1 if no loop.
     */
    val sustainBegin: Int

    /**
     * SustainEnd value is the frame index of the frame just past the end of the loop. The number of
     * frames included in the loop is (SustainEnd - SustainBegin).
     *
     * @return End of sustain loop or -1 if no loop.
     */
    val sustainEnd: Int

    /**
     * @return Beginning of release loop or -1 if no loop.
     */
    val releaseBegin: Int

    /**
     * @return End of release loop or -1 if no loop.
     */
    val releaseEnd: Int

    /**
     * Get rate to play the data. In an envelope this corresponds to the inverse of the frame
     * duration and would vary frame to frame. For an audio sample it is 1.0.
     *
     * @param index
     * @param synthesisPeriod
     * @return rate to scale the playback speed.
     */
    fun getRateScaler(index: Int, synthesisPeriod: Double): Double

    /**
     * @return For a stereo sample, return 2.
     */
    val channelsPerFrame: Int

    /**
     * @return The number of valid frames that can be read.
     */
    val numFrames: Int
}
