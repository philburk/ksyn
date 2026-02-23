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

import kotlin.math.cos
import kotlin.math.PI
import com.softsynth.ksyn.data.AudioSample

/**
 * An envelope that can be used in a GrainFarm to shape the amplitude of a Grain. The envelope
 * starts at 0.0, rises to 1.0, then returns to 0.0 following a cosine curve.
 * 
 * <pre>
 * output = 0.5 - (0.5 * cos(phase))
 * </pre>
 * 
 * @author Phil Burk (C) 2011 Mobileer Inc
 * @see GrainFarm
 */
class RaisedCosineEnvelope : GrainCommon(), GrainEnvelope {
    protected var phase: Double = 0.0
    protected var phaseIncrement: Double = 0.0

    init {
        frameRate = 44100.0f
        setDuration(0.1)
    }

    /**
     * @return next value of the envelope.
     */
    override fun next(): AudioSample {
        phase += phaseIncrement
        return if (phase > (2.0 * PI)) {
            0.0f
        } else {
            (0.5 - (0.5 * cos(phase))).toFloat() // TODO optimize using Taylor expansion
        }
    }

    /**
     * @return true if there are more envelope values left.
     */
    override fun hasMoreValues(): Boolean {
        return phase < (2.0 * PI)
    }

    /**
     * Reset the envelope back to the beginning.
     */
    override fun reset() {
        phase = 0.0
    }

    override fun setDuration(duration: Double) {
        phaseIncrement = 2.0 * PI / (frameRate * duration)
    }
}
