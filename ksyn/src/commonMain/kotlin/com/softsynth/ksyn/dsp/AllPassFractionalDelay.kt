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
import kotlin.math.sin

/**
 * Fractional delay using an integer delay followed by a first-order allpass filter.
 *
 * The gain is perfectly flat, so it does not damp a feedback loop.
 * The delay is exact at the frequency passed to [setDelay] and nearly the same for its harmonics.
 * Changing the delay can cause a small transient, so it is best for fixed delays.
 */
class AllPassFractionalDelay(maxDelay: Int) : FractionalDelay(maxDelay) {
    private val allPass = AllPassDelay(1)
    private var integerDelay = 2

    override val isSmooth: Boolean
        get() = false

    override fun setDelay(delay: Double, omega: Double) {
        val limitedDelay = limitDelay(delay)
        // Keep the allpass delay between 0.5 and 1.5 frames, where its coefficient is small
        // and its delay is nearly the same for all of the harmonics.
        integerDelay = floor(limitedDelay - 0.5).toInt()
        val fraction = limitedDelay - integerDelay
        // Exact phase delay at omega, not just at DC.
        allPass.coefficient = (sin(omega * (1.0 - fraction) / 2.0)
                / sin(omega * (1.0 + fraction) / 2.0)).toFloat()
    }

    override fun read(): Float {
        return allPass.process(tap(integerDelay))
    }

    override fun clear() {
        super.clear()
        allPass.clear()
    }
}
