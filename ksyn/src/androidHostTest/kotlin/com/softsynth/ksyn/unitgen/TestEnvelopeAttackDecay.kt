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

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestEnvelopeAttackDecay : TestUnitGate() {
    private var attackTime: Double = 0.0
    private var decayTime: Double = 0.0

    @BeforeTest
    fun beforeEach() {
        attackTime = 0.2
        decayTime = 0.4
    }

    @Test
    fun testOnOff() {
        val envelope = EnvelopeAttackDecay()
        synthesisEngine.add(envelope)

        envelope.attack.set(0.1)
        envelope.decay.set(0.2)

        synthesisEngine.start()
        envelope.start()
        time = synthesisEngine.currentTime
        checkSleepUntil(time + 0.1)
        assertEquals(0.0f, envelope.output.value, "still idling")

        // Trigger the envelope using on/off
        envelope.input.on()
        time = synthesisEngine.currentTime
        // Check end of attack cycle.
        checkSleepUntil(time + 0.1)
        assertTrue(envelope.output.value > 0.8f, "at peak")
        envelope.input.off()
        // Check end of decay cycle.
        checkSleepUntil(time + 0.3)
        assertTrue(envelope.output.value < 0.1f, "at peak")

        checkSleepUntil(synthesisEngine.currentTime + 0.1)

        // Trigger the envelope using trigger()
        envelope.input.trigger()
        time = synthesisEngine.currentTime
        // Check end of attack cycle.
        checkSleepUntil(time + 0.1)
        assertTrue(envelope.output.value > 0.8f, "at peak")
        // Check end of decay cycle.
        checkSleepUntil(time + 0.3)
        assertTrue(envelope.output.value < 0.1f, "at peak")
    }

    @Test
    fun testRetrigger() {
        val envelope = EnvelopeAttackDecay()
        synthesisEngine.add(envelope)

        envelope.attack.set(0.1)
        envelope.decay.set(0.2)

        synthesisEngine.start()
        envelope.start()
        time = synthesisEngine.currentTime
        checkSleepUntil(time + 0.1)
        assertEquals(0.0f, envelope.output.value, "still idling")

        // Trigger the envelope using trigger()
        envelope.input.trigger()
        // Check end of attack cycle.
        checkSleepUntil(synthesisEngine.currentTime + 0.1)
        assertEquals(1.0f, envelope.output.value, 0.1f, "at peak")

        // Decay half way.
        checkSleepUntil(synthesisEngine.currentTime + 0.1)
        assertTrue(envelope.output.value < 0.7f, "at peak")

        // Retrigger while decaying
        envelope.input.trigger()
        // Will get to top faster.
        checkSleepUntil(synthesisEngine.currentTime + 0.1)
        assertEquals(1.0f, envelope.output.value, 0.1f, "at peak")

        // Check end of decay cycle.
        checkSleepUntil(synthesisEngine.currentTime + 0.2)
        assertTrue(envelope.output.value < 0.1f, "at peak")
    }

    @Test
    fun testAutoDisable() {
        val ramp = LinearRamp()
        synthesisEngine.add(ramp)
        val envelope = EnvelopeAttackDecay()
        envelope.attack.set(0.1)
        envelope.decay.set(0.1)
        synthesisEngine.add(envelope)
        ramp.output.connect(envelope.amplitude)

        checkAutoDisable(ramp, envelope)
    }
}
