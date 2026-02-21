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
import com.softsynth.ksyn.data.DoubleTable
import com.softsynth.ksyn.data.Function
import com.softsynth.ksyn.engine.SynthesisEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * @author Phil Burk, (C) 2009 Mobileer Inc
 */
class TestFunction {
    private lateinit var synth: SynthesisEngine

    @BeforeTest
    fun beforeEach() {
        synth = KSyn.createSynthesizer() as SynthesisEngine
        synth.start()
    }

    @AfterTest
    fun afterEach() {
        synth.stop()
    }

    @Test
    fun testDoubleTable() {
        val data = doubleArrayOf(2.0, 0.0, 3.0)
        val table = DoubleTable(data)
        assertEquals(2.0, table.evaluate(-1.4), 0.0001, "DoubleTable below")
        assertEquals(2.0, table.evaluate(-1.0), 0.0001, "DoubleTable edge")
        assertEquals(1.0, table.evaluate(-0.5), 0.0001, "DoubleTable mid")
        assertEquals(0.0, table.evaluate(0.0), 0.0001, "DoubleTable zero")
        assertEquals(0.75, table.evaluate(0.25), 0.0001, "DoubleTable mid")
        assertEquals(3.0, table.evaluate(1.3), 0.0001, "DoubleTable above")
    }

    @Test
    fun testFunctionEvaluator() = runBlocking {
        val shaper = FunctionEvaluator()
        synth.add(shaper)
        shaper.start()

        val cuber = object : Function {
            override fun evaluate(x: Double): Double = x * x * x
        }
        shaper.function.set(cuber)

        shaper.input.set(0.5)
        
        // Advance synthesis engine until time passes.
        val targetFrame = (synth.currentTime + 0.001) * synth.frameRate
        while (synth.frameCount <= targetFrame) {
            synth.generateNextBuffer()
        }

        assertEquals((0.5f * 0.5f * 0.5f), shaper.output.value, 0.0001f, "Cuber")
    }
}
