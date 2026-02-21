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

import com.softsynth.ksyn.KSyn
import com.softsynth.ksyn.engine.SynthesisEngine
import com.softsynth.ksyn.toSample
import com.softsynth.ksyn.unitgen.Minimum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

import kotlinx.coroutines.runBlocking

class TestSet {

    private fun checkSleepUntil(synthesisEngine: SynthesisEngine, time: Double) = runBlocking {
        val targetFrame = (time * synthesisEngine.frameRate).toLong()
        while (synthesisEngine.frameCount < targetFrame) {
            synthesisEngine.generateNextBuffer()
        }
    }

    /** Internal value setting.  */
    @Test
    fun testSetValue() {
        val numParts = 4
        val port = UnitInputPort(numParts, "Tester")
        port.setValueInternal(0, 100.0f.toSample())
        port.setValueInternal(2, 120.0f.toSample())
        port.setValueInternal(1, 110.0f.toSample())
        port.setValueInternal(3, 130.0f.toSample())
        assertEquals(100.0f, port.get(0).toFloat(), "check port value")
        assertEquals(120.0f, port.get(2).toFloat(), "check port value")
        assertEquals(110.0f, port.get(1).toFloat(), "check port value")
        assertEquals(130.0f, port.get(3).toFloat(), "check port value")
    }

    @Test
    fun testSet() {
        val synthesisEngine = KSyn.createSynthesizer() as SynthesisEngine
        synthesisEngine.start()
        checkSleepUntil(synthesisEngine, 0.01)
        val min = Minimum()
        synthesisEngine.add(min)

        val x = 33.99f
        val y = 8.31f
        min.inputA.set(x)
        min.inputB.set(y)
        checkSleepUntil(synthesisEngine, synthesisEngine.currentTime + 0.01)
        assertEquals(x, min.inputA.get(0).toFloat(), "min set A") // In KSyn `getValue()` is `get().toFloat()` or `getValues()[0]`
        assertEquals(y, min.inputB.get(0).toFloat(), "min set B")
        min.start()
        checkSleepUntil(synthesisEngine, synthesisEngine.currentTime + 0.01)

        assertEquals(y, min.output.value, 0.001f, "min output") // `output.value` is available
        synthesisEngine.stop()
    }

    /** if we use a port index out of range we want to know now and not blow up the engine.  */
    @Test
    fun testSetBadPort() {
        val synthesisEngine = KSyn.createSynthesizer() as SynthesisEngine
        synthesisEngine.start()
        val min = Minimum()
        synthesisEngine.add(min)

        min.start()
        try {
            min.inputA.set(1, 23.45.toSample())
        } catch (ignored: ArrayIndexOutOfBoundsException) {
            // Success
        } catch (e: Exception) {
            fail("Catch port out of range, caught $e")
        }

        // Don't blow up here.
        checkSleepUntil(synthesisEngine, 0.01)

        synthesisEngine.stop()
    }
}
