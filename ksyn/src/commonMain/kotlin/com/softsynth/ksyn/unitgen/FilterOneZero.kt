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
import com.softsynth.ksyn.ports.UnitVariablePort

/**
 * First Order, One Zero filter using the following formula:
 * 
 * <pre>
 * y(n) = A0 * x(n) + A1 * x(n - 1)
 * </pre>
 * 
 * where y(n) is Output, x(n) is Input and x(n-1) is Input at the prior sample tick. Setting A1
 * positive gives a low-pass response; setting A1 negative gives a high-pass response. The bandwidth
 * of this filter is fairly high, so it often serves a building block by being cascaded with other
 * filters. If A0 and A1 are both 0.5, then this filter is a simple averaging lowpass filter, with a
 * zero at SR/2 = 22050 Hz. If A0 is 0.5 and A1 is -0.5, then this filter is a high pass filter,
 * with a zero at 0.0 Hz. A thorough description of the digital filter theory needed to fully
 * describe this filter is beyond the scope of this document. Calculating coefficients is
 * non-intuitive; the interested user is referred to one of the standard texts on filter theory
 * (e.g., Moore, "Elements of Computer Music", section 2.4).
 * 
 * @author Phil Burk (C) 2011 Mobileer Inc
 * @see FilterLowPass
 */
class FilterOneZero : UnitFilter() {
    val a0: UnitVariablePort
    val a1: UnitVariablePort
    private var x1: Double = 0.0

    init {
        a0 = UnitVariablePort("A0", 0.5)
        addPort(a0)
        a1 = UnitVariablePort("A1", 0.5)
        addPort(a1)
    }

    override fun generate() {
        val inputs = input.getValues()
        val outputs = output.getValues()
        val a0v = a0.value
        val a1v = a1.value

        var x1_loc = x1
        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            val x0 = inputs[i].toDouble()
            outputs[i] = ((a0v * x0) + (a1v * x1_loc)).toFloat()
            x1_loc = x0
        }
        x1 = x1_loc
    }
}
