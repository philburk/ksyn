/*
 * Copyright 2010 Phil Burk, Mobileer Inc
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

import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.Add
import com.softsynth.ksyn.unitgen.Circuit
import com.softsynth.ksyn.unitgen.EnvelopeDAHDSR
import com.softsynth.ksyn.unitgen.FilterLowPass
import com.softsynth.ksyn.unitgen.Multiply
import com.softsynth.ksyn.unitgen.SawtoothOscillatorBL
import com.softsynth.ksyn.unitgen.UnitVoice
import com.softsynth.ksyn.util.VoiceDescription

/**
 * Typical synthesizer voice with one oscillator and a biquad resonant filter. Modulate the amplitude and
 * filter using DAHDSR envelopes.
 *
 * @author Phil Burk (C) 2010 Mobileer Inc
 */
class SubtractiveSynthVoice : Circuit(), UnitVoice {
    private val osc = SawtoothOscillatorBL()
    private val filter = FilterLowPass()
    private val ampEnv = EnvelopeDAHDSR()
    private val filterEnv = EnvelopeDAHDSR()
    private val cutoffAdder = Add()
    private val frequencyScaler = Multiply()

    val amplitude: UnitInputPort
    val frequency: UnitInputPort
    
    /**
     * This scales the frequency value. You can use this to modulate a group of instruments using a
     * shared LFO and they will stay in tune.
     */
    val pitchModulation: UnitInputPort
    val cutoff: UnitInputPort
    val cutoffRange: UnitInputPort
    val Q: UnitInputPort

    init {
        add(frequencyScaler)
        add(osc)
        add(ampEnv)
        add(filterEnv)
        add(filter)
        add(cutoffAdder)

        filterEnv.output.connect(cutoffAdder.inputA)
        cutoffAdder.output.connect(filter.frequency)
        frequencyScaler.output.connect(osc.frequency)
        osc.output.connect(filter.input)
        filter.output.connect(ampEnv.amplitude)

        amplitude = osc.amplitude
        addPort(amplitude, "Amplitude")
        
        frequency = frequencyScaler.inputA
        addPort(frequency, "Frequency")
        
        pitchModulation = frequencyScaler.inputB
        addPort(pitchModulation, "PitchMod")
        
        cutoff = cutoffAdder.inputB
        addPort(cutoff, "Cutoff")
        
        cutoffRange = filterEnv.amplitude
        addPort(cutoffRange, "CutoffRange")
        
        Q = filter.Q
        addPort(Q, "Q") // Explicit name for Q if needed

        ampEnv.export(this, "Amp")
        filterEnv.export(this, "Filter")

        frequency.setup(osc.frequency)
        pitchModulation.setup(0.2, 1.0, 4.0)
        cutoff.setup(filter.frequency)
        cutoffRange.setup(filter.frequency)

        // Make the circuit turn off when the envelope finishes to reduce CPU load.
        ampEnv.setupAutoDisable(this)

        usePreset(0)
    }

    override fun noteOff(timeStamp: TimeStamp) {
        ampEnv.input.off(timeStamp)
        filterEnv.input.off(timeStamp)
    }

    override fun noteOn(freq: Double, ampl: Double, timeStamp: TimeStamp) {
        frequency.set(freq, timeStamp)
        amplitude.set(ampl, timeStamp)

        ampEnv.input.on(timeStamp)
        filterEnv.input.on(timeStamp)
    }

    override fun getOutputPort(): UnitOutputPort {
        return ampEnv.output
    }

    override fun usePreset(presetIndex: Int) {
        val n = presetIndex % presetNames.size
        when (n) {
            0 -> {
                ampEnv.attack.set(0.01)
                ampEnv.decay.set(0.2)
                ampEnv.release.set(1.0)
                cutoff.set(500.0)
                cutoffRange.set(500.0)
                filter.Q.set(1.0)
            }
            1 -> {
                ampEnv.attack.set(0.5)
                ampEnv.decay.set(0.3)
                ampEnv.release.set(0.2)
                cutoff.set(500.0)
                cutoffRange.set(500.0)
                filter.Q.set(3.0)
            }
            else -> {
                ampEnv.attack.set(0.1)
                ampEnv.decay.set(0.3)
                ampEnv.release.set(0.5)
                cutoff.set(2000.0)
                cutoffRange.set(500.0)
                filter.Q.set(2.0)
            }
        }
    }

    companion object {
        val presetNames = arrayOf("FastSaw", "SlowSaw", "BrightSaw")

        fun getVoiceDescription(): VoiceDescription {
            return MyVoiceDescription
        }
    }

    private object MyVoiceDescription : VoiceDescription("SubtractiveSynth", presetNames) {
        override val voiceClassName: String
            get() = "com.softsynth.ksyn.instruments.SubtractiveSynthVoice"
            
        private val tags = arrayOf("electronic", "filter", "clean")

        override fun createUnitVoice(): UnitVoice {
            return SubtractiveSynthVoice()
        }

        override fun getTags(presetIndex: Int): Array<String> {
            return tags
        }
    }
}
