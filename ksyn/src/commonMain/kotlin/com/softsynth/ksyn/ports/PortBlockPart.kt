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
import com.softsynth.ksyn.AudioBuffer
import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.engine.SynthesisEngine
import com.softsynth.ksyn.toSample

/**
 * Part of a multi-part port, for example, the left side of a stereo port.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
open class PortBlockPart internal constructor(
    internal val unitBlockPort: UnitBlockPort,
    defaultValue: AudioSample
) : ConnectableOutput, ConnectableInput {

    private val values = AudioBuffer(Synthesizer.FRAMES_PER_BLOCK)

    open fun getValues(): AudioBuffer = values

    private val connections = mutableListOf<PortBlockPart>()

    init {
        setValue(defaultValue)
    }

    fun get(): AudioSample = values[0]

    open fun getValue() : AudioSample = values[0]

    open fun setValue(value: AudioSample) {
        values.fill(value.toSample())
    }

    val isConnected: Boolean
        get() = connections.isNotEmpty()

    private fun addConnection(otherPart: PortBlockPart) {
        if (!connections.contains(otherPart)) {
            connections.add(otherPart)
        }
    }

    private fun removeConnection(otherPart: PortBlockPart) {
        connections.remove(otherPart)
    }

    private fun connectNow(otherPart: PortBlockPart) {
        addConnection(otherPart)
        otherPart.addConnection(this)
    }

    private fun disconnectNow(otherPart: PortBlockPart) {
        removeConnection(otherPart)
        otherPart.removeConnection(this)
    }

    private fun disconnectAllNow() {
        for (part in connections) {
            part.removeConnection(this)
        }
        connections.clear()
    }

    fun getConnection(i: Int): PortBlockPart = connections[i]

    val connectionCount: Int
        get() = connections.size

    /** Set all values to the last value. */
    internal fun flatten() {
        if (values.isNotEmpty()) {
            val lastValue = values.last()
            values.fill(lastValue, 0, values.size - 1)
        }
    }

    fun getPort(): UnitBlockPort = unitBlockPort

    private fun checkConnection(destination: PortBlockPart) {
        val sourceSynth: SynthesisEngine? = unitBlockPort.getSynthesisEngine()
        val destSynth: SynthesisEngine? = destination.unitBlockPort.getSynthesisEngine()
        if ((sourceSynth != destSynth) && (sourceSynth != null) && (destSynth != null)) {
            throw RuntimeException("Connection between units on different synths.")
        }
    }

    internal fun connect(destination: PortBlockPart) {
        checkConnection(destination)
        unitBlockPort.queueCommand { connectNow(destination) }
    }
    internal fun connect(destination: PortBlockPart, time: Double) {
        checkConnection(destination)
        unitBlockPort.scheduleCommand(time,
            { connectNow(destination) })
    }

    internal fun disconnect(destination: PortBlockPart) {
        unitBlockPort.queueCommand { disconnectNow(destination) }
    }
    internal fun disconnect(destination: PortBlockPart, time: Double) {
        unitBlockPort.scheduleCommand(time,
            { disconnectNow(destination) })
    }

    internal fun disconnectAll() {
        unitBlockPort.queueCommand { disconnectAllNow() }
    }

    override fun connect(other: ConnectableInput) {
        connect(other.portBlockPart)
    }

    override fun connect(other: ConnectableOutput) {
        other.connect(this)
    }

    override fun disconnect(other: ConnectableOutput) {
        other.disconnect(this)
    }

    override fun disconnect(other: ConnectableInput) {
        disconnect(other.portBlockPart)
    }

    /** To implement ConnectableInput */
    override val portBlockPart: PortBlockPart
        get() = this

    override fun pullData(frameCount: Long) {
        for (part in connections) {
            part.unitBlockPort.unitGenerator?.pullData(frameCount)
        }
    }
}
