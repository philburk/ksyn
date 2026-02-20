/*
 * Copyright 2011 Phil Burk, Mobileer Inc
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Base class for a set of IIR filters.
 *
 * @author Phil Burk (C) 2011 Mobileer Inc
 * @see FilterBandStop
 * @see FilterBandPass
 * @see FilterLowPass
 * @see FilterHighPass
 */
abstract class FilterBiquad : TunableFilter() {
    val amplitude = UnitInputPort("Amplitude", 1.0)

    companion object {
        const val MINIMUM_FREQUENCY = 0.00001
        const val MINIMUM_GAIN = 0.00001
        const val RATIO_MINIMUM = 0.499
    }

    protected var a0: Float = 0f
    protected var a1: Float = 0f
    protected var a2: Float = 0f
    protected var b1: Float = 0f
    protected var b2: Float = 0f
    private var x1: Float = 0f
    private var x2: Float = 0f
    private var y1: Float = 0f
    private var y2: Float = 0f
    protected var previousFrequency: Double = 0.0
    protected var omega: Double = 0.0
    protected var sin_omega: Double = 0.0
    protected var cos_omega: Double = 0.0

    init {
        addPort(amplitude)
    }

    override fun generate() {
        recalculate()
        performBiquadFilter()
    }

    protected abstract fun recalculate()

    fun performBiquadFilter() {
        val inputs = input.getValues()
        val amplitudes = amplitude.getValues()
        val outputs = output.getValues()

        var x1_loc = x1
        var x2_loc = x2
        var y1_loc = y1
        var y2_loc = y2

        val a0_loc = a0
        val a1_loc = a1
        val a2_loc = a2
        val b1_loc = b1
        val b2_loc = b2

        // Permute filter operations to reduce data movement.
        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK step 2) {
            val x0 = inputs[i]
            y2_loc = (a0_loc * x0) + (a1_loc * x1_loc) + (a2_loc * x2_loc) - (b1_loc * y1_loc) - (b2_loc * y2_loc)
            outputs[i] = amplitudes[i] * y2_loc

            val xNext = inputs[i + 1]
            y1_loc = (a0_loc * xNext) + (a1_loc * x0) + (a2_loc * x1_loc) - (b1_loc * y2_loc) - (b2_loc * y1_loc)
            outputs[i + 1] = amplitudes[i + 1] * y1_loc

            x1_loc = xNext
            x2_loc = x0
        }

        x1 = x1_loc
        x2 = x2_loc

        // apply small bipolar impulse to prevent arithmetic underflow
        val verySmall = UnitGenerator.VERY_SMALL_FLOAT.toFloat()
        y1 = y1_loc + verySmall
        y2 = y2_loc - verySmall
    }

    protected fun calculateOmega(ratio: Double) {
        var r = ratio
        if (r >= RATIO_MINIMUM) {
            r = RATIO_MINIMUM
        }
        omega = 2.0 * PI * r
        cos_omega = cos(omega)
        sin_omega = sin(omega)
    }
}
