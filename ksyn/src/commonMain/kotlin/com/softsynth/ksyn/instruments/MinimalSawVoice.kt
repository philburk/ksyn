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
import com.softsynth.ksyn.unitgen.Circuit
import com.softsynth.ksyn.unitgen.EnvelopeDAHDSR
import com.softsynth.ksyn.unitgen.PitchToFrequency
import com.softsynth.ksyn.unitgen.SawtoothOscillatorBL
import com.softsynth.ksyn.voices.PitchedVoice
import com.softsynth.ksyn.voices.VoiceDescription

/**
 * Typical synthesizer voice with one sawtooth oscillator.
 * Modulate the amplitude using a DAHDSR envelope.
 *
 * @author Phil Burk (C) 2026 Mobileer Inc
 */
class MinimalSawVoice : Circuit(), PitchedVoice {
    private val p2f = PitchToFrequency()
    private val osc = SawtoothOscillatorBL()
    private val ampEnv = EnvelopeDAHDSR()

    val pitch: UnitInputPort
    val amplitude: UnitInputPort

    init {
        add(p2f)
        add(osc)
        add(ampEnv)

        p2f.output.connect(osc.frequency)
        osc.output.connect(ampEnv.amplitude)

        // Use the pitchToFrequency input as the pitch input port.
        pitch = p2f.input
        // Set isValueAdded so we can modulate the pitch at audio rate.
        pitch.isValueAdded = true
        addPort(pitch, "Pitch")

        amplitude = osc.amplitude
        addPort(amplitude, "Amplitude")

        // Optionally export the ADSR ports for external use.
        ampEnv.export(this, "Amp")

        pitch.setup(0.0, 60.0, 128.0)

        // Make the circuit turn off when the envelope finishes to reduce CPU load.
        ampEnv.setupAutoDisable(this)

        usePreset(0)
    }

    override fun noteOff(timeStamp: TimeStamp) {
        ampEnv.input.off(timeStamp)
    }

    override fun noteOn(pitch: Double, amplitude: Double, timeStamp: TimeStamp) {
        p2f.input.set(pitch, timeStamp)
        this.amplitude.set(amplitude, timeStamp)
        ampEnv.input.on(timeStamp)
    }

    override fun getOutputPort(): UnitOutputPort {
        return ampEnv.output
    }

    override fun getPitchPort(): UnitInputPort? {
        return pitch
    }

    override fun usePreset(presetIndex: Int) {
        val n = presetIndex % presetNames.size
        when (n) {
            0 -> {
                ampEnv.attack.set(0.01)
                ampEnv.decay.set(0.2)
                ampEnv.release.set(1.0)
            }
            1 -> {
                ampEnv.attack.set(0.5)
                ampEnv.decay.set(0.3)
                ampEnv.release.set(0.2)
            }
            else -> {
                ampEnv.attack.set(0.1)
                ampEnv.decay.set(0.3)
                ampEnv.release.set(0.5)
            }
        }
    }

    companion object {
        val presetNames = arrayOf("FastSaw", "SlowSaw")

        fun getVoiceDescription(): VoiceDescription {
            return MyVoiceDescription
        }
    }

    private object MyVoiceDescription : VoiceDescription("MinimalSawSynth", presetNames) {
        override val voiceClassName: String
            get() = "com.softsynth.ksyn.instruments.MinimalSawSynth"

        private val tags = arrayOf("electronic", "clean")

        override fun createPitchedVoice(): PitchedVoice {
            return MinimalSawVoice()
        }

        override fun getTags(presetIndex: Int): Array<String> {
            return tags
        }
    }
}
