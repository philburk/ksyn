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

import com.softsynth.ksyn.math.AudioMath.semitonesToFrequencyScaler
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.Add
import com.softsynth.ksyn.unitgen.Circuit
import com.softsynth.ksyn.unitgen.EnvelopeDAHDSR
import com.softsynth.ksyn.unitgen.FilterFourPoles
import com.softsynth.ksyn.unitgen.MorphingOscillatorBL
import com.softsynth.ksyn.unitgen.Multiply
import com.softsynth.ksyn.unitgen.UnitGenerator
import com.softsynth.ksyn.unitgen.UnitVoice
import com.softsynth.ksyn.util.VoiceDescription

/**
 * Synthesizer voice with two morphing oscillators and a four-pole resonant filter.
 * Modulate the amplitude and filter using DAHDSR envelopes.
 */
class DualOscillatorSynthVoice : Circuit(), UnitVoice {
    private val frequencyMultiplier = Multiply()
    private val amplitudeMultiplier = Multiply()
    private val detuneScaler1 = Multiply()
    private val detuneScaler2 = Multiply()
    private val amplitudeBoost = Multiply()
    private val osc1 = MorphingOscillatorBL()
    private val osc2 = MorphingOscillatorBL()
    private val filter = FilterFourPoles()
    private val ampEnv = EnvelopeDAHDSR()
    private val filterEnv = EnvelopeDAHDSR()
    private val cutoffAdder = Add()

    val amplitude: UnitInputPort
    val frequency: UnitInputPort
    
    /**
     * This scales the frequency value. You can use this to modulate a group of instruments using a
     * shared LFO and they will stay in tune. Set to 1.0 for no modulation.
     */
    val frequencyScaler: UnitInputPort
    val oscShape1: UnitInputPort
    val oscShape2: UnitInputPort
    val cutoff: UnitInputPort
    val filterEnvDepth: UnitInputPort
    val Q: UnitInputPort

    init {
        add(frequencyMultiplier)
        add(amplitudeMultiplier)
        add(amplitudeBoost)
        add(detuneScaler1)
        add(detuneScaler2)
        add(osc1)
        add(osc2)
        add(ampEnv)
        add(filterEnv)
        add(filter)
        add(cutoffAdder)

        filterEnv.output.connect(cutoffAdder.inputA)
        cutoffAdder.output.connect(filter.frequency)
        frequencyMultiplier.output.connect(detuneScaler1.inputA)
        frequencyMultiplier.output.connect(detuneScaler2.inputA)
        detuneScaler1.output.connect(osc1.frequency)
        detuneScaler2.output.connect(osc2.frequency)
        osc1.output.connect(amplitudeMultiplier.inputA) // mix oscillators
        osc2.output.connect(amplitudeMultiplier.inputA)
        amplitudeMultiplier.output.connect(filter.input)
        filter.output.connect(amplitudeBoost.inputA)
        amplitudeBoost.output.connect(ampEnv.amplitude)

        amplitude = amplitudeMultiplier.inputB
        addPort(amplitude, UnitGenerator.PORT_NAME_AMPLITUDE)
        
        frequency = frequencyMultiplier.inputA
        addPort(frequency, UnitGenerator.PORT_NAME_FREQUENCY)
        
        oscShape1 = osc1.shape
        addPort(oscShape1, "OscShape1")
        
        oscShape2 = osc2.shape
        addPort(oscShape2, "OscShape2")

        cutoff = cutoffAdder.inputB
        addPort(cutoff, UnitGenerator.PORT_NAME_CUTOFF)
        addPortAlias(cutoff, UnitGenerator.PORT_NAME_TIMBRE)
        
        Q = filter.Q
        addPort(Q, "Q")
        
        frequencyScaler = frequencyMultiplier.inputB
        addPort(frequencyScaler, UnitGenerator.PORT_NAME_FREQUENCY_SCALER)
        
        filterEnvDepth = filterEnv.amplitude
        addPort(filterEnvDepth, "FilterEnvDepth")

        filterEnv.export(this, "Filter")
        ampEnv.export(this, "Amp")

        frequency.setup(osc1.frequency)
        frequencyScaler.setup(0.2, 1.0, 4.0)
        cutoff.setup(filter.frequency)
        
        // Allow negative filter sweeps
        filterEnvDepth.setup(-4000.0, 2000.0, 4000.0)

        // set amplitudes slightly different so that they never entirely cancel
        osc1.amplitude.set(0.5)
        osc2.amplitude.set(0.4)
        
        // Make the circuit turn off when the envelope finishes to reduce CPU load.
        ampEnv.setupAutoDisable(this)
        
        // Add named port for mapping pressure.
        amplitudeBoost.inputB.setup(1.0, 1.0, 4.0)
        addPortAlias(amplitudeBoost.inputB, UnitGenerator.PORT_NAME_PRESSURE)

        usePreset(0)
    }

    /**
     * The first oscillator will be tuned UP by semitoneOffset/2.
     * The second oscillator will be tuned DOWN by semitoneOffset/2.
     * @param semitoneOffset
     */
    private fun setDetunePitch(semitoneOffset: Double) {
        val halfOffset = semitoneOffset * 0.5
        setDetunePitch1(halfOffset)
        setDetunePitch2(-halfOffset)
    }

    /**
     * Set the detuning for osc1 in semitones.
     * @param semitoneOffset
     */
    private fun setDetunePitch1(semitoneOffset: Double) {
        val scale = semitonesToFrequencyScaler(semitoneOffset)
        detuneScaler1.inputB.set(scale)
    }

    /**
     * Set the detuning for osc2 in semitones.
     * @param semitoneOffset
     */
    private fun setDetunePitch2(semitoneOffset: Double) {
        val scale = semitonesToFrequencyScaler(semitoneOffset)
        detuneScaler2.inputB.set(scale)
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

    // Reset to basic voice.
    fun reset() {
        osc1.shape.set(0.0)
        osc2.shape.set(0.0)
        ampEnv.attack.set(0.005)
        ampEnv.decay.set(0.2)
        ampEnv.sustain.set(0.5)
        ampEnv.release.set(1.0)
        filterEnv.attack.set(0.01)
        filterEnv.decay.set(0.6)
        filterEnv.sustain.set(0.4)
        filterEnv.release.set(1.0)
        cutoff.set(500.0)
        filterEnvDepth.set(3000.0)
        
        // Note: FilterFourPoles does not currently have `reset` implemented in KSyn.
        // Assuming defaults are enough for initialization here.
        filter.Q.set(3.9)
        setDetunePitch(0.02)
    }

    override fun usePreset(presetIndex: Int) {
        reset() // start from known configuration
        val n = presetIndex % presetNames.size
        when (n) {
            0 -> {}
            1 -> {
                ampEnv.attack.set(0.1)
                ampEnv.decay.set(0.9)
                ampEnv.sustain.set(0.1)
                ampEnv.release.set(0.1)
                cutoff.set(500.0)
                filterEnvDepth.set(500.0)
                filter.Q.set(3.0)
            }
            2 -> {
                ampEnv.attack.set(0.1)
                ampEnv.decay.set(0.3)
                ampEnv.release.set(0.5)
                cutoff.set(2000.0)
                filterEnvDepth.set(500.0)
                filter.Q.set(2.0)
            }
            3 -> {
                osc1.shape.set(-0.9)
                osc2.shape.set(-0.8)
                ampEnv.attack.set(0.3)
                ampEnv.decay.set(0.8)
                ampEnv.release.set(0.2)
                filterEnv.sustain.set(0.7)
                cutoff.set(500.0)
                filterEnvDepth.set(500.0)
                filter.Q.set(3.0)
            }
            4 -> {
                osc1.shape.set(1.0)
                osc2.shape.set(0.0)
            }
            5 -> {
                osc1.shape.set(1.0)
                setDetunePitch1(0.0)
                osc2.shape.set(0.9)
                setDetunePitch1(7.0)
            }
            6 -> {
                osc1.shape.set(0.6)
                osc2.shape.set(-0.2)
                setDetunePitch1(0.01)
                ampEnv.attack.set(0.005)
                ampEnv.decay.set(0.09)
                ampEnv.sustain.set(0.0)
                ampEnv.release.set(1.0)
                filterEnv.attack.set(0.005)
                filterEnv.decay.set(0.1)
                filterEnv.sustain.set(0.4)
                filterEnv.release.set(1.0)
                cutoff.set(2000.0)
                filterEnvDepth.set(5000.0)
                filter.Q.set(7.02)
            }
        }
    }

    companion object {
        private val presetNames = arrayOf(
            "FastSaw", "SlowSaw", "BrightSaw",
            "SoftSine", "SquareSaw", "SquareFifth",
            "Blip"
        )

        fun getVoiceDescription(): VoiceDescription {
            return MyVoiceDescription
        }
    }

    private object MyVoiceDescription : VoiceDescription("DualOscillatorSynthVoice", presetNames) {
        override val voiceClassName: String
            get() = "com.softsynth.ksyn.instruments.DualOscillatorSynthVoice"
            
        private val tags = arrayOf("electronic", "filter", "analog", "subtractive")

        override fun createUnitVoice(): UnitVoice {
            return DualOscillatorSynthVoice()
        }

        override fun getTags(presetIndex: Int): Array<String> {
            return tags
        }
    }
}
