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

import com.softsynth.ksyn.KSyn
import com.softsynth.ksyn.engine.SynthesisEngine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

open class TestUnitGate : NonRealTimeTestCase() {

    protected var time: Double = 0.0

    fun checkAutoDisable(ramp: LinearRamp, envelope: UnitGate) {
        val tolerance = 0.01f
        val adder = Add()
        synthesisEngine.add(adder)

        envelope.output.connect(adder.inputA)
        // Ensure ramp is connected correctly
        ramp.output.connect(adder.inputB)

        envelope.input.setupAutoDisable(ramp)

        // set up so ramp value should equal time
        ramp.current.set(0, 0.0)
        ramp.input.set(1.0)
        ramp.time.set(1.0)

        synthesisEngine.start()
        adder.start()

        time = synthesisEngine.currentTime
        time += 0.1
        checkSleepUntil(time)
        assertEquals(0.0f, envelope.output.value, "still idling")
        assertEquals(0.0f, ramp.output.value, tolerance, "ramp frozen at beginning")

        for (i in 0 until 3) {
            var level = ramp.output.value
            
            envelope.input.on()
            time += 0.1
            level += 0.1f
            checkSleepUntil(time)
            assertEquals(level, ramp.output.value, tolerance, "ramp going up $i")
            assertTrue(envelope.isEnabled, "enabled at peak")

            envelope.input.off()
            time += 0.1
            level += 0.1f
            checkSleepUntil(time)
            assertEquals(level, ramp.output.value, tolerance, "ramp going up more $i")
            assertEquals(0.0f, envelope.output.value, 0.1f, "at bottom")

            time += 0.2
            checkSleepUntil(time)
            assertEquals(level, ramp.output.value, tolerance, "ramp frozen $i")
        }
    }
}
