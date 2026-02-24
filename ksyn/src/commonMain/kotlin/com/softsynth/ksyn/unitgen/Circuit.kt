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

import com.softsynth.ksyn.engine.SynthesisEngine
import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitPort

/**
 * Contains a list of units that are executed together.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
open class Circuit : UnitGenerator() {
    private val units = ArrayList<UnitGenerator>()
    private val portAliases = LinkedHashMap<String, UnitPort>()

    override fun generate() {
        for (unit in units) {
            unit.generate()
        }
    }

    /**
     * Call flattenOutputs on subunits. Flatten output ports so we don't output a changing signal
     * when stopped.
     */
    override fun flattenOutputs() {
        for (unit in units) {
            unit.flattenOutputs()
        }
    }

    /**
     * Call setEnabled on subunits.
     */
    override var isEnabled: Boolean = true
        set(enabled) {
            field = enabled
            for (unit in units) {
                unit.isEnabled = enabled
            }
        }

    override var synthesisEngine: SynthesisEngine? = null
        set(engine) {
            field = engine
            for (unit in units) {
                unit.synthesisEngine = engine
            }
        }

    /** Add a unit to the circuit. */
    fun add(unit: UnitGenerator) {
        units.add(unit)
        unit.setCircuit(this)
        // Propagate circuit properties down into subunits.
        unit.isEnabled = isEnabled
    }

    open fun usePreset(presetIndex: Int) {
    }

    /**
     * Add an alternate name for looking up a port.
     * @param port
     * @param alias
     */
    fun addPortAlias(port: UnitPort, alias: String) {
        // Store in a hash table by an alternate name.
        portAliases[alias.lowercase()] = port
    }

    /**
     * Case-insensitive search for a port by its name or alias.
     * @param portName
     * @return matching port or null
     */
    override fun getPortByName(portName: String): UnitPort? {
        var port = super.getPortByName(portName)
        if (port == null) {
            port = portAliases[portName.lowercase()]
        }
        return port
    }

    /**
     * The units are guaranteed to be in the same order
     * as they were added.
     *
     * @return an array of units that have been added to this circuit.
     */
    fun getUnits(): Array<UnitGenerator> {
        return units.toTypedArray()
    }
}
