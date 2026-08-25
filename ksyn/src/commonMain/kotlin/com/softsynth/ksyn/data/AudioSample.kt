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

typealias AudioSample = Float

/**
 * Base class for FloatSample and ShortSample.
 *
 * @author Phil Burk (C) 2010 Mobileer Inc
 */
abstract class AudioSampleData : SequentialDataCommon() {
    private var _channelsPerFrame = 1
    var frameRate: Double = 44100.0
    /** The recorded pitch as a fractional MIDI semitone value where 60 is Middle C. */
    var pitch: Double = 0.0
    private var markers: ArrayList<SampleMarker>? = null

    abstract fun allocate(numFrames: Int, channelsPerFrame: Int)

    override fun getRateScaler(index: Int, synthesisPeriod: Double): Double {
        return 1.0
    }

    override val channelsPerFrame: Int
        get() = _channelsPerFrame

    fun setChannelsPerFrame(channelsPerFrame: Int) {
        this._channelsPerFrame = channelsPerFrame
    }

    override fun readDouble(index: Int): Double {
        return readSample(index).toDouble()
    }

    override fun writeDouble(index: Int, value: Double) {
        writeSample(index, value.toFloat())
    }

    val markerCount: Int
        get() = markers?.size ?: 0

    fun getMarker(index: Int): SampleMarker? {
        return markers?.get(index)
    }

    /**
     * Add a marker that will be stored sorted by position. This is normally used internally by the
     * SampleLoader.
     */
    fun addMarker(marker: SampleMarker) {
        if (markers == null) {
            markers = ArrayList()
        }
        val safeMarkers = markers!!
        var idx = safeMarkers.size
        for (k in 0 until safeMarkers.size) {
            val cue = safeMarkers[k]
            if (cue.position > marker.position) {
                idx = k
                break
            }
        }
        safeMarkers.add(idx, marker)
    }
}
