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

import com.softsynth.ksyn.ports.UnitInputPort

/**
 * PhaseShifter effects processor. This unit emulates a common guitar pedal effect but without the
 * LFO modulation. You can use your own modulation source connected to the "offset" port. Different
 * frequencies are phase shifted varying amounts using a series of AllPass filters. By feeding the
 * output back to the input we can get varying phase cancellation. This implementation was based on
 * code posted to the music-dsp archive by Ross Bencina. http://www.musicdsp.org/files/phaser.cpp
 * 
 * @author (C) 2014 Phil Burk, Mobileer Inc
 */
class PhaseShifter(numStages: Int = 6) : UnitFilter() {
    /**
     * Connect an oscillator to this port to sweep the phase. A range of 0.05 to 0.4 is a good
     * start.
     */
    val offset: UnitInputPort
    val feedback: UnitInputPort
    val depth: UnitInputPort

    private var zm1: Double = 0.0
    private val xs: DoubleArray
    private val ys: DoubleArray

    init {
        offset = UnitInputPort("Offset", 0.1)
        addPort(offset)
        
        feedback = UnitInputPort("Feedback", 0.7)
        addPort(feedback)
        
        depth = UnitInputPort("Depth", 1.0)
        addPort(depth)

        xs = DoubleArray(numStages)
        ys = DoubleArray(numStages)
    }

    override fun generate(start: Int, limit: Int) {
        val inputs = input.getValues()
        val outputs = output.getValues()
        val feedbacks = feedback.getValues()
        val depths = depth.getValues()
        val offsets = offset.getValues()
        var gain: Double

        for (i in start until limit) {
            // Support audio rate modulation.
            val currentOffset = offsets[i]

            // Prevent gain from exceeding 1.0.
            gain = 1.0 - (currentOffset * currentOffset)
            if (gain < -1.0) {
                gain = -1.0
            }

            var x = inputs[i] + (zm1 * feedbacks[i])
            // Cascaded all-pass filters.
            for (stage in xs.indices) {
                val temp = (gain * (ys[stage] - x)) + xs[stage]
                ys[stage] = temp
                xs[stage] = x
                x = temp
            }
            zm1 = x
            outputs[i] = inputs[i] + (x * depths[i])
        }
    }
}
