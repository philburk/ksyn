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

import com.softsynth.ksyn.dsp.AllPassDelay
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * String resonator tuned with a first-order allpass filter.
 *
 * The allpass has a flat gain so the decay and brightness are the same for every note.
 * Use it for strings whose pitch is fixed during a note, like a piano or harp.
 * The frequency is read once per block. Changing it while the string rings can cause a small transient.
 * For pitch bends use [VariableStringResonator].
 */
class FixedStringResonator(minFrequency: Double = DEFAULT_MIN_FREQUENCY) : StringResonator(minFrequency) {
    private val allPass = AllPassDelay(1)
    private var integerDelay = 2

    override fun updateTuning(frequency: Double) {
        val totalDelay = delayFor(frequency)
        // Keep the allpass delay between 0.5 and 1.5 frames, where its coefficient is small
        // and its delay is nearly the same for all of the harmonics.
        integerDelay = floor(totalDelay - 0.5).toInt()
        val fraction = totalDelay - integerDelay
        // Exact phase delay at the fundamental, not just at DC.
        val omega = 2.0 * PI * frequency / frameRate
        allPass.coefficient = (sin(omega * (1.0 - fraction) / 2.0)
                / sin(omega * (1.0 + fraction) / 2.0)).toFloat()
    }

    override fun readDelayed(frequency: Float): Float {
        return allPass.process(delayLine.read(integerDelay))
    }

    override fun clearTuning() {
        allPass.clear()
    }
}
