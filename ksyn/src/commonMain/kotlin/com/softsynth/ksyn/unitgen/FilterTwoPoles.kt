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
 * Second Order, Two Pole filter using the following formula:
 *
 * <pre>
 * y(n) = A0 * x(n) - B1 * y(n - 1) - B2 * y(n - 2)
 * </pre>
 *
 * where y(n) is Output, x(n) is Input, and y(n-1) is a delayed copy of the output. This filter is a
 * recursive IIR or Infinite Impulse Response filter. it can be unstable depending on the values of
 * the coefficients. A thorough description of the digital filter theory needed to fully describe
 * this filter is beyond the scope of this document. Calculating coefficients is non-intuitive; the
 * interested user is referred to one of the standard texts on filter theory (e.g., Moore,
 * "Elements of Computer Music", section 2.4).
 *
 * @author (C) 1997-2009 Phil Burk, Mobileer Inc
 */
class FilterTwoPoles : UnitFilter() {
    var a0 = UnitVariablePort("A0")
    var b1 = UnitVariablePort("B1")
    var b2 = UnitVariablePort("B2")

    private var y1: Float = 0.0f
    private var y2: Float = 0.0f

    init {
        addPort(a0)
        addPort(b1)
        addPort(b2)
    }

    override fun generate() {
        val inputs = input.getValues()
        val outputs = output.getValues()
        val a0v = a0.getValue(0)
        val b1v = b1.getValue(0)
        val b2v = b2.getValue(0)

        for (i in 0..<Synthesizer.FRAMES_PER_BLOCK) {
            val x0 = inputs[i]
            y1 = 2.0f * ((a0v * x0) + (b1v * y1) + (b2v * y2))
            outputs[i] = y1
            y2 = y1
        }
    }
}
