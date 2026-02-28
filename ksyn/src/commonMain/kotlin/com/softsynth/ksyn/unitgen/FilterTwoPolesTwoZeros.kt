/*
 * Copyright 1997 Phil Burk, Mobileer Inc
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
import com.softsynth.ksyn.ports.UnitVariablePort

/**
 * Second Order, Two Pole, Two Zero filter using the following formula:
 * 
 * <pre>
 * y(n) = 2.0 * (A0 * x(n) + A1 * x(n - 1) + A2 * x(n - 2) - B1 * y(n - 1) - B2 * y(n - 2))
 * </pre>
 * 
 * where y(n) is Output, x(n) is Input, x(n-1) is a delayed copy of the input, and y(n-1) is a
 * delayed copy of the output. This filter is a recursive IIR or Infinite Impulse Response filter.
 * It can be unstable depending on the values of the coefficients. This filter is basically the same
 * as the FilterBiquad with different ports. A thorough description of the digital filter theory
 * needed to fully describe this filter is beyond the scope of this document. Calculating
 * coefficients is non-intuitive; the interested user is referred to one of the standard texts on
 * filter theory (e.g., Moore, "Elements of Computer Music", section 2.4). Special thanks to Robert
 * Bristow-Johnson for contributing his filter equations to the music-dsp list. They were used for
 * calculating the coefficients for the lowPass, highPass, and other parametric filter calculations.
 * 
 * @author (C) 1997-2009 Phil Burk, SoftSynth.com
 */

class FilterTwoPolesTwoZeros : UnitFilter() {
    val a0: UnitVariablePort
    val a1: UnitVariablePort
    val a2: UnitVariablePort
    val b1: UnitVariablePort
    val b2: UnitVariablePort

    private var x1: Double = 0.0
    private var y1: Double = 0.0
    private var x2: Double = 0.0
    private var y2: Double = 0.0

    init {
        a0 = UnitVariablePort("A0")
        addPort(a0)
        a1 = UnitVariablePort("A1")
        addPort(a1)
        a2 = UnitVariablePort("A2")
        addPort(a2)
        b1 = UnitVariablePort("B1")
        addPort(b1)
        b2 = UnitVariablePort("B2")
        addPort(b2)
    }

    override fun generate() {
        val inputs = input.getValues()
        val outputs = output.getValues()
        val a0v = a0.value
        val a1v = a1.value
        val a2v = a2.value
        val b1v = b1.value
        val b2v = b2.value

        var x1_loc = x1
        var y1_loc = y1
        var x2_loc = x2
        var y2_loc = y2

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            val x0 = inputs[i].toDouble()
            y1_loc = 2.0 * ((a0v * x0) + (a1v * x1_loc) + (a2v * x2_loc) + (b1v * y1_loc) + (b2v * y2_loc))
            outputs[i] = y1_loc.toFloat()
            x2_loc = x1_loc
            x1_loc = x0
            y2_loc = y1_loc
        }

        x1 = x1_loc
        y1 = y1_loc
        x2 = x2_loc
        y2 = y2_loc
    }
}
