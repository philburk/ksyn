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

import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.Add
import com.softsynth.ksyn.unitgen.Circuit
import com.softsynth.ksyn.unitgen.EnvelopeAttackDecay
import com.softsynth.ksyn.unitgen.Multiply
import com.softsynth.ksyn.unitgen.PassThrough
import com.softsynth.ksyn.unitgen.SineOscillator
import com.softsynth.ksyn.unitgen.SineOscillatorPhaseModulated
import com.softsynth.ksyn.unitgen.UnitVoice
import com.softsynth.ksyn.util.VoiceDescription

/**
 * Drum instruments using 2 Operator FM.
 * 
 * @author Phil Burk (C) 2011 Mobileer Inc
 */
class DrumWoodFM : Circuit(), UnitVoice {
    // Declare units and ports.
    private val ampEnv = EnvelopeAttackDecay()
    private val carrierOsc = SineOscillatorPhaseModulated()
    private val modEnv = EnvelopeAttackDecay()
    private val modOsc = SineOscillator()
    private val freqDistributor = PassThrough()
    private val modSummer = Add()
    private val frequencyMultiplier = Multiply()

    val mcratio: UnitInputPort
    val index: UnitInputPort
    val modRange: UnitInputPort
    val frequency: UnitInputPort

    init {
        // Create unit generators.
        add(carrierOsc)
        add(freqDistributor)
        add(modSummer)
        add(ampEnv)
        add(modEnv)
        add(modOsc)
        add(frequencyMultiplier)

        mcratio = frequencyMultiplier.inputB
        addPort(mcratio, "MCRatio")
        
        index = modSummer.inputA
        addPort(index, "Index")
        
        modRange = modEnv.amplitude
        addPort(modRange, "ModRange")
        
        frequency = freqDistributor.input
        addPort(frequency, "Frequency")

        ampEnv.export(this, "Amp")
        modEnv.export(this, "Mod")

        freqDistributor.output.connect(carrierOsc.frequency)
        freqDistributor.output.connect(frequencyMultiplier.inputA)

        carrierOsc.output.connect(ampEnv.amplitude)
        modEnv.output.connect(modSummer.inputB)
        modSummer.output.connect(modOsc.amplitude)
        modOsc.output.connect(carrierOsc.modulation)
        frequencyMultiplier.output.connect(modOsc.frequency)

        // Make the circuit turn off when the envelope finishes to reduce CPU load.
        ampEnv.setupAutoDisable(this)

        usePreset(0)
    }

    override fun noteOff(timeStamp: TimeStamp) {
        // Attack-decay instruments don't need noteOff typically.
    }

    override fun noteOn(freq: Double, ampl: Double, timeStamp: TimeStamp) {
        carrierOsc.amplitude.set(ampl, timeStamp)
        ampEnv.input.trigger(timeStamp)
        modEnv.input.trigger(timeStamp)
    }

    override fun getOutputPort(): UnitOutputPort {
        return ampEnv.output
    }

    override fun usePreset(presetIndex: Int) {
        mcratio.setup(0.001, 0.6875, 20.0)
        ampEnv.attack.setup(0.001, 0.005, 8.0)
        modEnv.attack.setup(0.001, 0.005, 8.0)

        val n = presetIndex % NUM_PRESETS
        when (n) {
            0 -> {
                ampEnv.decay.setup(0.001, 0.293, 8.0)
                modEnv.decay.setup(0.001, 0.07, 8.0)
                frequency.setup(0.0, 349.0, 3000.0)
                index.setup(0.001, 0.05, 10.0)
                modRange.setup(0.001, 0.4, 10.0)
            }
            else -> {
                ampEnv.decay.setup(0.001, 0.12, 8.0)
                modEnv.decay.setup(0.001, 0.06, 8.0)
                frequency.setup(0.0, 1400.0, 3000.0)
                index.setup(0.001, 0.16, 10.0)
                modRange.setup(0.001, 0.17, 10.0)
            }
        }
    }

    companion object {
        private const val NUM_PRESETS = 3
        private val presetNames = arrayOf("WoodBlockFM", "ClaveFM")

        fun getVoiceDescription(): VoiceDescription {
            return MyVoiceDescription
        }
    }

    private object MyVoiceDescription : VoiceDescription("DrumWoodFM", presetNames) {
        override val voiceClassName: String
            get() = "com.softsynth.ksyn.instruments.DrumWoodFM"
            
        private val tags = arrayOf("electronic", "drum")

        override fun createUnitVoice(): UnitVoice {
            return DrumWoodFM()
        }

        override fun getTags(presetIndex: Int): Array<String> {
            return tags
        }
    }
}
