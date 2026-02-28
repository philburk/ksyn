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
 * This filter is based on the BiQuad filter and is used as a base class for FilterLowShelf and
 * FilterHighShelf. Coefficients are updated whenever the frequency, gain or slope changes.
 * 
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
abstract class FilterBiquadShelf : FilterBiquad() {

    companion object {
        const val MINIMUM_SLOPE = 0.00001
    }

    /**
     * Gain of peak. Use 1.0 for flat response.
     */
    val gain: UnitInputPort

    /**
     * Shelf Slope parameter. When S = 1, the shelf slope is as steep as you can get it and remain
     * monotonically increasing or decreasing gain with frequency.
     */
    val slope: UnitInputPort

    private var prevGain: Double = 0.0
    private var prevSlope: Double = 0.0

    private var beta: Double = 0.0
    protected var alpha: Double = 0.0
    protected var factorA: Double = 0.0
    protected var AP1: Double = 0.0
    protected var AM1: Double = 0.0
    protected var beta_sn: Double = 0.0
    protected var AP1cs: Double = 0.0
    protected var AM1cs: Double = 0.0

    init {
        gain = UnitInputPort("Gain", 1.0)
        addPort(gain)
        slope = UnitInputPort("Slope", 1.0)
        addPort(slope)
    }

    /**
     * Abstract method. Each filter must implement its update of coefficients.
     */
    abstract fun updateCoefficients()

    /**
     * Compute coefficients for shelf filter if frequency, gain or slope have changed.
     */
    override fun recalculate() {
        // Just look at first value to save time.
        var frequencyValue = frequency.getValues()[0].toDouble()
        if (frequencyValue < MINIMUM_FREQUENCY) {
            frequencyValue = MINIMUM_FREQUENCY
        }

        var gainValue = gain.getValues()[0].toDouble()
        if (gainValue < MINIMUM_GAIN) {
            gainValue = MINIMUM_GAIN
        }

        var slopeValue = slope.getValues()[0].toDouble()
        if (slopeValue < MINIMUM_SLOPE) {
            slopeValue = MINIMUM_SLOPE
        }

        // Only do complex calculations if input changed.
        if ((frequencyValue != previousFrequency) || (gainValue != prevGain)
                || (slopeValue != prevSlope)) {
            previousFrequency = frequencyValue // hold previous frequency
            prevGain = gainValue
            prevSlope = slopeValue

            val ratio = frequencyValue * framePeriod
            calculateOmega(ratio)

            factorA = sqrt(gainValue)

            AP1 = factorA + 1.0
            AM1 = factorA - 1.0

            /* Avoid sqrt(r<0) which hangs filter. */
            val beta2 = ((gainValue + 1.0) / slopeValue) - (AM1 * AM1)
            beta = if (beta2 < 0.0) 0.0 else sqrt(beta2)

            beta_sn = beta * sin_omega
            AP1cs = AP1 * cos_omega
            AM1cs = AM1 * cos_omega

            updateCoefficients()
        }
    }

}
