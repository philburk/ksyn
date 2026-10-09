/*
 * Copyright 2026 Phil Burk
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

package com.softsynth.ksyn.dsp

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One-pole low pass filter with a gain of 1.0 at DC:
 *
 * <pre>
 * H(z) = (1 - pole) / (1 - pole * z^-1)
 * </pre>
 *
 * Its delay varies with frequency, so use [phaseDelayAt] when it is inside a tuned loop.
 *
 * @param pole between 0.0 (no filtering) and just below 1.0 (very dark)
 */
class OnePoleLowPass(pole: Float = 0.0f) {
    /** Between 0.0 and just below 1.0. Can be changed at any time. */
    var pole: Float = pole

    private var y1: Float = 0.0f

    fun process(input: Float): Float {
        val p = pole
        y1 = ((1.0f - p) * input) + (p * y1)
        return y1
    }

    /** Set the internal state to zero. */
    fun clear() {
        y1 = 0.0f
    }

    /**
     * Set the pole for an approximate -3 dB cutoff frequency.
     *
     * @param cutoff in Hz
     * @param frameRate in Hz
     */
    fun setCutoff(cutoff: Double, frameRate: Double) {
        pole = exp(-2.0 * PI * cutoff / frameRate).toFloat()
    }

    /**
     * @param omega radian frequency, 2 * PI * frequency / frameRate
     * @return magnitude of the frequency response, 1.0 at DC
     */
    fun gainAt(omega: Double): Double {
        val p = pole.toDouble()
        return (1.0 - p) / sqrt(1.0 - (2.0 * p * cos(omega)) + (p * p))
    }

    /**
     * @param omega radian frequency, 2 * PI * frequency / frameRate, greater than zero
     * @return delay in frames of a sine wave at that frequency
     */
    fun phaseDelayAt(omega: Double): Double {
        val p = pole.toDouble()
        return atan(p * sin(omega) / (1.0 - (p * cos(omega)))) / omega
    }
}
