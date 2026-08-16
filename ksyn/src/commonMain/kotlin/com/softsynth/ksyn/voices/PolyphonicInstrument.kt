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

package com.softsynth.ksyn.voices

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.unitgen.Circuit
import com.softsynth.ksyn.unitgen.Multiply
import com.softsynth.ksyn.unitgen.PassThrough
import com.softsynth.ksyn.unitgen.UnitSource
import com.softsynth.ksyn.unitgen.UnitVoice
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.toSample
import com.softsynth.ksyn.util.Instrument
import com.softsynth.ksyn.util.VoiceAllocator

/**
 * The API for this class is likely to change. Please comment on its usefulness.
 *
 * @author Phil Burk (C) 2011 Mobileer Inc
 */
open class PolyphonicInstrument(val synth: Synthesizer, val voices: Array<PitchedVoice>) : Circuit(), UnitSource, Instrument {
    private val mixer: Multiply
    private val pitchPassthrough: PassThrough
    private val voiceAllocator: OnOffAllocator<PitchedVoice>
    val amplitude: UnitInputPort
    val pitchOffset: UnitInputPort

    init {
        voiceAllocator = OnOffAllocator<PitchedVoice>(voices)
        mixer = Multiply()
        pitchPassthrough = PassThrough()
        add(pitchPassthrough)
        add(mixer)

        pitchOffset = pitchPassthrough.input
        addPort(pitchOffset, "PitchOffset")
        pitchOffset.setup(-2.0, 0.0, 2.0)

        amplitude = mixer.inputB
        addPort(amplitude, "Amplitude")
        amplitude.setup(0.0001, 0.4, 2.0)
        
        // Mix all the voices to one output using port mixing.
        for (voice in voices) {
            val unit = voice.getUnitGenerator()
            val wasEnabled = unit.isEnabled
            // This overrides the enabled property of the voice.
            add(unit)
            voice.getOutputPort().connect(mixer.inputA)
            val pitchPort = voice.getPitchPort()
            if (pitchPort != null) {
                pitchPassthrough.output.connect(pitchPort)
                pitchPort.isValueAdded = true
            }
            // restore
            unit.isEnabled = wasEnabled
        }

        exportAllInputPorts()
    }

    /**
     * Connect a PassThrough unit to the input ports of the voices so that they can be controlled
     * together using a single port. Note that this will prevent their individual use. So the
     * "Pitch" and "Amplitude" ports are excluded. Note that this method is a bit funky and is
     * likely to change.
     */
    fun exportAllInputPorts() {
        // Iterate through the ports.
        for (port in voices[0].getUnitGenerator().getPorts()) {
            if (port is UnitInputPort) {
                val voicePortName = port.name
                // FIXME Need better way to identify ports that are per note.
                if (voicePortName != "Pitch" && voicePortName != "Amplitude") {
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

    override fun noteOn(tag: Int, pitch: Double, amplitude: Double, timeStamp: TimeStamp?) {
        synth.queueCommand {
            val voice = voiceAllocator.on(tag)
            val ts: TimeStamp = (timeStamp ?: synth.currentTime) as TimeStamp
            voice.noteOn(pitch, amplitude, ts)
            voice.setPort("Range", 0.7.toSample(), ts)
        }
    }

    override fun noteOff(tag: Int, timeStamp: TimeStamp?) {
        synth.queueCommand {
            val voice: PitchedVoice? = voiceAllocator.off(tag)
            val ts: TimeStamp = (timeStamp ?: synth.currentTime) as TimeStamp
            voice?.noteOff(ts) // TODO use nonscheduled noteOff?
        }
    }

    override fun setPort(tag: Int, portName: String, value: Double, timeStamp: TimeStamp?) {
        synth.queueCommand {
            val voice = voiceAllocator.findVoice(tag)
            if (voice != null) {
                val ts: TimeStamp = (timeStamp ?: synth.currentTime) as TimeStamp
                voice.setPort(portName, value.toFloat(), ts)
            }
        }
    }

    override fun setPitchBend(tag: Int, value: Double, timeStamp: TimeStamp?) {
        synth.queueCommand {
            val voice = voiceAllocator.findVoice(tag)
            if (voice != null) {
                val ts: TimeStamp = (timeStamp ?: synth.currentTime) as TimeStamp
                voice.setPitchBend(value, ts)
            }
        }
    }

    override fun allNotesOff(timeStamp: TimeStamp?) {
        synth.queueCommand {
            voiceAllocator.allOff()
        }

        val ts: TimeStamp = (timeStamp ?: synth.currentTime) as TimeStamp
        for (voice in voices) {
            voice.noteOff(ts)
        }
    }

    // synchronized not strictly needed here since VoiceAllocator isn't explicitly synchronized,
    // but in a concurrent layout we'd leave it atomic checking state
    fun isOn(tag: Int): Boolean {
        return voiceAllocator.isOn(tag)
    }
}
