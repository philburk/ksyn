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

package com.softsynth.ksyn.instruments

import com.softsynth.ksyn.data.DoubleTable
import com.softsynth.ksyn.math.PolynomialTableData
import com.softsynth.ksyn.math.T
import com.softsynth.ksyn.ports.UnitFunctionPort
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.Circuit
import com.softsynth.ksyn.unitgen.EnvelopeDAHDSR
import com.softsynth.ksyn.unitgen.FunctionEvaluator
import com.softsynth.ksyn.unitgen.Multiply
import com.softsynth.ksyn.unitgen.SineOscillator
import com.softsynth.ksyn.unitgen.UnitVoice
import com.softsynth.ksyn.util.VoiceDescription

/**
 * Waveshaping oscillator with envelopes.
 * 
 * @author Phil Burk (C) 2011 Mobileer Inc
 */
class WaveShapingVoice : Circuit(), UnitVoice {
    private val osc = SineOscillator()
    private val waveShaper = FunctionEvaluator()
    private val ampEnv = EnvelopeDAHDSR()
    private val rangeEnv = EnvelopeDAHDSR()
    private val frequencyScaler = Multiply()

    val range: UnitInputPort
    val frequency: UnitInputPort
    val amplitude: UnitInputPort
    val function: UnitFunctionPort
    val pitchModulation: UnitInputPort

    init {
        add(frequencyScaler)
        add(osc)
        add(waveShaper)
        add(rangeEnv)
        add(ampEnv)

        amplitude = ampEnv.amplitude
        addPort(amplitude, "Amplitude")
        
        range = osc.amplitude
        addPort(range, "Range")
        
        function = waveShaper.function
        addPort(function, "Function")
        
        frequency = frequencyScaler.inputA
        addPort(frequency, "Frequency")
        
        pitchModulation = frequencyScaler.inputB
        addPort(pitchModulation, "PitchMod")

        ampEnv.export(this, "Amp")
        rangeEnv.export(this, "Range")

        // Set the shared Chebyshev polynomial table
        function.set(chebyshevTable)

        // Connect units.
        osc.output.connect(rangeEnv.amplitude)
        rangeEnv.output.connect(waveShaper.input)
        ampEnv.output.connect(waveShaper.amplitude)
        frequencyScaler.output.connect(osc.frequency)

        // Set reasonable defaults for the ports.
        pitchModulation.setup(0.1, 1.0, 10.0)
        range.setup(0.1, 0.8, 1.0)
        frequency.setup(osc.frequency)
        amplitude.setup(0.0, 0.5, 1.0)

        // Make the circuit turn off when the envelope finishes to reduce CPU load.
        ampEnv.setupAutoDisable(this)

        usePreset(2)
    }

    override fun getOutputPort(): UnitOutputPort {
        return waveShaper.output
    }

    override fun noteOn(freq: Double, amp: Double, timeStamp: TimeStamp) {
        frequency.set(freq, timeStamp)
        amplitude.set(amp, timeStamp)
        ampEnv.input.on(timeStamp)
        rangeEnv.input.on(timeStamp)
    }

    override fun noteOff(timeStamp: TimeStamp) {
        ampEnv.input.off(timeStamp)
        rangeEnv.input.off(timeStamp)
    }

    override fun usePreset(presetIndex: Int) {
        val n = presetIndex % NUM_PRESETS
        when (n) {
            0 -> {
                ampEnv.attack.set(0.01)
                ampEnv.decay.set(0.2)
                ampEnv.release.set(1.0)
                rangeEnv.attack.set(0.01)
                rangeEnv.decay.set(0.2)
                rangeEnv.sustain.set(0.4)
                rangeEnv.release.set(1.0)
            }
            1 -> {
                ampEnv.attack.set(0.5)
                ampEnv.decay.set(0.3)
                ampEnv.release.set(0.2)
                rangeEnv.attack.set(0.03)
                rangeEnv.decay.set(0.2)
                rangeEnv.sustain.set(0.5)
                rangeEnv.release.set(1.0)
            }
            else -> {
                ampEnv.attack.set(0.1)
                ampEnv.decay.set(0.3)
                ampEnv.release.set(0.5)
                rangeEnv.attack.set(0.01)
                rangeEnv.decay.set(0.2)
                rangeEnv.sustain.set(0.9)
                rangeEnv.release.set(1.0)
            }
        }
    }

    companion object {
        private const val NUM_PRESETS = 3
        private const val CHEBYSHEV_ORDER = 11

        // Default Chebyshev polynomial table to share.
        private val chebyshevTable: DoubleTable by lazy {
            val chebData = PolynomialTableData(
                T(CHEBYSHEV_ORDER), 1024
            )
            DoubleTable(chebData.data)
        }
        
        private val presetNames = arrayOf("FastChebyshev", "SlowChebyshev", "BrightChebyshev")

        fun getVoiceDescription(): VoiceDescription {
            return MyVoiceDescription
        }
    }

    private object MyVoiceDescription : VoiceDescription("Waveshaping", presetNames) {
        override val voiceClassName: String
            get() = "com.softsynth.ksyn.instruments.WaveShapingVoice"
            
        private val tags = arrayOf("electronic", "waveshaping", "clean")

        override fun createUnitVoice(): UnitVoice {
            return WaveShapingVoice()
        }

        override fun getTags(presetIndex: Int): Array<String> {
            return tags
        }
    }
}
