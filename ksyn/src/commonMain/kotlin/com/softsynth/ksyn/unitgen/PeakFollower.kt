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
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.ports.UnitVariablePort
import kotlin.math.abs

/**
 * Tracks the peaks of an input signal. This can be used to monitor the overall amplitude of a
 * signal. The output can be used to drive color organs, vocoders, VUmeters, etc. Output drops
 * exponentially when the input drops below the current output level. The output approaches zero
 * based on the value on the halfLife port.
 * 
 * @author (C) 1997-2009 Phil Burk, SoftSynth.com
 */
class PeakFollower : UnitGenerator() {
    val input: UnitInputPort
    val current: UnitVariablePort
    val halfLife: UnitInputPort
    val output: UnitOutputPort

    private var previousHalfLife: Double = -1.0
    private var decayScalar: Double = 0.99

    init {
        input = UnitInputPort("Input")
        addPort(input)
        
        halfLife = UnitInputPort("HalfLife", 0.1)
        addPort(halfLife)
        
        current = UnitVariablePort("Current")
        addPort(current)
        
        output = UnitOutputPort("Output")
        addPort(output)
    }

    override fun generate() {
        val inputs = input.getValues()
        val outputs = output.getValues()
        val currentHalfLife = halfLife.getValues()[0].toDouble()
        var currentValue = current.getValue()

        if (currentHalfLife != previousHalfLife) {
            decayScalar = convertHalfLifeToMultiplier(currentHalfLife)
            previousHalfLife = currentHalfLife
        }

        val scalar = 1.0 - decayScalar

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            val inputValue = abs(inputs[i])

            if (inputValue >= currentValue) {
                currentValue = inputValue.toDouble()
            } else {
                currentValue *= scalar
            }

            outputs[i] = currentValue.toFloat()
        }

        /*
         * When current gets close to zero, set current to zero to prevent FP underflow
         */
        if (currentValue < VERY_SMALL_FLOAT) {
            currentValue = 0.0
        }

        current.value = currentValue
    }
}
