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

class TestEnvelopeDAHDSR : TestUnitGate() {

    private var delayTime: Double = 0.0
    private var attackTime: Double = 0.0
    private var holdTime: Double = 0.0
    private var decayTime: Double = 0.0
    private var sustainLevel: Double = 0.0
    private var releaseTime: Double = 0.0

    @BeforeTest
    fun beforeEach() {
        delayTime = 0.1
        attackTime = 0.2
        holdTime = 0.3
        decayTime = 0.4
        sustainLevel = 0.5
        releaseTime = 0.6
    }

    @Test
    fun testStages() {
        val ramp = checkToSustain()

        // Change sustain level to simulate tremolo sustain.
        sustainLevel = 0.7
        ramp.sustain.set(sustainLevel)
        time += 0.01
        checkSleepUntil(time)
        assertEquals(sustainLevel.toFloat(), ramp.output.value, 0.01f, "sustain moving delaying")

        // Gate off to let envelope release.
        ramp.input.set(0.0)
        checkSleepUntil(time + (releaseTime * 0.1))
        val releaseValue = ramp.output.value
        assertEquals((sustainLevel * 0.36).toFloat(), releaseValue, 0.01f, "partway down release")
    }

    private fun checkToSustain(): EnvelopeDAHDSR {
        val ramp = EnvelopeDAHDSR()
        synthesisEngine.add(ramp)

        ramp.delay.set(delayTime)
        ramp.attack.set(attackTime)
        ramp.hold.set(holdTime)
        ramp.decay.set(decayTime)
        ramp.sustain.set(sustainLevel)
        ramp.release.set(releaseTime)

        synthesisEngine.start()
        ramp.start()
        time = synthesisEngine.currentTime
        checkSleepUntil(time + (2.0 * delayTime))
        assertEquals(0.0f, ramp.output.value, "still idling")

        // Trigger the envelope.
        ramp.input.set(1.0)
        time = synthesisEngine.currentTime
        // Check end of delay cycle.
        checkSleepUntil(time + (delayTime * 0.9))
        assertEquals(0.0f, ramp.output.value, 0.01f, "still delaying")
        // Half way up attack ramp.
        checkSleepUntil(time + delayTime + (attackTime * 0.5))
        assertEquals(0.5f, ramp.output.value, 0.01f, "half attack")
        // Holding after attack.
        checkSleepUntil(time + delayTime + attackTime + (holdTime * 0.1))
        assertEquals(1.0f, ramp.output.value, 0.01f, "holding")
        checkSleepUntil(time + delayTime + attackTime + (holdTime * 0.9))
        assertEquals(1.0f, ramp.output.value, 0.01f, "still holding")
        checkSleepUntil(time + delayTime + attackTime + holdTime + decayTime)
        time = synthesisEngine.currentTime
        assertEquals(sustainLevel.toFloat(), ramp.output.value, 0.01f, "at sustain")
        return ramp
    }

    @Test
    fun testRetrigger() {
        val ramp = checkToSustain()

        // Gate off to let envelope release.
        ramp.input.set(0.0)
        checkSleepUntil(time + (releaseTime * 0.1))
        val releaseValue = ramp.output.value
        assertEquals((sustainLevel * 0.36).toFloat(), releaseValue, 0.01f, "partway down release")

        // Retrigger during release phase.
        time = synthesisEngine.currentTime
        ramp.input.set(1.0)
        // Check end of delay cycle.
        checkSleepUntil(time + (delayTime * 0.9))
        assertEquals(releaseValue, ramp.output.value, 0.01f, "still delaying")
        // Half way up attack ramp from where it started.
        checkSleepUntil(time + delayTime + (attackTime * 0.5))
        assertEquals(releaseValue + 0.5f, ramp.output.value, 0.01f, "half attack")
    }

    // I noticed a hang while playing with knobs.
    @Test
    fun testHang() {
        delayTime = 0.0
        attackTime = 0.0
        holdTime = 0.0
        decayTime = 0.0
        sustainLevel = 0.3
        releaseTime = 3.0

        val ramp = EnvelopeDAHDSR()
        synthesisEngine.add(ramp)

        ramp.delay.set(delayTime)
        ramp.attack.set(attackTime)
        ramp.hold.set(holdTime)
        ramp.decay.set(decayTime)
        ramp.sustain.set(sustainLevel)
        ramp.release.set(releaseTime)

        synthesisEngine.start()
        ramp.start()
        // Trigger the envelope.
        ramp.input.set(1.0)
        time = synthesisEngine.currentTime
        checkSleepUntil(time + 0.01)
        assertEquals(sustainLevel.toFloat(), ramp.output.value, "should jump to sustain level")

        // Gate off to let envelope release.
        ramp.input.set(0.0)
        checkSleepUntil(time + 1.0)
        val releaseValue = ramp.output.value
        assertTrue(sustainLevel > releaseValue, "partway down release")

        holdTime = 0.5
        ramp.hold.set(holdTime)
        decayTime = 0.5
        ramp.decay.set(decayTime)

        // Retrigger during release phase and try to catch it at top of hold
        time = synthesisEngine.currentTime
        ramp.input.set(1.0)
        // Check end of delay cycle.
        checkSleepUntil(time + (holdTime * 0.1))
        assertEquals(1.0f, ramp.output.value, 0.01f, "should jump to hold")
    }

    @Test
    fun testNegative() {
        delayTime = -0.1
        attackTime = -0.2
        holdTime = -0.3
        decayTime = -0.4
        sustainLevel = 0.3
        releaseTime = -0.5

        val ramp = EnvelopeDAHDSR()
        synthesisEngine.add(ramp)

        ramp.delay.set(delayTime)
        ramp.attack.set(attackTime)
        ramp.hold.set(holdTime)
        ramp.decay.set(decayTime)
        ramp.sustain.set(sustainLevel)
        ramp.release.set(releaseTime)

        synthesisEngine.start()
        ramp.start()
        // Trigger the envelope.
        ramp.input.set(1.0)
        time = synthesisEngine.currentTime
        time += 0.1
        checkSleepUntil(time + 0.01)
        assertEquals(sustainLevel.toFloat(), ramp.output.value, "should jump to sustain level")

        sustainLevel = -0.4
        ramp.sustain.set(sustainLevel)
        time += 0.1
        checkSleepUntil(time)
        assertEquals(0.0f, ramp.output.value, "sustain should clip at zero")

        sustainLevel = 0.4
        ramp.sustain.set(sustainLevel)
        time += 0.1
        checkSleepUntil(time)
        assertEquals(sustainLevel.toFloat(), ramp.output.value, "sustain should come back")

        // Gate off to let envelope release.
        ramp.input.set(0.0)
        time += 0.1
        checkSleepUntil(time)
        val releaseValue = ramp.output.value
        assertEquals(0.0f, releaseValue, "release quickly")
    }

    @Test
    fun testOnOff() {
        val ramp = EnvelopeDAHDSR()
        synthesisEngine.add(ramp)

        ramp.delay.set(0.0)
        ramp.attack.set(0.1)
        ramp.hold.set(0.0)
        ramp.decay.set(0.0)
        ramp.sustain.set(0.9)
        ramp.release.set(0.1)

        synthesisEngine.start()
        ramp.start()
        time = synthesisEngine.currentTime
        checkSleepUntil(time + 0.2)
        assertEquals(0.0f, ramp.output.value, "still idling")

        // Trigger the envelope.
        ramp.input.on()
        time = synthesisEngine.currentTime
        // Check end of delay cycle.
        checkSleepUntil(time + 0.2)
        assertEquals(0.9f, ramp.output.value, 0.01f, "at sustain")

        // Release the envelope.
        ramp.input.off()
        time = synthesisEngine.currentTime
        // Check end of delay cycle.
        checkSleepUntil(time + 0.2)
        assertEquals(0.0f, ramp.output.value, 0.01f, "after release")
    }

    @Test
    fun testAutoDisable() {
        val ramp = LinearRamp()
        synthesisEngine.add(ramp)
        val envelope = EnvelopeDAHDSR()
        synthesisEngine.add(envelope)
        envelope.attack.set(0.1)
        envelope.decay.set(0.1)
        envelope.release.set(0.1)
        envelope.sustain.set(0.1)
        ramp.output.connect(envelope.amplitude)

        checkAutoDisable(ramp, envelope)
    }

    fun checkReleaseTiming(releaseTime: Double, tolerance: Float) {
        delayTime = 0.0
        attackTime = 0.2
        holdTime = 0.0
        decayTime = 10.0
        sustainLevel = 1.0

        val ramp = EnvelopeDAHDSR()
        synthesisEngine.add(ramp)

        ramp.delay.set(delayTime)
        ramp.attack.set(attackTime)
        ramp.hold.set(holdTime)
        ramp.decay.set(decayTime)
        ramp.sustain.set(sustainLevel)
        ramp.release.set(releaseTime)

        synthesisEngine.start()
        ramp.start()
        // Trigger the envelope.
        ramp.input.set(1.0)
        time = synthesisEngine.currentTime
        time += attackTime * 2
        checkSleepUntil(time)
        assertEquals(sustainLevel.toFloat(), ramp.output.value, "should be at to sustain level")

        // Start envelope release.
        ramp.input.set(0.0)
        val db90 = 20.0 * Math.log10(1.0 / 32768.0)
        val numSteps = 10
        for (i in 0 until 10) {
            time += releaseTime / numSteps
            checkSleepUntil(time)
            val expectedDB = db90 * (i + 1) / numSteps
            val expectedAmplitude = sustainLevel * Math.pow(10.0, expectedDB / 20.0)
            val releaseValue = ramp.output.value
            assertEquals(expectedAmplitude.toFloat(), releaseValue, tolerance, "release $i at")
        }
        time += releaseTime / numSteps
        checkSleepUntil(time)
        val releaseValue = ramp.output.value
        assertEquals(0.0f, releaseValue, 0.0001f, "env after release time should go to zero")
    }

    @Test
    fun testReleaseTiming() {
        checkReleaseTiming(0.1, 0.004f)
        checkReleaseTiming(1.0, 0.002f)
        checkReleaseTiming(2.5, 0.001f)
        checkReleaseTiming(10.0, 0.001f)
    }
}
