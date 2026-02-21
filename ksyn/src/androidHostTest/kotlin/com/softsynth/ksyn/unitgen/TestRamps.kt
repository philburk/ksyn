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

import kotlin.test.Test
import kotlin.test.assertEquals

class TestRamps : NonRealTimeTestCase() {

    fun viewContinuousRamp(duration: Double, startValue: Double, targetValue: Double) {
        val ramp = ContinuousRamp()
        synthesisEngine.add(ramp)

        ramp.current.set(0, startValue)
        ramp.input.set(startValue)
        ramp.time.set(duration)

        synthesisEngine.start()
        ramp.start()
        checkSleepUntil(synthesisEngine.currentTime + 0.01)
        ramp.input.set(targetValue)

        var time = synthesisEngine.currentTime
        val numLoops = 20
        val increment = duration / numLoops
        for (i in 0..numLoops) {
            val value = ramp.output.value
            // System.out.printf("i = %d, t = %9.5f,  value = %8.4f\n", i, time, value)
            time += increment
            checkSleepUntil(time)
        }
        synthesisEngine.stop()
    }

    fun checkContinuousRamp(duration: Double, startValue: Double, targetValue: Double) {
        val ramp = ContinuousRamp()
        synthesisEngine.add(ramp)

        ramp.current.set(0, startValue)
        ramp.input.set(startValue)
        ramp.time.set(duration)

        synthesisEngine.start()
        ramp.start()
        checkSleepUntil(synthesisEngine.currentTime + 0.01)
        assertEquals(ramp.input.value, ramp.output.value, "start flat")

        ramp.input.set(targetValue)
        val startTime = synthesisEngine.currentTime
        checkSleepUntil(startTime + (duration / 2))
        assertEquals(((targetValue + startValue) / 2.0).toFloat(), ramp.output.value, 0.01f, "ramping up")
        checkSleepUntil(startTime + duration)
        assertEquals(targetValue.toFloat(), ramp.output.value, 0.01f, "ramping up")
        checkSleepUntil(startTime + duration + 0.1)
        assertEquals(targetValue.toFloat(), ramp.output.value, "flat again")

        synthesisEngine.stop()
    }

    @Test
    fun testContinuousRamp() {
        viewContinuousRamp(4.0, 0.0, 1.0)
    }

    @Test
    fun testExponentialRamp() {
        val ramp = ExponentialRamp()
        synthesisEngine.add(ramp)

        val duration = 0.3
        ramp.current.set(0, 1.0)
        ramp.input.set(1.0)
        ramp.time.set(duration)

        synthesisEngine.start()
        ramp.start()
        checkSleepUntil(synthesisEngine.currentTime + 0.01)
        assertEquals(ramp.input.value, ramp.output.value, "start flat")

        ramp.input.set(8.0)
        val startTime = synthesisEngine.currentTime
        checkSleepUntil(startTime + 0.1)
        assertEquals(2.0f, ramp.output.value, 0.01f, "ramping up")
        checkSleepUntil(startTime + 0.2)
        assertEquals(4.0f, ramp.output.value, 0.01f, "ramping up")
        checkSleepUntil(startTime + 0.3)
        assertEquals(8.0f, ramp.output.value, 0.01f, "ramping up")
        checkSleepUntil(startTime + 0.4)
        assertEquals(8.0f, ramp.output.value, "flat again")
    }

    @Test
    fun testLinearRamp() {
        val ramp = LinearRamp()
        synthesisEngine.add(ramp)

        val duration = 0.4
        ramp.current.set(0, 0.0)
        ramp.input.set(0.0)
        ramp.time.set(duration)

        synthesisEngine.start()
        ramp.start()
        checkSleepUntil(synthesisEngine.currentTime + 0.01)
        assertEquals(ramp.input.value, ramp.output.value, "start flat")

        ramp.input.set(8.0)
        val startTime = synthesisEngine.currentTime
        checkSleepUntil(startTime + 0.1)
        assertEquals(2.0f, ramp.output.value, 0.01f, "ramping up")
        checkSleepUntil(startTime + 0.2)
        assertEquals(4.0f, ramp.output.value, 0.01f, "ramping up")
        checkSleepUntil(startTime + 0.3)
        assertEquals(6.0f, ramp.output.value, 0.01f, "ramping up")
        checkSleepUntil(startTime + 0.4)
        assertEquals(8.0f, ramp.output.value, "flat again")
    }

    @Test
    fun testExponentialRampConnected() {
        val ramp = ExponentialRamp()
        val pass = PassThrough()
        synthesisEngine.add(ramp)
        synthesisEngine.add(pass)

        val duration = 0.3
        ramp.current.set(0, 1.0)
        pass.input.set(1.0)
        ramp.time.set(duration)

        // Send value through a connected unit.
        pass.input.set(1.0)
        pass.output.connect(ramp.input)

        synthesisEngine.start()
        ramp.start()
        checkSleepUntil(synthesisEngine.currentTime + 0.01)
        assertEquals(ramp.input.value, ramp.output.value, "start flat")

        pass.input.set(8.0)
        val startTime = synthesisEngine.currentTime
        checkSleepUntil(startTime + 0.1)
        assertEquals(2.0f, ramp.output.value, 0.01f, "ramping up")
        checkSleepUntil(startTime + 0.2)
        assertEquals(4.0f, ramp.output.value, 0.01f, "ramping up")
        checkSleepUntil(startTime + 0.3)
        assertEquals(8.0f, ramp.output.value, 0.01f, "ramping up")
        checkSleepUntil(startTime + 0.4)
        assertEquals(8.0f, ramp.output.value, "flat again")
    }

    @Test
    fun testLinearRampConnected() {
        val ramp = LinearRamp()
        val pass = PassThrough()
        synthesisEngine.add(ramp)
        synthesisEngine.add(pass)

        val duration = 0.4
        ramp.current.set(0, 0.0)
        pass.input.set(0.0)
        ramp.time.set(duration)

        // Send value through a connected unit.
        pass.input.set(0.0)
        pass.output.connect(ramp.input)

        synthesisEngine.start()
        ramp.start()
        checkSleepUntil(synthesisEngine.currentTime + 0.01)
        assertEquals(ramp.input.value, ramp.output.value, "start flat")

        pass.input.set(8.0)
        val startTime = synthesisEngine.currentTime
        checkSleepUntil(startTime + 0.1)
        assertEquals(2.0f, ramp.output.value, 0.01f, "ramping up")
        checkSleepUntil(startTime + 0.2)
        assertEquals(4.0f, ramp.output.value, 0.01f, "ramping up")
        checkSleepUntil(startTime + 0.3)
        assertEquals(6.0f, ramp.output.value, 0.01f, "ramping up")
        checkSleepUntil(startTime + 0.4)
        assertEquals(8.0f, ramp.output.value, "flat again")
    }
}
