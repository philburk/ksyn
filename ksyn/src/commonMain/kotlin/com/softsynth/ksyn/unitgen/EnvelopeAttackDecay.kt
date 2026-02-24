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

package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.engine.SynthesisEngine
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.toSample

/**
 * Two stage Attack/Decay envelope that is triggered by an input level greater than THRESHOLD. This
 * does not sustain.
 *
 * @author Phil Burk (C) 2011 Mobileer Inc
 */
class EnvelopeAttackDecay : UnitGate() {

    /**
     * Time in seconds for the rising stage of the envelope to go from 0.0 to 1.0. The attack is a
     * linear ramp.
     */
    val attack = UnitInputPort("Attack")

    /**
     * Time in seconds for the falling stage to go from 0 dB to -90 dB.
     */
    val decay = UnitInputPort("Decay")

    val amplitude = UnitInputPort("Amplitude", 1.0)

    private enum class State {
        IDLE, ATTACKING, DECAYING
    }

    private var state = State.IDLE
    private var scaler: Double = 1.0
    private var level: Double = 0.0
    private var increment: Double = 0.0

    init {
        addPort(attack)
        attack.setup(0.001, 0.05, 8.0)
        addPort(decay)
        decay.setup(0.001, 0.2, 8.0)
        addPort(amplitude)
        startIdle()
    }

    override fun generate() {
        val amplitudes = amplitude.getValues()
        val outputs = output.getValues()

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            val triggered = input.checkGate(i)
            when (state) {
                State.IDLE -> {
                    outputs[i] = level.toSample()
                    if (triggered) {
                        startAttack(i)
                    }
                }
                State.ATTACKING -> {
                    // Increment first so we can render fast attacks.
                    level += increment
                    if (level >= 1.0) {
                        level = 1.0
                        outputs[i] = (level * amplitudes[i].toDouble()).toSample()
                        startDecay(i)
                    } else {
                        outputs[i] = (level * amplitudes[i].toDouble()).toSample()
                    }
                }
                State.DECAYING -> {
                    outputs[i] = (level * amplitudes[i].toDouble()).toSample()
                    level *= scaler
                    if (triggered) {
                        startAttack(i)
                    } else if (level < SynthesisEngine.DB90) {
                        input.checkAutoDisable()
                        startIdle()
                    }
                }
            }
        }
    }

    private fun startIdle() {
        state = State.IDLE
        level = 0.0
    }

    private fun startAttack(i: Int) {
        val attacks = attack.getValues()
        val duration = attacks[i].toDouble()
        if (duration < MIN_DURATION) {
            level = 1.0
            startDecay(i)
        } else {
            // assume going from 0.0 to 1.0 even if retriggering
            increment = (synthesisEngine?.framePeriod ?: 0.0) / duration
            state = State.ATTACKING
        }
    }

    private fun startDecay(i: Int) {
        val decays = decay.getValues()
        val duration = decays[i].toDouble()
        if (duration < MIN_DURATION) {
            startIdle()
        } else {
            scaler = synthesisEngine?.convertTimeToExponentialScaler(duration) ?: 1.0
            state = State.DECAYING
        }
    }

    fun export(circuit: Circuit, prefix: String) {
        circuit.addPort(attack, prefix + attack.name)
        circuit.addPort(decay, prefix + decay.name)
    }

    companion object {
        const val THRESHOLD = 0.01
        private const val MIN_DURATION = 1.0 / 100000.0
    }
}
