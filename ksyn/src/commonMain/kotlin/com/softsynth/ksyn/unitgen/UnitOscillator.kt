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

import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.ports.UnitVariablePort
import com.softsynth.ksyn.shared.time.TimeStamp

/**
 * Base class for all oscillators.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
abstract class UnitOscillator : UnitGenerator(), UnitVoice {
    /** Frequency in Hertz.  */
    var frequency: UnitInputPort
    var amplitude: UnitInputPort
    var phase: UnitVariablePort
    var output: UnitOutputPort

    /* Define Unit Ports used by connect() and set(). */
    init {
        addPort(UnitInputPort(PORT_NAME_FREQUENCY).also { frequency = it })
        frequency.setup(40.0, DEFAULT_FREQUENCY, 8000.0)
        addPort(UnitInputPort(PORT_NAME_AMPLITUDE, DEFAULT_AMPLITUDE).also { amplitude = it })
        addPort(UnitVariablePort(PORT_NAME_PHASE).also { phase = it })
        addPort(UnitOutputPort(PORT_NAME_OUTPUT).also { output = it })
    }

    override fun getOutputPort(): UnitOutputPort {
        return output
    }

    /**
     * Convert a frequency in Hertz to a phaseIncrement in the range -1.0 to +1.0
     */
    fun convertFrequencyToPhaseIncrement(freq: Double): Double {
        var phaseIncrement: Double
        try {
            phaseIncrement = freq * synthesisEngine!!.getInverseNyquist()
        } catch (e: NullPointerException) {
            throw NullPointerException(
                "Null Synth! You probably forgot to add this unit to the Synthesizer!"
            )
        }
        // Clip to range.
        phaseIncrement = if (phaseIncrement > 1.0) 1.0 else (if (phaseIncrement < -1.0)
            -1.0
        else
            phaseIncrement)
        return phaseIncrement
    }

    fun noteOn(freq: Double, ampl: Double) {
        frequency.set(freq)
        amplitude.set(ampl)
    }

    fun noteOff() {
        amplitude.set(0.0)
    }

    override fun noteOff(timeStamp: TimeStamp) {
        amplitude.set(0.0, timeStamp.time)
    }

    override fun noteOn(freq: Double, ampl: Double, timeStamp: TimeStamp) {
        frequency.set(freq, timeStamp.time)
        amplitude.set(ampl, timeStamp.time)
    }

    override fun usePreset(presetIndex: Int) {
    }

    companion object {
        const val DEFAULT_FREQUENCY: Double = 440.0
        const val DEFAULT_AMPLITUDE: Double = 1.0
    }
}
