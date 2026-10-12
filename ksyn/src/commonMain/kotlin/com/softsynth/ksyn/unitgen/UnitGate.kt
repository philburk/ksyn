/*
 * Copyright 2012 Phil Burk, Mobileer Inc
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

import com.softsynth.ksyn.ports.UnitGatePort
import com.softsynth.ksyn.ports.UnitOutputPort

/**
 * Base class for other envelopes.
 * 
 * @author Phil Burk (C) 2012 Mobileer Inc
 */
abstract class UnitGate : UnitGenerator(), UnitSource, SupportsAutoDisable {
    /**
     * Input that triggers the envelope. Use amplitude port if you want to connect a signal to be
     * modulated by the envelope.
     */
    val input = UnitGatePort("Input")
    val output = UnitOutputPort("Output")

    init {
        addPort(input)
        addPort(output)
    }

    override fun getOutputPort(): UnitOutputPort {
        return output
    }

    /**
     * Specify a unit to be disabled when the envelope finishes.
     *
     * @param unit the UnitGenerator to disable. Defaults to this envelope.
     */
    override fun setupAutoDisable(unit: UnitGenerator) {
        input.setupAutoDisable(unit)
    }

    override val isAutoDisableEnabled: Boolean
        get() = input.isAutoDisableEnabled

    override fun setAutoDisableEnabled(enabled: Boolean) {
        input.setAutoDisableEnabled(enabled)
    }

}
