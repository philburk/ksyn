package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.data.SegmentedEnvelope
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestSequentialDataReaderAutoDisable : NonRealTimeTestCase() {

    @Test
    fun testFixedRateMonoReaderAutoDisable() {
        val ramp = LinearRamp()
        val reader = FixedRateMonoReader()
        val adder = Add()

        synthesisEngine.add(ramp)
        synthesisEngine.add(reader)
        synthesisEngine.add(adder)

        reader.output.connect(adder.inputA)
        ramp.output.connect(adder.inputB)

        // 4410 frames = 0.1 seconds at 44100 Hz
        val numFrames = (synthesisEngine.frameRate * 0.1).toInt()
        val sampleData = FloatArray(numFrames) { 0.5f }
        val sample = FloatSample(sampleData, 1)

        reader.setupAutoDisable(ramp)
        assertFalse(ramp.isEnabled, "Ramp should start disabled")

        synthesisEngine.start()
        adder.start()

        // Advance a bit while idle
        checkSleepUntil(synthesisEngine.currentTime + 0.05)
        assertFalse(ramp.isEnabled, "Ramp should remain disabled while queue is idle")

        // Queue sample - advance time so scheduled command executes and playback begins
        reader.dataQueue.queue(sample)
        checkSleepUntil(synthesisEngine.currentTime + 0.02)
        assertTrue(ramp.isEnabled, "Ramp should be enabled while sample is playing")

        // Advance halfway through sample
        checkSleepUntil(synthesisEngine.currentTime + 0.05)
        assertTrue(ramp.isEnabled, "Ramp should remain enabled while sample is playing")

        // Advance past end of sample (0.1s total duration)
        checkSleepUntil(synthesisEngine.currentTime + 0.1)
        assertFalse(ramp.isEnabled, "Ramp should be disabled after sample completes")
    }

    @Test
    fun testVariableRateMonoReaderAutoDisableWithEnvelope() {
        val ramp = LinearRamp()
        val reader = VariableRateMonoReader()
        val adder = Add()

        synthesisEngine.add(ramp)
        synthesisEngine.add(reader)
        synthesisEngine.add(adder)

        reader.output.connect(adder.inputA)
        ramp.output.connect(adder.inputB)

        // Envelope: attack (0.05s -> 1.0), decay (0.05s -> 0.5), release (0.05s -> 0.0)
        // sustain loop on frame 1 (decay end)
        val envelope = SegmentedEnvelope(
            doubleArrayOf(
                0.05, 1.0, // frame 0: attack
                0.05, 0.5, // frame 1: decay to sustain
                0.05, 0.0  // frame 2: release
            )
        ).apply {
            sustainBegin = 1
            sustainEnd = 2
        }

        reader.rate.set(1.0)
        reader.setupAutoDisable(ramp)
        assertFalse(ramp.isEnabled, "Ramp should start disabled")

        synthesisEngine.start()
        adder.start()

        // Advance a bit while idle
        checkSleepUntil(synthesisEngine.currentTime + 0.05)
        assertFalse(ramp.isEnabled, "Ramp should remain disabled while queue is idle")

        // Note On: queueOn attack + sustain loop
        reader.dataQueue.queueOn(envelope)
        checkSleepUntil(synthesisEngine.currentTime + 0.02)
        assertTrue(ramp.isEnabled, "Ramp should be enabled on queueOn")

        // Advance through attack into sustain loop
        checkSleepUntil(synthesisEngine.currentTime + 0.15)
        assertTrue(ramp.isEnabled, "Ramp should remain enabled while sustaining")

        // Note Off: queueOff release
        reader.dataQueue.queueOff(envelope)
        // Advance slightly - release is still playing
        checkSleepUntil(synthesisEngine.currentTime + 0.02)
        assertTrue(ramp.isEnabled, "Ramp should remain enabled during release phase")

        // Advance past release completion (0.05s decay remaining + 0.05s release)
        checkSleepUntil(synthesisEngine.currentTime + 0.15)
        assertFalse(ramp.isEnabled, "Ramp should be disabled after envelope release completes")

        // Retrigger: Note On again
        reader.dataQueue.queueOn(envelope)
        checkSleepUntil(synthesisEngine.currentTime + 0.02)
        assertTrue(ramp.isEnabled, "Ramp should wake up on subsequent queueOn")

        // Note Off and let finish
        reader.dataQueue.queueOff(envelope)
        checkSleepUntil(synthesisEngine.currentTime + 0.15)
        assertFalse(ramp.isEnabled, "Ramp should be disabled again after second note finishes")
    }

    @Test
    fun testSelfAutoDisableDefault() {
        val reader = VariableRateMonoReader()
        synthesisEngine.add(reader)

        val envelope = SegmentedEnvelope(
            doubleArrayOf(
                0.05, 1.0,
                0.05, 0.0
            )
        )

        reader.rate.set(1.0)
        reader.setupAutoDisable() // defaults to this
        assertFalse(reader.isEnabled, "Reader should start disabled")

        synthesisEngine.start()
        reader.start()

        reader.dataQueue.queue(envelope)
        checkSleepUntil(synthesisEngine.currentTime + 0.02)
        assertTrue(reader.isEnabled, "Reader should become enabled when queued")

        checkSleepUntil(synthesisEngine.currentTime + 0.15)
        assertFalse(reader.isEnabled, "Reader should become disabled when envelope completes")
    }
}
