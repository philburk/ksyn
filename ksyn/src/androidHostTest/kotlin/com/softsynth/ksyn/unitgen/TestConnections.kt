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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestConnections : NonRealTimeTestCase() {

    private lateinit var add1: Add
    private lateinit var add2: Add
    private lateinit var add3: Add

    @BeforeTest
    fun beforeEach() {
        add1 = Add()
        add2 = Add()
        add3 = Add()
        synthesisEngine.add(add1)
        synthesisEngine.add(add2)
        synthesisEngine.add(add3)

        synthesisEngine.start()
        add1.start()
        add2.start()
        add3.start()

        add1.inputA.set(0.1f)
        add1.inputB.set(0.2f)

        add2.inputA.set(0.4f)
        add2.inputB.set(0.8f)

        add3.inputA.set(1.6f)
        add3.inputB.set(3.2f)
    }

    @Test
    fun testSet() {
        checkSleepUntil(synthesisEngine.currentTime + 0.01)
        assertEquals(0.3f, add1.output.value, 0.0001f, "set inputs of adder")
    }

    @Test
    fun testConnect() {
        checkSleepUntil(synthesisEngine.currentTime + 0.01)
        assertEquals(0.3f, add1.output.value, 0.0001f, "set inputs of adder")
        assertEquals(1.2f, add2.output.value, 0.0001f, "set inputs of adder")

        // Test different ways of connecting.
        add1.output.connect(add2.inputB)
        checkConnection()

        add1.output.connect(0, add2.inputB, 0)
        checkConnection()

        add1.output.getConnectablePart(0).connect(add2.inputB)
        checkConnection()

        add1.output.getConnectablePart(0).connect(add2.inputB.getConnectablePart(0))
        checkConnection()

        add2.inputB.connect(add1.output)
        checkConnection()

        add2.inputB.connect(0, add1.output, 0)
        checkConnection()

        add2.inputB.getConnectablePart(0).connect(add1.output)
        checkConnection()

        add2.inputB.getConnectablePart(0).connect(add1.output.getConnectablePart(0))
        checkConnection()
    }

    private fun checkConnection() {
        checkSleepUntil(synthesisEngine.currentTime + 0.01)
        assertEquals(0.3f, add1.output.value, 0.0001f, "connection should not change output")
        assertEquals(0.7f, add2.output.value, 0.0001f, "replace set value with output")

        // Revert to set value after disconnection.
        add1.output.disconnectAll()
        checkSleepUntil(synthesisEngine.currentTime + 0.01)
        assertEquals(0.3f, add1.output.value, 0.0001f, "still the same")
        assertEquals(1.2f, add2.output.value, 0.0001f, "should revert to original set() value")
    }
}
