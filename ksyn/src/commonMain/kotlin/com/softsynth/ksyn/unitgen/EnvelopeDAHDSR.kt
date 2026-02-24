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
 * limitations under the License.
 */

package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.engine.SynthesisEngine
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.toSample

/**
 * Six stage envelope similar to an ADSR. DAHDSR is like an ADSR but with an additional Delay stage
 * before the attack, and a Hold stage after the Attack. If Delay and Hold are both set to zero then
 * it will act like an ADSR. The envelope is triggered when the input goes above THRESHOLD. The
 * envelope is released when the input goes below THRESHOLD. The THRESHOLD is currently 0.01 but may
 * change so it would be best to use an input signal that went from 0 to 1. Mathematically an
 * exponential Release will never reach 0.0. But when it reaches -96 dB the DAHDSR just sets its
 * output to 0.0 and stops.
 *
 * @author Phil Burk (C) 2010 Mobileer Inc
 */
class EnvelopeDAHDSR : UnitGate() {

    /** Time in seconds for first stage of the envelope, before the attack. Typically zero. */
    val delay = UnitInputPort("Delay", 0.0)
    /** Time in seconds for the rising stage of the envelope to go from 0.0 to 1.0. */
    val attack = UnitInputPort("Attack", 0.1)
    /** Time in seconds for the plateau between the attack and decay stages. */
    val hold = UnitInputPort("Hold", 0.0)
    /** Time in seconds for the falling stage to go from 0 dB to -90 dB. */
    val decay = UnitInputPort("Decay", 0.2)
    /** Level for the sustain stage. */
    val sustain = UnitInputPort("Sustain", 0.5)
    /** Time in seconds to go from 0 dB to -90 dB. */
    val release = UnitInputPort("Release", 0.3)
    val amplitude = UnitInputPort("Amplitude", 1.0)

    private enum class State {
        IDLE, DELAYING, ATTACKING, HOLDING, DECAYING, SUSTAINING, RELEASING
    }

    private var state = State.IDLE
    private var countdown: Int = 0
    private var scaler: Double = 1.0
    private var level: Double = 0.0
    private var increment: Double = 0.0

    init {
        addPort(delay)
        delay.setup(0.0, 0.0, 2.0)
        delay.set(0, 0.0f)
        attack.set(0, 0.01f)
        hold.set(0, 0.0f)
        decay.set(0, 0.2f)
        sustain.set(0.5f)
        release.set(0.3f)
        amplitude.set(1.0f)
    }

    override fun generate() {
        val sustains = sustain.getValues()
        val amplitudes = amplitude.getValues()
        val outputs = output.getValues()

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            val triggered = input.checkGate(i)
            when (state) {
                State.IDLE -> {
                    outputs[i] = (level * amplitudes[i].toDouble()).toSample()
                    if (triggered) {
                        startDelay(i)
                    }
                }
                State.DELAYING -> {
                    outputs[i] = (level * amplitudes[i].toDouble()).toSample()
                    if (input.isOff) {
                        startRelease(i)
                    } else {
                        countdown -= 1
                        if (countdown <= 0) {
                            startAttack(i)
                        }
                    }
                }
                State.ATTACKING -> {
                    // Increment first so we can render fast attacks.
                    level += increment
                    if (level >= 1.0) {
                        level = 1.0
                        outputs[i] = (level * amplitudes[i].toDouble()).toSample()
                        startHold(i)
                    } else {
                        outputs[i] = (level * amplitudes[i].toDouble()).toSample()
                        if (input.isOff) {
                            startRelease(i)
                        }
                    }
                }
                State.HOLDING -> {
                    outputs[i] = amplitudes[i] // level is 1.0
                    countdown -= 1
                    if (countdown <= 0) {
                        startDecay(i)
                    } else if (input.isOff) {
                        startRelease(i)
                    }
                }
                State.DECAYING -> {
                    outputs[i] = (level * amplitudes[i].toDouble()).toSample()
                    level *= scaler // exponential decay
                    if (triggered) {
                        startDelay(i)
                    } else if (level < sustains[i].toDouble()) {
                        level = sustains[i].toDouble()
                        startSustain(i)
                    } else if (level < SynthesisEngine.DB96) {
                        input.checkAutoDisable()
                        startIdle()
                    } else if (input.isOff) {
                        startRelease(i)
                    }
                }
                State.SUSTAINING -> {
                    level = sustains[i].toDouble()
                    if (level < 0.0) level = 0.0
                    outputs[i] = (level * amplitudes[i].toDouble()).toSample()
                    if (triggered) {
                        startDelay(i)
                    } else if (input.isOff) {
                        startRelease(i)
                    }
                }
                State.RELEASING -> {
                    outputs[i] = (level * amplitudes[i].toDouble()).toSample()
                    level *= scaler // exponential decay
                    if (triggered) {
                        startDelay(i)
                    } else if (level < SynthesisEngine.DB96) {
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

    private fun startDelay(i: Int) {
        val delays = delay.getValues()
        val duration = delays[i].toDouble()
        if (duration <= 0.0) {
            startAttack(i)
        } else {
            state = State.DELAYING
            countdown = (duration * (synthesisEngine?.frameRate?.toDouble() ?: 44100.0)).toInt()
        }
    }

    private fun startAttack(i: Int) {
        val attacks = attack.getValues()
        val duration = attacks[i].toDouble()
        if (duration < MIN_DURATION) {
            level = 1.0
            startHold(i)
        } else {
            increment = (synthesisEngine?.framePeriod ?: 0.0) / duration
            state = State.ATTACKING
        }
    }

    private fun startHold(i: Int) {
        val holds = hold.getValues()
        val duration = holds[i].toDouble()
        if (duration <= 0.0) {
            startDecay(i)
        } else {
            state = State.HOLDING
            countdown = (duration * (synthesisEngine?.frameRate?.toDouble() ?: 44100.0)).toInt()
        }
    }

    private fun startDecay(i: Int) {
        val decays = decay.getValues()
        val duration = decays[i].toDouble()
        if (duration < MIN_DURATION) {
            startSustain(i)
        } else {
            scaler = synthesisEngine?.convertTimeToExponentialScaler(duration) ?: 1.0
            state = State.DECAYING
        }
    }

    private fun startSustain(i: Int) {
        state = State.SUSTAINING
    }

    private fun startRelease(i: Int) {
        val releases = release.getValues()
        var duration = releases[i].toDouble()
        if (duration < MIN_DURATION) {
            duration = MIN_DURATION
        }
        scaler = synthesisEngine?.convertTimeToExponentialScaler(duration) ?: 1.0
        state = State.RELEASING
    }

    /* UnitSource overrides handled by UnitGate */

    fun export(circuit: Circuit, prefix: String) {
        circuit.addPort(attack, prefix + attack.name)
        circuit.addPort(decay, prefix + decay.name)
        circuit.addPort(sustain, prefix + sustain.name)
        circuit.addPort(release, prefix + release.name)
    }

    companion object {
        private const val MIN_DURATION = 1.0 / 100000.0
    }
}
