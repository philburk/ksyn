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

import kotlin.math.PI
import com.softsynth.ksyn.data.AudioSample

/**
 * A simple sine wave generator for a Grain. This uses the same fast Taylor expansion that the
 * SineOscillator uses.
 * 
 * @author Phil Burk (C) 2011 Mobileer Inc
 */
class GrainSourceSine : GrainCommon(), GrainSource {
    protected var phase: Double = 0.0
    var phaseIncrement: Double = 0.0

    init {
        setRate(1.0)
    }

    override fun next(): AudioSample {
        phase += phaseIncrement
        if (phase > 1.0) {
            phase -= 2.0
        }
        return SineOscillator.fastSin(phase).toFloat()
    }

    override fun setRate(rate: Double) {
        phaseIncrement = rate * 0.1 / PI
    }
}
