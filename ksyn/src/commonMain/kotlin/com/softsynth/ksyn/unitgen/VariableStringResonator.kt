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

package com.softsynth.ksyn.unitgen

import kotlin.math.floor

/**
 * String resonator tuned with 3rd order Lagrange interpolation.
 *
 * The frequency is read on every frame and can change smoothly while the string rings.
 * Use it for pitch bends and vibrato, like on a guitar.
 * The interpolation loses a little high frequency energy, which varies slightly with the frequency.
 * For fixed pitches [FixedStringResonator] has a perfectly flat response.
 */
class VariableStringResonator(minFrequency: Double = DEFAULT_MIN_FREQUENCY) : StringResonator(minFrequency) {

    override fun updateTuning(frequency: Double) {
        // The delay is calculated on every frame in readDelayed().
    }

    override fun readDelayed(frequency: Float): Float {
        val totalDelay = delayFor(limitFrequency(frequency.toDouble()))
        // Read 4 taps so that the fractional position d is between the middle two, 1 <= d < 2.
        val firstTap = floor(totalDelay).toInt() - 1
        val d = (totalDelay - firstTap).toFloat()
        val x0 = delayLine.read(firstTap)
        val x1 = delayLine.read(firstTap + 1)
        val x2 = delayLine.read(firstTap + 2)
        val x3 = delayLine.read(firstTap + 3)

        // Lagrange coefficients for taps at positions 0, 1, 2, 3 evaluated at d.
        val dm1 = d - 1.0f
        val dm2 = d - 2.0f
        val dm3 = d - 3.0f
        val h0 = -dm1 * dm2 * dm3 * (1.0f / 6.0f)
        val h1 = d * dm2 * dm3 * 0.5f
        val h2 = -d * dm1 * dm3 * 0.5f
        val h3 = d * dm1 * dm2 * (1.0f / 6.0f)
        return (h0 * x0) + (h1 * x1) + (h2 * x2) + (h3 * x3)
    }
}
