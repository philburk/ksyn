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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Two-pole low pass filter with a gain of 1.0 at DC.
 * The default Q of 0.7071 gives a Butterworth response, which is as flat as possible below the cutoff.
 *
 * The state is kept in double precision because the poles get very close to 1.0
 * when the cutoff is low.
 * Its delay varies with frequency, so use [phaseDelayAt] when it is inside a tuned loop.
 */
class BiquadLowPass {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(input: Float): Float {
        val x0 = input.toDouble()
        val y0 = (b0 * x0) + (b1 * x1) + (b2 * x2) - (a1 * y1) - (a2 * y2)
        x2 = x1
        x1 = x0
        y2 = y1
        y1 = y0
        return y0.toFloat()
    }

    /** Set the internal state to zero. */
    fun clear() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }

    /**
     * Calculate coefficients using the Audio EQ Cookbook by Robert Bristow-Johnson.
     *
     * @param cutoff in Hz, limited to [MAX_CUTOFF_RATIO] * frameRate
     * @param frameRate in Hz
     * @param q resonance, 0.7071 for Butterworth
     */
    fun setCutoff(cutoff: Double, frameRate: Double, q: Double = BUTTERWORTH_Q) {
        val limitedCutoff = cutoff.coerceAtMost(MAX_CUTOFF_RATIO * frameRate)
        val omega = 2.0 * PI * limitedCutoff / frameRate
        val cosOmega = cos(omega)
        val alpha = sin(omega) / (2.0 * q)
        val a0 = 1.0 + alpha
        b0 = ((1.0 - cosOmega) / 2.0) / a0
        b1 = (1.0 - cosOmega) / a0
        b2 = b0
        a1 = (-2.0 * cosOmega) / a0
        a2 = (1.0 - alpha) / a0
    }

    /**
     * @param omega radian frequency, 2 * PI * frequency / frameRate
     * @return magnitude of the frequency response, 1.0 at DC
     */
    fun gainAt(omega: Double): Double {
        val numerator = evaluate(b0, b1, b2, omega)
        val denominator = evaluate(1.0, a1, a2, omega)
        return hypot(numerator[0], numerator[1]) / hypot(denominator[0], denominator[1])
    }

    /**
     * @param omega radian frequency, 2 * PI * frequency / frameRate, greater than zero
     * @return delay in frames of a sine wave at that frequency
     */
    fun phaseDelayAt(omega: Double): Double {
        val numerator = evaluate(b0, b1, b2, omega)
        val denominator = evaluate(1.0, a1, a2, omega)
        var phase = atan2(numerator[1], numerator[0]) - atan2(denominator[1], denominator[0])
        // A two-pole low pass lags by 0 to 180 degrees.
        while (phase > 0.0) phase -= 2.0 * PI
        while (phase <= -2.0 * PI) phase += 2.0 * PI
        return -phase / omega
    }

    /** @return real and imaginary parts of c0 + c1 * e^-jw + c2 * e^-2jw */
    private fun evaluate(c0: Double, c1: Double, c2: Double, omega: Double): DoubleArray {
        val real = c0 + (c1 * cos(omega)) + (c2 * cos(2.0 * omega))
        val imaginary = -(c1 * sin(omega)) - (c2 * sin(2.0 * omega))
        return doubleArrayOf(real, imaginary)
    }

    companion object {
        const val BUTTERWORTH_Q = 0.7071067811865476

        /** The cutoff is limited to this fraction of the frame rate. */
        const val MAX_CUTOFF_RATIO = 0.45
    }
}
