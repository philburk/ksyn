/*
 * Copyright 2014 Phil Burk, Mobileer Inc
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
import kotlin.math.sqrt

/**
 * Resonant filter in the style of the Moog ladder filter.
 *
 * @author Phil Burk (C) 2014 Mobileer Inc
 * @see FilterLowPass
 */
class FilterFourPoles : TunableFilter() {
    val Q = UnitInputPort("Q")
    val gain = UnitInputPort("Gain") // unused but present in Java

    companion object {
        private const val MINIMUM_FREQUENCY = 1.0
        private const val MINIMUM_Q = 0.00001
        private const val SATURATION_COEFFICIENT = 0.2
        private val SATURATION_UPPER_INPUT = 1.0 / sqrt(3.0 * SATURATION_COEFFICIENT)
        private val SATURATION_LOWER_INPUT = 0.0 - SATURATION_UPPER_INPUT
        private val SATURATION_UPPER_OUTPUT = cubicPolynomial(SATURATION_UPPER_INPUT)
        private val SATURATION_LOWER_OUTPUT = cubicPolynomial(SATURATION_LOWER_INPUT)

        private fun cubicPolynomial(x: Double): Double {
            return x - (x * x * x * SATURATION_COEFFICIENT)
        }

        private fun cubicPolynomial(x: Float): Float {
            return (x - (x * x * x * SATURATION_COEFFICIENT)).toFloat()
        }
    }

    private var x1: Float = 0f
    private var x2: Float = 0f
    private var x3: Float = 0f
    private var x4: Float = 0f
    private var y1: Float = 0f
    private var y2: Float = 0f
    private var y3: Float = 0f
    private var y4: Float = 0f

    private var previousFrequency: Double = 0.0
    private var previousQ: Double = 0.0
    // filter coefficients
    private var f: Float = 0f
    private var fTo4th: Float = 0f
    private var feedback: Float = 0f

    var isOversampled: Boolean = true

    init {
        addPort(Q)
        frequency.setup(40.0, 400.0, 4000.0)
        Q.setup(0.1, 2.0, 10.0)
    }

    fun recalculate() {
        var frequencyValue = frequency.getValues()[0].toDouble()
        var qValue = Q.getValues()[0].toDouble()

        if (frequencyValue < MINIMUM_FREQUENCY) {
            frequencyValue = MINIMUM_FREQUENCY
        }
        if (qValue < MINIMUM_Q) {
            qValue = MINIMUM_Q
        }

        if ((frequencyValue != previousFrequency) || (qValue != previousQ)) {
            previousFrequency = frequencyValue
            previousQ = qValue
            computeCoefficients()
        }
    }

    private fun computeCoefficients() {
        val normalizedFrequency = previousFrequency * framePeriod
        var fudge = 4.9 - 0.27 * previousQ
        if (fudge < 3.0) fudge = 3.0
        val fDouble = normalizedFrequency * (if (isOversampled) 1.0 else 2.0) * fudge
        f = fDouble.toFloat()

        val fSquared = fDouble * fDouble
        fTo4th = (fSquared * fSquared).toFloat()
        feedback = (0.5 * previousQ * (1.0 - 0.15 * fSquared)).toFloat()
    }

    override fun generate() {
        val inputs = input.getValues()
        val outputs = output.getValues()

        recalculate()

        // Local vars
        var y1_loc = y1
        var y2_loc = y2
        var y3_loc = y3
        var y4_loc = y4
        var x1_loc = x1
        var x2_loc = x2
        var x3_loc = x3
        var x4_loc = x4
        val f_loc = f
        val fTo4th_loc = fTo4th
        val feedback_loc = feedback
        val coeff = 0.3f

        for (i in 0..<Synthesizer.FRAMES_PER_BLOCK) {
            var x0 = inputs[i]

            if (isOversampled) {
                // oneSample(0.0) inline
                var x0_os = 0.0f
                x0_os -= y4_loc * feedback_loc
                x0_os *= 0.35013f * fTo4th_loc
                y1_loc = x0_os + coeff * x1_loc + (1.0f - f_loc) * y1_loc
                x1_loc = x0_os
                y2_loc = y1_loc + coeff * x2_loc + (1.0f - f_loc) * y2_loc
                x2_loc = y1_loc
                y3_loc = y2_loc + coeff * x3_loc + (1.0f - f_loc) * y3_loc
                x3_loc = y2_loc
                y4_loc = y3_loc + coeff * x4_loc + (1.0f - f_loc) * y4_loc
                y4_loc = clip(y4_loc)
                x4_loc = y3_loc
            }

            // oneSample(x0) inline
            x0 -= y4_loc * feedback_loc
            x0 *= 0.35013f * fTo4th_loc
            y1_loc = x0 + coeff * x1_loc + (1.0f - f_loc) * y1_loc
            x1_loc = x0
            y2_loc = y1_loc + coeff * x2_loc + (1.0f - f_loc) * y2_loc
            x2_loc = y1_loc
            y3_loc = y2_loc + coeff * x3_loc + (1.0f - f_loc) * y3_loc
            x3_loc = y2_loc
            y4_loc = y3_loc + coeff * x4_loc + (1.0f - f_loc) * y4_loc
            y4_loc = clip(y4_loc)
            x4_loc = y3_loc

            outputs[i] = y4_loc
        }

        // update state
        y1 = y1_loc
        y2 = y2_loc
        y3 = y3_loc
        y4 = y4_loc
        x1 = x1_loc
        x2 = x2_loc
        x3 = x3_loc
        x4 = x4_loc

        val verySmall = UnitGenerator.VERY_SMALL_FLOAT.toFloat()
        y1 += verySmall
        y2 -= verySmall
    }

    private fun clip(x: Float): Float {
        if (x > SATURATION_UPPER_INPUT.toFloat()) {
            return SATURATION_UPPER_OUTPUT.toFloat()
        } else if (x < SATURATION_LOWER_INPUT.toFloat()) {
            return SATURATION_LOWER_OUTPUT.toFloat()
        } else {
            return cubicPolynomial(x)
        }
    }

    fun reset() {
        x1 = 0f
        x2 = 0f
        x3 = 0f
        x4 = 0f
        y1 = 0f
        y2 = 0f
        y3 = 0f
        y4 = 0f

        previousFrequency = 0.0
        previousQ = 0.0
        f = 0f
        fTo4th = 0f
        feedback = 0f
    }
}
