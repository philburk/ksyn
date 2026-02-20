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
import com.softsynth.ksyn.ports.UnitOutputPort
import kotlin.math.PI
import kotlin.math.sin

/**
 * A versatile filter described in Hal Chamberlain's "Musical Applications of MicroProcessors".
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 * @see FilterLowPass
 * @see FilterHighPass
 * @see FilterFourPoles
 */
class FilterStateVariable : TunableFilter() {
    val amplitude = UnitInputPort("Amplitude", 1.0)
    val resonance = UnitInputPort("Resonance", 0.2)
    val lowPass = UnitOutputPort("LowPass")
    val bandPass = UnitOutputPort("BandPass")
    val highPass = UnitOutputPort("HighPass")

    private var freqInternal: Float = 0f
    private var previousFrequency: Double = Double.MAX_VALUE
    private var lowPassValue: Float = 0f
    private var bandPassValue: Float = 0f

    init {
        frequency.set(440.0)
        addPort(resonance)
        addPort(amplitude)
        addPort(lowPass)
        addPort(bandPass)
        addPort(highPass)
    }

    override fun generate() {
        val inputs = input.getValues()
        val outputs = output.getValues()
        val frequencies = frequency.getValues()
        val amplitudes = amplitude.getValues()
        val reses = resonance.getValues()
        val lows = lowPass.getValues()
        val highs = highPass.getValues()
        val bands = bandPass.getValues()

        val newFreq = frequencies[0].toDouble()
        if (newFreq != previousFrequency) {
            previousFrequency = newFreq
            freqInternal = (2.0 * sin(PI * newFreq * framePeriod)).toFloat()
        }

        // Local variables for loop
        var lp = lowPassValue
        var bp = bandPassValue
        val fi = freqInternal

        for (i in 0..<Synthesizer.FRAMES_PER_BLOCK) {
            lp = (fi * bp) + lp
            // Clip between -1 and +1
            if (lp < -1.0f) lp = -1.0f
            else if (lp > 1.0f) lp = 1.0f
            lows[i] = lp

            outputs[i] = lp * amplitudes[i]

            val hp = inputs[i] - (reses[i] * bp) - lp
            highs[i] = hp

            bp = (fi * hp) + bp
            bands[i] = bp
        }

        lowPassValue = lp
        bandPassValue = bp
    }
}
