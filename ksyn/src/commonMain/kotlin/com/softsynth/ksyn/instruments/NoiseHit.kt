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

package com.softsynth.ksyn.instruments

import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.Circuit
import com.softsynth.ksyn.unitgen.EnvelopeAttackDecay
import com.softsynth.ksyn.unitgen.PinkNoise
import com.softsynth.ksyn.unitgen.UnitVoice
import com.softsynth.ksyn.util.VoiceDescription

/**
 * Cheap synthetic cymbal sound.
 */
class NoiseHit : Circuit(), UnitVoice {
    private val ampEnv = EnvelopeAttackDecay()
    private val noise = PinkNoise()

    init {
        // Create unit generators.
        add(noise)
        add(ampEnv)
        noise.output.connect(ampEnv.amplitude)

        ampEnv.export(this, "Amp")

        // Make the circuit turn off when the envelope finishes to reduce CPU load.
        ampEnv.setupAutoDisable(this)

        usePreset(0)
    }

    override fun noteOff(timeStamp: TimeStamp) {
    }

    override fun noteOn(freq: Double, ampl: Double, timeStamp: TimeStamp) {
        noise.amplitude.set(ampl, timeStamp)
        ampEnv.input.trigger(timeStamp)
    }

    override fun getOutputPort(): UnitOutputPort {
        return ampEnv.output
    }

    override fun usePreset(presetIndex: Int) {
        val n = presetIndex % NUM_PRESETS
        when (n) {
            0 -> {
                ampEnv.attack.set(0.001)
                ampEnv.decay.set(0.1)
            }
            1 -> {
                ampEnv.attack.set(0.03)
                ampEnv.decay.set(1.4)
            }
            else -> {
                ampEnv.attack.set(0.9)
                ampEnv.decay.set(0.3)
            }
        }
    }

    companion object {
        private const val NUM_PRESETS = 3
        private val presetNames = arrayOf("ShortNoiseHit", "LongNoiseHit", "SlowNoiseHit")

        fun getVoiceDescription(): VoiceDescription {
            return MyVoiceDescription
        }
    }

    private object MyVoiceDescription : VoiceDescription("NoiseHit", presetNames) {
        override val voiceClassName: String
            get() = "com.softsynth.ksyn.instruments.NoiseHit"
            
        private val tags = arrayOf("electronic", "noise")

        override fun createUnitVoice(): UnitVoice {
            return NoiseHit()
        }

        override fun getTags(presetIndex: Int): Array<String> {
            return tags
        }
    }
}
