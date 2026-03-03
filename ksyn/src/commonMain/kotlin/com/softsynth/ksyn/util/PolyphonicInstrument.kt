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

package com.softsynth.ksyn.util

import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.unitgen.Circuit
import com.softsynth.ksyn.unitgen.Multiply
import com.softsynth.ksyn.unitgen.PassThrough
import com.softsynth.ksyn.unitgen.UnitSource
import com.softsynth.ksyn.unitgen.UnitVoice
import com.softsynth.ksyn.shared.time.TimeStamp

/**
 * The API for this class is likely to change. Please comment on its usefulness.
 *
 * @author Phil Burk (C) 2011 Mobileer Inc
 */
open class PolyphonicInstrument(val voices: Array<UnitVoice>) : Circuit(), UnitSource, Instrument {
    private val mixer: Multiply
    private val voiceAllocator: VoiceAllocator
    val amplitude: UnitInputPort

    init {
        voiceAllocator = VoiceAllocator(voices)
        mixer = Multiply()
        add(mixer)
        
        // Mix all the voices to one output.
        for (voice in voices) {
            val unit = voice.getUnitGenerator()
            val wasEnabled = unit.isEnabled
            // This overrides the enabled property of the voice.
            add(unit)
            voice.getOutputPort().connect(mixer.inputA)
            // restore
            unit.isEnabled = wasEnabled
        }

        amplitude = mixer.inputB
        addPort(amplitude, "Amplitude")
        amplitude.setup(0.0001, 0.4, 2.0)
        exportAllInputPorts()
    }

    /**
     * Connect a PassThrough unit to the input ports of the voices so that they can be controlled
     * together using a single port. Note that this will prevent their individual use. So the
     * "Frequency" and "Amplitude" ports are excluded. Note that this method is a bit funky and is
     * likely to change.
     */
    fun exportAllInputPorts() {
        // Iterate through the ports.
        for (port in voices[0].getUnitGenerator().getPorts()) {
            if (port is UnitInputPort) {
                val voicePortName = port.name
                // FIXME Need better way to identify ports that are per note.
                if (voicePortName != "Frequency" && voicePortName != "Amplitude") {
                    exportNamedInputPort(voicePortName)
                }
            }
        }
    }

    /**
     * Create a UnitInputPort for the circuit that is connected to the named port on each voice
     * through a PassThrough unit. This allows you to control all of the voices at once.
     *
     * @param portName
     * @see exportAllInputPorts
     */
    fun exportNamedInputPort(portName: String) {
        var voicePort: UnitInputPort? = null
        val fanout = PassThrough()
        for (voice in voices) {
            voicePort = voice.getUnitGenerator().getPortByName(portName) as? UnitInputPort
            voicePort?.let {
                fanout.output.connect(it)
            }
        }
        if (voicePort != null) {
            addPort(fanout.input, portName)
            fanout.input.setup(voicePort)
        }
    }

    override fun getOutputPort(): UnitOutputPort {
        return mixer.output
    }

    override fun usePreset(presetIndex: Int) {
        // Apply preset to all voices.
        for (voice in voices) {
            voice.usePreset(presetIndex)
        }
        // Then copy values from first voice to instrument.
        for (port in voices[0].getUnitGenerator().getPorts()) {
            if (port is UnitInputPort) {
                // FIXME Need better way to identify ports that are per note.
                val fanPort = getPortByName(port.name) as? UnitInputPort
                if (fanPort != null && fanPort !== amplitude) {
                    fanPort.set(port.get(0))
                }
            }
        }
    }

    override fun usePreset(
        presetIndex: Int,
        timeStamp: TimeStamp?
    ) {
        usePreset(presetIndex)
    }

    override fun noteOn(tag: Int, frequency: Double, amplitude: Double, timeStamp: TimeStamp?) {
        voiceAllocator.noteOn(tag, frequency, amplitude, timeStamp)
    }

    override fun noteOff(tag: Int, timeStamp: TimeStamp?) {
        voiceAllocator.noteOff(tag, timeStamp)
    }

    override fun setPort(tag: Int, portName: String, value: Double, timeStamp: TimeStamp?) {
        voiceAllocator.setPort(tag, portName, value, timeStamp)
    }

    override fun allNotesOff(timeStamp: TimeStamp?) {
        voiceAllocator.allNotesOff(timeStamp)
    }

    // synchronized not strictly needed here since VoiceAllocator isn't explicitly synchronized,
    // but in a concurrent layout we'd leave it atomic checking state
    fun isOn(tag: Int): Boolean {
        return voiceAllocator.isOn(tag)
    }
}
