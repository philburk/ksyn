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

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.unitgen.KSYN_BLOCK_SIZE
import com.softsynth.ksyn.unitgen.UnitGenerator

/**
 * Handles the mixing logic for a single channel (Part) of a UnitInputPort.
 * A UnitInputPort has an array of these, one for each part (e.g. Left/Right).
 */
class InputMixingBlockPart(
    private val unitInputPort: UnitInputPort,
    defaultValue: Double
) : PortBlockPart(unitInputPort, defaultValue) {

    // Internal buffer for summing inputs.
    // In KSyn, we use FloatArray for audio data (SIMD friendly).
    // TODO private val mixer = FloatArray(KSYN_BLOCK_SIZE)
    private val mixer = DoubleArray(Synthesizer.FRAMES_PER_BLOCK)

    // Cache the single value for scalar access
    private var current: Double = defaultValue

    override fun getValue(): Double {
        return current
    }

    override fun setValue(value: Double) {
        current = value
        super.setValue(value)
    }

    override fun getValues(): DoubleArray {
        val numConnections = connectionCount
        val result: DoubleArray

        if (numConnections == 0) {
            // No connection so just use our own data.
            result = super.getValues()
        } else {
            // Mix all of the connected ports.
            var inputs: DoubleArray
            var jCon = 0

            // Prime the mixer
            if (unitInputPort.isValueAdded) {
                // ADD mode: Start with local values (e.g. set())
                inputs = super.getValues()
                inputs.copyInto(mixer)
                jCon = 0
            } else {
                // OVERRIDE mode: Start with first connection, ignore local set()
                val otherPart = getConnection(0)
                inputs = otherPart.getValues()
                inputs.copyInto(mixer)
                jCon = 1
            }

            // Mix in remaining inputs
            for (i in jCon until numConnections) {
                val otherPart = getConnection(i)
                inputs = otherPart.getValues()
                for (k in mixer.indices) {
                    mixer[k] += inputs[k]
                }
            }
            result = mixer
        }

        current = result[0]
        return result
    }

    private fun printIndentation(level: Int) {
        repeat(level) {
            print("    ")
        }
    }

    private fun portToString(port: UnitPort): String {
        // UnitGenerator might be null if detached, handle safely
        val ugenName = port.unitGenerator?.let { it::class.simpleName } ?: "Unattached"
        return "$ugenName.${port.name}"
    }

    fun printConnections(level: Int) {
        val count = connectionCount
        for (i in 0 until count) {
            val part = getConnection(i)

            printIndentation(level)

            // Note: In KSyn, port is a property of PortBlockPart
            println("${portToString(getPort())} <--- ${portToString(part.getPort())}")

            // Recursively print upstream connections
            part.getPort().unitGenerator?.printConnections(level + 1)
        }
    }
}
