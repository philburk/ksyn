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

import com.softsynth.ksyn.dsp.BiquadLowPass
import com.softsynth.ksyn.ports.UnitInputPort
import kotlin.math.pow

/**
 * Base class for Karplus-Strong style string resonators.
 *
 * This is a [FilterComb] with a two-pole Butterworth low pass filter in the loop.
 * Use any signal on [input] as the excitation, for example a burst of noise, an impulse,
 * a sample or live audio.
 *
 * The [frequency] port sets the fundamental. All of the internal delays are included in the tuning.
 * The [decay] port sets the time in seconds for the fundamental to decay by 60 dB.
 * Lowering [decay] while the string rings acts like a damper.
 * The [brightness] port controls how quickly the upper harmonics decay relative to the fundamental.
 * It sets the cutoff of the loop filter relative to the fundamental, so the tone is similar
 * at every pitch. The filter is nearly flat up to the fundamental, so [decay] and [brightness]
 * are mostly independent. The loop gain must stay below 1.0, so a very long [decay]
 * with a dark setting may decay sooner than requested, especially at high pitches.
 *
 * @param minFrequency lowest frequency supported, determines the size of the delay line
 * @param interpolation how the fractional delay is calculated
 * @see FixedStringResonator
 * @see VariableStringResonator
 */
abstract class StringResonator(
    minFrequency: Double,
    interpolation: Interpolation,
) : FilterComb(minFrequency, interpolation) {

    /**
     * 0.0 is dark, like a nylon string, with the loop filter cutoff at 3 times the fundamental.
     * 1.0 is bright, like a steel string, with the cutoff at 256 times the fundamental.
     */
    val brightness = UnitInputPort("Brightness", 0.5)

    private val loopFilter = BiquadLowPass()
    private var filterFrequency = -1.0
    private var filterBrightness = -1.0

    init {
        addPort(brightness)
        brightness.setup(0.0, 0.5, 1.0)
    }

    override fun updateLoopFilter(frequency: Double, omega: Double): Boolean {
        val currentBrightness = brightness.getValues()[0].toDouble()
        if (frequency == filterFrequency && currentBrightness == filterBrightness) {
            return false
        }
        val ratio = MIN_CUTOFF_RATIO * (MAX_CUTOFF_RATIO / MIN_CUTOFF_RATIO)
            .pow(currentBrightness.coerceIn(0.0, 1.0))
        loopFilter.setCutoff(frequency * ratio, frameRate)
        filterFrequency = frequency
        filterBrightness = currentBrightness
        return true
    }

    override fun filterLoop(input: Float): Float = loopFilter.process(input)

    override fun loopFilterDelayAt(omega: Double): Double = loopFilter.phaseDelayAt(omega)

    override fun loopFilterGainAt(omega: Double): Double = loopFilter.gainAt(omega)

    override fun clearLoopFilter() {
        loopFilter.clear()
    }

    companion object {
        /** Loop filter cutoff, relative to the fundamental, at brightness 0.0 and 1.0. */
        private const val MIN_CUTOFF_RATIO = 3.0
        private const val MAX_CUTOFF_RATIO = 256.0
    }
}
