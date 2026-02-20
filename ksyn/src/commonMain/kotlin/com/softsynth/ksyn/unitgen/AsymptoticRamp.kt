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

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitVariablePort
import kotlin.math.abs

/**
 * Output approaches Input exponentially. This unit provides a slowly changing value that approaches
 * its Input value exponentially. The equation is:
 *
 * <PRE>
 * Output = Output + Rate * (Input - Output);
 * </PRE>
 *
 * Note that the output may never reach the value of the input. It approaches the input
 * asymptotically. The Rate is calculated internally based on the value on the halfLife port. Rate
 * is generally just slightly less than 1.0.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 * @version 016
 * @see LinearRamp
 * @see ExponentialRamp
 * @see ContinuousRamp
 */
class AsymptoticRamp : UnitFilter() {
    var current = UnitVariablePort("Current")
    var halfLife = UnitInputPort(1, "HalfLife", 0.1f)
    private var previousHalfLife: Double = -1.0
    private var decayScalar: Float = 0.99f

    init {
        addPort(halfLife)
        addPort(current)
    }

    override fun generate() {
        val outputs = output.getValues()
        val inputs = input.getValues()
        val currentHalfLife = halfLife.getValues()[0].toDouble()
        var currentValue = current.getValue(0)
        var inputValue: Float = currentValue

        if (currentHalfLife != previousHalfLife) {
            decayScalar = convertHalfLifeToMultiplier(currentHalfLife).toFloat()
            previousHalfLife = currentHalfLife
        }

        for (i in 0..<Synthesizer.FRAMES_PER_BLOCK) {
            inputValue = inputs[i]
            currentValue += decayScalar * (inputValue - currentValue)
            outputs[i] = currentValue
        }

        /*
         * When current gets close to input, set current to input to prevent FP underflow, which can
         * cause a severe performance degradation in 'C'.
         */
        if (abs(inputValue - currentValue) < VERY_SMALL_FLOAT) {
            currentValue = inputValue
        }

        current.set(0, currentValue)
    }
}
