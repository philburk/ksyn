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

import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.UnitGenerator

open class UnitGatePort(name: String) : UnitInputPort(1, name) {
    var isAutoDisableEnabled = false
        private set
    private var triggered = false
    var isOff = true
        private set
    private var gatedUnit: UnitGenerator? = null

    fun on() {
        setOn(true)
    }

    fun on(timeStamp: TimeStamp) {
        setOn(true, timeStamp)
    }

    fun off() {
        setOn(false)
    }

    fun off(timeStamp: TimeStamp) {
        setOn(false, timeStamp)
    }

    private fun setOn(on: Boolean) {
        queueCommand {
            setOnInternal(on)
        }
    }

    private fun setOn(on: Boolean, timeStamp: TimeStamp) {
        scheduleCommand(timeStamp.time) {
            setOnInternal(on)
        }
    }

    private fun setOnInternal(on: Boolean) {
        if (on) {
            triggerInternal()
        }
        setValueInternal(if (on) 1.0f else 0.0f)
    }

    private fun triggerInternal() {
        getGatedUnit().isEnabled = true
        triggered = true
    }

    fun trigger() {
        queueCommand {
            triggerInternal()
        }
    }

    fun trigger(timeStamp: TimeStamp) {
        scheduleCommand(timeStamp.time) {
            triggerInternal()
        }
    }

    /**
     * This is called by UnitGenerators. It sets the off value that can be tested using isOff().
     *
     * @param i
     * @return true if triggered by a positive edge.
     */
    fun checkGate(i: Int): Boolean {
        val inputs = getValues()
        var result = triggered
        triggered = false
        if (isOff) {
            if (inputs[i].toDouble() >= THRESHOLD) {
                result = true
                isOff = false
            }
        } else {
            if (inputs[i].toDouble() < THRESHOLD) {
                isOff = true
            }
        }
        return result
    }

    /**
     * Request the containing UnitGenerator be disabled when checkAutoDisabled() is called. This can
     * be used to reduce CPU load.
     *
     * @param autoDisableEnabled
     */
    fun setAutoDisableEnabled(autoDisableEnabled: Boolean) {
        this.isAutoDisableEnabled = autoDisableEnabled
    }

    /**
     * Called by UnitGenerator when an envelope reaches the end of its contour.
     */
    fun checkAutoDisable() {
        if (isAutoDisableEnabled) {
            getGatedUnit().isEnabled = false
        }
    }

    private fun getGatedUnit(): UnitGenerator {
        return gatedUnit ?: unitGenerator!!
    }

    fun setupAutoDisable(unit: UnitGenerator) {
        gatedUnit = unit
        setAutoDisableEnabled(true)
        // Start off disabled so we don't immediately swamp the CPU.
        gatedUnit?.isEnabled = false
    }

    companion object {
        const val THRESHOLD = 0.01
    }
}
