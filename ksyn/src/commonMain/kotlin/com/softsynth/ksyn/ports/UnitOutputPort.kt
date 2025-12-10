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

package com.softsynth.ksyn.ports

import com.softsynth.ksyn.AudioSample
import com.softsynth.ksyn.KSyn
import com.softsynth.ksyn.unitgen.UnitSink

/**
 * Units write to their output port blocks. Other multiple connected input ports read from them.
 *
 * Converted to Kotlin Multiplatform.
 */
class UnitOutputPort(
    name: String = "Output",
    numParts: Int = 1,
    defaultValue: AudioSample = KSyn.ZERO
) : UnitBlockPort(numParts, name, defaultValue), ConnectableOutput, GettablePort {

    // Support for legacy JSyn constructor order (numParts, name)
    constructor(numParts: Int, name: String, defaultValue: AudioSample = KSyn.ZERO) : this(name, numParts, defaultValue)

    fun flatten() {
        for (part in parts) {
            part.flatten()
        }
    }

    // ==========================================
    // Connect (Immediate)
    // ==========================================

    fun connect(thisPartNum: Int, otherPort: UnitInputPort, otherPartNum: Int) {
        val source = parts[thisPartNum]
        // Accessing 'parts' is valid if UnitInputPort and UnitOutputPort are in the same package/module
        // or if parts is public. In KSyn UnitBlockPort, parts is public.
        val destination = otherPort.parts[otherPartNum]
        source.connect(destination)
    }

    fun connect(input: UnitInputPort) {
        connect(0, input, 0)
    }

    override fun connect(input: ConnectableInput) {
        // We can connect our primary part to the connectable input part
        parts[0].connect(input)
    }

    /**
     * Connect to a UnitSink (like LineOut).
     * Assumes UnitSink has an input port exposed via getInput().
     */
    fun connect(sink: UnitSink) {
        connect(0, sink.input!!, 0)
    }

    // ==========================================
    // Connect (Scheduled)
    // ==========================================

    fun connect(thisPartNum: Int, otherPort: UnitInputPort, otherPartNum: Int, time: Double) {
        scheduleCommand(time) {
            connect(thisPartNum, otherPort, otherPartNum)
        }
    }

    // ==========================================
    // Disconnect (Immediate)
    // ==========================================

    fun disconnect(thisPartNum: Int, otherPort: UnitInputPort, otherPartNum: Int) {
        val source = parts[thisPartNum]
        val destination = otherPort.parts[otherPartNum]
        source.disconnect(destination)
    }

    fun disconnect(otherPort: UnitInputPort) {
        disconnect(0, otherPort, 0)
    }

    override fun disconnect(input: ConnectableInput) {
        parts[0].disconnect(input)
    }

    // ==========================================
    // Disconnect (Scheduled)
    // ==========================================

    fun disconnect(thisPartNum: Int, otherPort: UnitInputPort, otherPartNum: Int, time: Double) {
        scheduleCommand(time) {
            disconnect(thisPartNum, otherPort, otherPartNum)
        }
    }

    // ==========================================
    // Interfaces / Helpers
    // ==========================================

    fun getConnectablePart(i: Int): ConnectableOutput {
        return parts[i]
    }
}
