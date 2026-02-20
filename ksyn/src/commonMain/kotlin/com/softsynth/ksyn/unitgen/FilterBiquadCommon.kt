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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Extend this class to create a filter that implements a Biquad filter with a Q port.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
abstract class FilterBiquadCommon : FilterBiquad() {
    val Q = UnitInputPort("Q")

    companion object {
        const val MINIMUM_Q = 0.00001
    }

    private var previousQ: Double = 0.0
    protected var alpha: Double = 0.0

    init {
        addPort(Q)
        Q.setup(0.1, 1.0, 10.0)
    }

    /**
     * Calculate coefficients based on the filter type, eg. LowPass.
     */
    abstract fun updateCoefficients()

    protected fun computeBiquadCommon(ratio: Double, qVal: Double) {
        var r = ratio
        if (r >= FilterBiquad.RATIO_MINIMUM) {
            r = FilterBiquad.RATIO_MINIMUM
        }

        omega = 2.0 * PI * r
        cos_omega = cos(omega)
        sin_omega = sin(omega)
        alpha = sin_omega / (2.0 * qVal)
    }

    /**
     * The recalculate() method checks and ensures that the frequency and Q values are at a minimum.
     * It also only updates the Biquad coefficients if either frequency or Q have changed.
     */
    override fun recalculate() {
        var frequencyValue = frequency.getValues()[0].toDouble()
        var qValue = Q.getValues()[0].toDouble()

        if (frequencyValue < FilterBiquad.MINIMUM_FREQUENCY) {
            frequencyValue = FilterBiquad.MINIMUM_FREQUENCY
        }

        if (qValue < MINIMUM_Q) {
            qValue = MINIMUM_Q
        }

        // only update changed values
        if (isRecalculationNeeded(frequencyValue, qValue)) {
            previousFrequency = frequencyValue
            previousQ = qValue

            val ratio = frequencyValue * framePeriod
            computeBiquadCommon(ratio, qValue)
            updateCoefficients()
        }
    }

    protected open fun isRecalculationNeeded(frequencyValue: Double, qValue: Double): Boolean {
        return (frequencyValue != previousFrequency) || (qValue != previousQ)
    }
}
