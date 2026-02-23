/*
 * Copyright 2011 Phil Burk, Mobileer Inc
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

import com.softsynth.ksyn.data.AudioSample

/**
 * A single Grain that is normally created and controlled by a GrainFarm.
 * 
 * @author Phil Burk (C) 2011 Mobileer Inc
 */
class Grain(val source: GrainSource, val envelope: GrainEnvelope) : GrainEnvelope {

    private var _frameRate: AudioSample = 0.0f
    var amplitude: AudioSample = 1.0f

    override fun next(): AudioSample {
        return if (envelope.hasMoreValues()) {
            val env = envelope.next()
            source.next() * env * amplitude
        } else {
            0.0f
        }
    }

    override fun hasMoreValues(): Boolean {
        return envelope.hasMoreValues()
    }

    override fun reset() {
        source.reset()
        envelope.reset()
    }

    fun setRate(rate: Double) {
        source.setRate(rate)
    }

    override fun setDuration(duration: Double) {
        envelope.setDuration(duration)
    }

    override var frameRate: AudioSample
        get() = _frameRate
        set(value) {
            _frameRate = value
            source.frameRate = value
            envelope.frameRate = value
        }
}
