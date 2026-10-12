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

import kotlin.math.floor

/**
 * Fractional delay using 3rd order Lagrange interpolation between 4 taps.
 *
 * The delay can change on every frame without clicks, so it is good for pitch bends and vibrato.
 * The interpolation loses a little high frequency energy, which varies slightly with the delay.
 */
class LagrangeFractionalDelay(maxDelay: Int) : FractionalDelay(maxDelay) {
    private var firstTap = 1
    private var h0 = 0.0f
    private var h1 = 1.0f
    private var h2 = 0.0f
    private var h3 = 0.0f

    override val isSmooth: Boolean
        get() = true

    override fun setDelay(delay: Double, omega: Double) {
        val limitedDelay = limitDelay(delay)
        // Read 4 taps so that the fractional position d is between the middle two, 1 <= d < 2.
        firstTap = floor(limitedDelay).toInt() - 1
        val d = (limitedDelay - firstTap).toFloat()

        // Lagrange coefficients for taps at positions 0, 1, 2, 3 evaluated at d.
        val dm1 = d - 1.0f
        val dm2 = d - 2.0f
        val dm3 = d - 3.0f
        h0 = -dm1 * dm2 * dm3 * (1.0f / 6.0f)
        h1 = d * dm2 * dm3 * 0.5f
        h2 = -d * dm1 * dm3 * 0.5f
        h3 = d * dm1 * dm2 * (1.0f / 6.0f)
    }

    override fun read(): Float {
        return (h0 * tap(firstTap)) + (h1 * tap(firstTap + 1)) +
                (h2 * tap(firstTap + 2)) + (h3 * tap(firstTap + 3))
    }
}
