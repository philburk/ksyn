/*
 * Copyright 2009 Phil Burk, Mobileer Inc
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

import com.softsynth.ksyn.ports.UnitInputPort
import kotlin.math.sqrt

/**
 * PeakingEQ Filter. This can be used to raise or lower the gain around the cutoff frequency.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
class FilterPeakingEQ : FilterBiquadCommon() {
    val gain = UnitInputPort("Gain", 1.0)

    private var previousGain: Double = 0.0

    init {
        addPort(gain)
    }

    override fun isRecalculationNeeded(frequencyValue: Double, qValue: Double): Boolean {
        var currentGain = gain.getValues()[0].toDouble()
        if (currentGain < FilterBiquad.MINIMUM_GAIN) {
            currentGain = FilterBiquad.MINIMUM_GAIN
        }

        var needed = super.isRecalculationNeeded(frequencyValue, qValue)
        needed = needed || (previousGain != currentGain)

        previousGain = currentGain
        return needed
    }

    override fun updateCoefficients() {
        val factorA = sqrt(previousGain)
        val alphaTimesA = alpha * factorA
        val alphaOverA = alpha / factorA
        // Note this is not the normal scalar!
        val scalar = 1.0 / (1.0 + alphaOverA)
        val a1_b1_value = -2.0 * cos_omega * scalar

        this.a0 = ((1.0 + alphaTimesA) * scalar).toFloat()

        this.a1 = a1_b1_value.toFloat()
        this.a2 = ((1.0 - alphaTimesA) * scalar).toFloat()

        this.b1 = a1_b1_value.toFloat()
        this.b2 = ((1.0 - alphaOverA) * scalar).toFloat()
    }
}
