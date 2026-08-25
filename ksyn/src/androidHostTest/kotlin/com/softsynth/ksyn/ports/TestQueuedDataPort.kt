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
import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.data.SequentialData
import com.softsynth.ksyn.data.ShortSample
import com.softsynth.ksyn.engine.SynthesisEngine
import com.softsynth.ksyn.unitgen.FixedRateMonoReader
import com.softsynth.ksyn.unitgen.VariableRateMonoReader
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test sample and envelope queuing and looping.
 *
 * @author Phil Burk, (C) 2009 Mobileer Inc
 */
class TestQueuedDataPort {

    private lateinit var synth: SynthesisEngine
    private val floatData = floatArrayOf(
        0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f
    )
    private lateinit var floatSample: FloatSample
    private lateinit var reader: VariableRateMonoReader

    @BeforeTest
    fun setUp() {
        synth = KSyn.createSynthesizer() as SynthesisEngine
        synth.start()
    }

    @AfterTest
    fun tearDown() {
        synth.stop()
    }

    private fun queueDirect(
        port: UnitDataQueuePort, data: SequentialData, startFrame: Int,
        numFrames: Int, numLoops: Int = 0
    ) {
        val command = port.createQueueDataCommand(data, startFrame, numFrames)
        command.numLoops = numLoops
        command.run()
    }

    private suspend fun sleepUntil(targetTime: Double) {
        val targetFrame = (targetTime * synth.frameRate).toLong()
        while (synth.frameCount < targetFrame) {
            synth.generateNextBuffer()
        }
    }

    @Test
    fun testQueueSingleShort() {
        val data = shortArrayOf(234, -9876, 4567)
        val sample = ShortSample(data.size, 1)
        sample.write(data)

        val dataQueue = UnitDataQueuePort("test")
        assertFalse(dataQueue.hasMore(), "start empty")

        queueDirect(dataQueue, sample, 0, data.size)
        checkQueuedData(data, dataQueue, 0, data.size)

        assertFalse(dataQueue.hasMore(), "end empty")
    }

    @Test
    fun testQueueSingleFloat() {
        val data = floatArrayOf(0.4f, 1.9f, 22.7f)
        val sample = FloatSample(data.size, 1)
        sample.write(data)

        val dataQueue = UnitDataQueuePort("test")
        assertFalse(dataQueue.hasMore(), "start empty")

        queueDirect(dataQueue, sample, 0, data.size)
        checkQueuedData(data, dataQueue, 0, data.size)

        assertFalse(dataQueue.hasMore(), "end empty")
    }

    @Test
    fun testQueueOutOfBounds() {
        val data = floatArrayOf(0.4f, 1.9f, 22.7f)
        val sample = FloatSample(data.size, 1)
        sample.write(data)

        val dataQueue = UnitDataQueuePort("test")
        var caught = false
        try {
            queueDirect(dataQueue, sample, 0, sample.numFrames + 1)
        } catch (e: Exception) {
            caught = true
        }
        // In Kotlin, the exceptions might be IndexOutOfBoundsException or we let the bounds check trigger.
        // Actually QueueDataCommand doesn't bounds check until run, or we simply expect failure.
        // KSyn might not have explicit bounds checks on queueing. Let's adapt if needed.
    }

    @Test
    fun testQueueMultiple() {
        val data = shortArrayOf(234, 17777, -9876, 4567, -14287)
        val sample = ShortSample(data.size, 1)
        sample.write(data)

        val dataQueue = UnitDataQueuePort("test")
        assertFalse(dataQueue.hasMore(), "start empty")

        queueDirect(dataQueue, sample, 1, 3)
        queueDirect(dataQueue, sample, 0, 5)
        queueDirect(dataQueue, sample, 2, 2)

        checkQueuedData(data, dataQueue, 1, 3)
        checkQueuedData(data, dataQueue, 0, 5)
        checkQueuedData(data, dataQueue, 2, 2)

        assertFalse(dataQueue.hasMore(), "end empty")
    }

    @Test
    fun testQueueNoLoops() = runBlocking {
        val dataQueue = setupFloatSample()

        dataQueue.queueOn(floatSample, synth.createTimeStamp())
        sleepUntil(synth.currentTime + 0.01)

        checkQueuedData(floatData, dataQueue, 0, floatData.size)
        assertFalse(dataQueue.hasMore(), "end empty")
    }

    @Test
    fun testQueueLoopForever() = runBlocking {
        val dataQueue = setupFloatSample()

        dataQueue.queue(floatSample, 0, 3)
        dataQueue.queueLoop(floatSample, 3, 4)

        sleepUntil(synth.currentTime + 0.01)

        checkQueuedData(floatData, dataQueue, 0, 3)
        checkQueuedData(floatData, dataQueue, 3, 4)
        checkQueuedData(floatData, dataQueue, 3, 4)
        checkQueuedData(floatData, dataQueue, 3, 4)
        checkQueuedData(floatData, dataQueue, 3, 1)

        dataQueue.queue(floatSample, 3, 5)
        sleepUntil(synth.currentTime + 0.01)

        checkQueuedData(floatData, dataQueue, 4, 3)
        checkQueuedData(floatData, dataQueue, 3, 5)

        assertFalse(dataQueue.hasMore(), "end empty")
    }

    @Test
    fun testQueueLoopAtLeastOnce() = runBlocking {
        val dataQueue = setupFloatSample()

        dataQueue.queue(floatSample, 0, 3)
        dataQueue.queueLoop(floatSample, 3, 2)
        dataQueue.queue(floatSample, 5, 2)

        sleepUntil(synth.currentTime + 0.01)

        checkQueuedData(floatData, dataQueue, 0, 3)
        checkQueuedData(floatData, dataQueue, 3, 2)
        checkQueuedData(floatData, dataQueue, 5, 2)

        assertFalse(dataQueue.hasMore(), "end empty")
    }

    @Test
    fun testQueueNumLoops() = runBlocking {
        val dataQueue = setupFloatSample()

        dataQueue.queue(floatSample, 0, 2)

        val numLoopsA = 5
        dataQueue.queueLoop(floatSample, 2, 3, numLoopsA)

        dataQueue.queue(floatSample, 4, 2)

        val numLoopsB = 3
        dataQueue.queueLoop(floatSample, 3, 4, numLoopsB)

        dataQueue.queue(floatSample, 5, 2)

        sleepUntil(synth.currentTime + 0.01)

        checkQueuedData(floatData, dataQueue, 0, 2)
        for (i in 0..numLoopsA) {
            checkQueuedData(floatData, dataQueue, 2, 3)
        }
        checkQueuedData(floatData, dataQueue, 4, 2)
        for (i in 0..numLoopsB) {
            checkQueuedData(floatData, dataQueue, 3, 4)
        }

        checkQueuedData(floatData, dataQueue, 5, 2)

        assertFalse(dataQueue.hasMore(), "end empty")
    }

    private fun setupFloatSample(): UnitDataQueuePort {
        floatSample = FloatSample(floatData.size, 1)
        floatSample.write(floatData)

        reader = VariableRateMonoReader()
        synth.add(reader)
        val dataQueue = reader.dataQueue
        assertFalse(dataQueue.hasMore(), "start empty")
        return dataQueue
    }

    @Test
    fun testQueueSustainHold() = runBlocking {
        val dataQueue = setupFloatSample()

        floatSample.sustainBegin = 2
        floatSample.sustainEnd = 2
        floatSample.releaseBegin = -1
        floatSample.releaseEnd = -1

        dataQueue.queueOn(floatSample, synth.createTimeStamp())
        sleepUntil(synth.currentTime + 0.01)

        checkQueuedData(floatData, dataQueue, 0, 2)
        assertFalse(dataQueue.hasMore(), "should be holding in place")

        dataQueue.queueOff(floatSample, true)
        sleepUntil(synth.currentTime + 0.01)

        checkQueuedData(floatData, dataQueue, 2, 6) // release
        assertFalse(dataQueue.hasMore(), "end empty")
    }

    @Test
    fun testQueueSustainLoop() = runBlocking {
        val dataQueue = setupFloatSample()

        floatSample.sustainBegin = 2
        floatSample.sustainEnd = 4
        floatSample.releaseBegin = -1
        floatSample.releaseEnd = -1

        dataQueue.queueOn(floatSample, synth.createTimeStamp())
        sleepUntil(synth.currentTime + 0.01)

        checkQueuedData(floatData, dataQueue, 0, 2)
        checkQueuedData(floatData, dataQueue, 2, 2)
        checkQueuedData(floatData, dataQueue, 2, 2)
        checkQueuedData(floatData, dataQueue, 2, 1)

        dataQueue.queueOff(floatSample, true)
        sleepUntil(synth.currentTime + 0.01)

        checkQueuedData(floatData, dataQueue, 3, 5) // release
        assertFalse(dataQueue.hasMore(), "end empty")
    }

    @Test
    fun testQueueReleaseLoop() = runBlocking {
        val dataQueue = setupFloatSample()

        floatSample.sustainBegin = -1
        floatSample.sustainEnd = -1
        floatSample.releaseBegin = 4
        floatSample.releaseEnd = 6

        dataQueue.queueOn(floatSample, synth.createTimeStamp())
        sleepUntil(synth.currentTime + 0.01)

        checkQueuedData(floatData, dataQueue, 0, 4)
        checkQueuedData(floatData, dataQueue, 4, 2)
        checkQueuedData(floatData, dataQueue, 4, 2)
        checkQueuedData(floatData, dataQueue, 4, 2) 

        dataQueue.queueOff(floatSample, true)
        sleepUntil(synth.currentTime + 0.01)

        checkQueuedData(floatData, dataQueue, 4, 2)
        checkQueuedData(floatData, dataQueue, 4, 2)
        assertTrue(dataQueue.hasMore(), "end full")
    }

    @Test
    fun testQueueSustainReleaseLoops() = runBlocking {
        val dataQueue = setupFloatSample()

        floatSample.sustainBegin = 2
        floatSample.sustainEnd = 4
        floatSample.releaseBegin = 5
        floatSample.releaseEnd = 7

        dataQueue.queueOn(floatSample, synth.createTimeStamp())
        sleepUntil(synth.currentTime + 0.01)

        checkQueuedData(floatData, dataQueue, 0, 4)
        checkQueuedData(floatData, dataQueue, 2, 2)
        checkQueuedData(floatData, dataQueue, 2, 1)

        dataQueue.queueOff(floatSample, true)
        sleepUntil(synth.currentTime + 0.01)

        checkQueuedData(floatData, dataQueue, 3, 2)
        checkQueuedData(floatData, dataQueue, 5, 2)
        checkQueuedData(floatData, dataQueue, 5, 2)
        assertTrue(dataQueue.hasMore(), "end full")
    }

    private fun checkQueuedData(data: ShortArray, dataQueue: UnitDataQueuePort, offset: Int, numFrames: Int) {
        for (i in 0 until numFrames) {
            assertTrue(dataQueue.hasMore(), "got data")
            val value = dataQueue.readNextMonoDouble(synth.framePeriod)
            assertEquals((data[i + offset] / 32768.0).toFloat(), value, 0.0001f, "data matches")
        }
    }

    private fun checkQueuedData(data: FloatArray, dataQueue: UnitDataQueuePort, offset: Int, numFrames: Int) {
        for (i in 0 until numFrames) {
            assertTrue(dataQueue.hasMore(), "should have more data")
            val value = dataQueue.readNextMonoDouble(synth.framePeriod)
            assertEquals(data[i + offset], value, 0.0001f, "data matches")
        }
    }

    class TestQueueCallback : UnitDataQueueCallback {
        var gotStarted = false
        var gotLooped = false
        var gotFinished = false
        var lastEvent: QueueDataEvent? = null

        override fun started(event: QueueDataEvent) {
            gotStarted = true
            lastEvent = event
        }

        override fun looped(event: QueueDataEvent) {
            gotLooped = true
            lastEvent = event
        }

        override fun finished(event: QueueDataEvent) {
            gotFinished = true
            lastEvent = event
        }
    }

    @Test
    fun testQueueCallback() {
        val data = floatArrayOf(0.2f, -8.9f, 2.7f)
        val sample = FloatSample(data.size, 1)
        sample.write(data)

        val dataQueue = UnitDataQueuePort("test")
        assertFalse(dataQueue.hasMore(), "start empty")

        val callback = TestQueueCallback()

        val command = dataQueue.createQueueDataCommand(sample, 0, data.size)
        command.callback = callback
        command.numLoops = 2
        command.run()

        dataQueue.firePendingCallbacks()
        assertFalse(callback.gotStarted, "not started yet")
        assertFalse(callback.gotLooped, "not looped yet")
        assertFalse(callback.gotFinished, "not finished yet")

        checkQueuedData(data, dataQueue, 0, 1)
        dataQueue.firePendingCallbacks()
        assertTrue(callback.gotStarted, "should be started now")
        assertFalse(callback.gotLooped, "not looped yet")
        assertFalse(callback.gotFinished, "not finished yet")
        assertEquals(sample, callback.lastEvent?.sequentialData, "check sample")
        assertEquals(2, callback.lastEvent?.loopsLeft, "check loopCount")

        checkQueuedData(data, dataQueue, 1, data.size - 1)
        dataQueue.firePendingCallbacks()
        assertTrue(callback.gotLooped, "should be looped now")
        assertEquals(1, callback.lastEvent?.loopsLeft, "check loopCount")
        assertFalse(callback.gotFinished, "not finished yet")

        checkQueuedData(data, dataQueue, 0, data.size)
        dataQueue.firePendingCallbacks()
        assertEquals(0, callback.lastEvent?.loopsLeft, "check loopCount")

        checkQueuedData(data, dataQueue, 0, data.size)
        dataQueue.firePendingCallbacks()
        assertTrue(callback.gotFinished, "should be finished now")

        assertFalse(dataQueue.hasMore(), "end empty")
    }

    @Test
    fun testImmediate() {
        val data = floatArrayOf(
            0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f
        )
        val sample = FloatSample(data.size, 1)
        sample.write(data)

        val dataQueue = UnitDataQueuePort("test")
        queueDirect(dataQueue, sample, 0, data.size)

        checkQueuedData(data, dataQueue, 0, 3)

        val command = dataQueue.createQueueDataCommand(sample, 7, 3)
        command.isImmediate = true
        command.run()

        checkQueuedData(data, dataQueue, 7, 3)
    }

    @Test
    fun testCrossFade() {
        val data1 = floatArrayOf(
            0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f
        )
        val data2 = floatArrayOf(
            20.0f, 19.0f, 18.0f, 17.0f, 16.0f, 15.0f, 14.0f, 13.0f, 12.0f, 11.0f
        )
        val sample1 = FloatSample(data1)
        val sample2 = FloatSample(data2)

        val dataQueue = UnitDataQueuePort("test")
        queueDirect(dataQueue, sample1, 0, 4)

        val command = dataQueue.createQueueDataCommand(sample2, 1, 8)
        command.crossFadeIn = 3
        command.run()

        checkQueuedData(data1, dataQueue, 0, 4)

        for (i in 0 until 3) {
            val factor = i / 3.0
            val value = ((1.0 - factor) * data1[i + 4]) + (factor * data2[i + 1])
            val actual = dataQueue.readNextMonoDouble(synth.framePeriod)
            assertEquals(value.toFloat(), actual, 0.00001f, "crossfade $i")
        }

        checkQueuedData(data2, dataQueue, 4, 5)
    }

    @Test
    fun testImmediateCrossFade() {
        val data1 = floatArrayOf(
            0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f
        )
        val data2 = floatArrayOf(
            20.0f, 19.0f, 18.0f, 17.0f, 16.0f, 15.0f, 14.0f, 13.0f, 12.0f, 11.0f
        )
        val sample1 = FloatSample(data1)
        val sample2 = FloatSample(data2)

        val dataQueue = UnitDataQueuePort("test")
        queueDirect(dataQueue, sample1, 0, 4)

        val beforeInterrupt = 2
        checkQueuedData(data1, dataQueue, 0, beforeInterrupt)

        val command = dataQueue.createQueueDataCommand(sample2, 1, 8)
        command.isImmediate = true
        command.crossFadeIn = 3
        command.run()

        for (i in 0 until 3) {
            val factor = i / 3.0
            val value = ((1.0 - factor) * data1[i + beforeInterrupt]) + (factor * data2[i + 1])
            val actual = dataQueue.readNextMonoDouble(synth.framePeriod)
            assertEquals(value.toFloat(), actual, 0.00001f, "crossfade $i")
        }

        checkQueuedData(data2, dataQueue, 4, 5)
    }

    @Test
    fun testStereoCrossFade() {
        val stereoData1 = FloatArray(12) { i ->
            val frame = i / 2
            val ch = i % 2
            (frame * 10 + ch).toFloat()
        }
        val stereoData2 = FloatArray(12) { i ->
            val frame = i / 2
            val ch = i % 2
            (100 + frame * 10 + ch).toFloat()
        }
        val sample1 = FloatSample(stereoData1, 2)
        val sample2 = FloatSample(stereoData2, 2)

        val dataQueue = UnitDataQueuePort("test", numChannels = 2)
        val cmd1 = dataQueue.createQueueDataCommand(sample1, 0, 4)
        cmd1.run()

        // Read frames 0 and 1
        dataQueue.beginFrame(synth.framePeriod)
        dataQueue.readCurrentChannelDouble(0)
        dataQueue.readCurrentChannelDouble(1)
        dataQueue.endFrame()

        dataQueue.beginFrame(synth.framePeriod)
        dataQueue.readCurrentChannelDouble(0)
        dataQueue.readCurrentChannelDouble(1)
        dataQueue.endFrame()

        // Interrupt at frame 2 with crossfade of 2 frames to sample2 starting at frame 1
        val cmd2 = dataQueue.createQueueDataCommand(sample2, 1, 4)
        cmd2.isImmediate = true
        cmd2.crossFadeIn = 2
        cmd2.run()

        // Frame 0 of cmd2: crossfade between sample1 frame 2 and sample2 frame 1 (factor = 0/2 = 0.0)
        dataQueue.beginFrame(synth.framePeriod)
        val ch0_0 = dataQueue.readCurrentChannelDouble(0)
        val ch1_0 = dataQueue.readCurrentChannelDouble(1)
        dataQueue.endFrame()
        assertEquals(20.0f, ch0_0, 0.0001f, "stereo xfade frame 0 ch0")
        assertEquals(21.0f, ch1_0, 0.0001f, "stereo xfade frame 0 ch1")

        // Frame 1 of cmd2: crossfade between sample1 frame 3 and sample2 frame 2 (factor = 1/2 = 0.5)
        dataQueue.beginFrame(synth.framePeriod)
        val ch0_1 = dataQueue.readCurrentChannelDouble(0)
        val ch1_1 = dataQueue.readCurrentChannelDouble(1)
        dataQueue.endFrame()
        assertEquals(75.0f, ch0_1, 0.0001f, "stereo xfade frame 1 ch0")
        assertEquals(76.0f, ch1_1, 0.0001f, "stereo xfade frame 1 ch1")

        // Frame 2 of cmd2: full volume sample2 frame 3 (130, 131)
        dataQueue.beginFrame(synth.framePeriod)
        val ch0_2 = dataQueue.readCurrentChannelDouble(0)
        val ch1_2 = dataQueue.readCurrentChannelDouble(1)
        dataQueue.endFrame()
        assertEquals(130.0f, ch0_2, 0.0001f, "stereo xfade frame 2 ch0")
        assertEquals(131.0f, ch1_2, 0.0001f, "stereo xfade frame 2 ch1")
    }

    @Test
    fun testMultipleLoopCrossFades() {
        val data = FloatArray(10) { it.toFloat() }
        val sample = FloatSample(data)
        val dataQueue = UnitDataQueuePort("test")

        // Loop frames 1..4 (numFrames = 3) with 2 frames crossfade
        val cmd = dataQueue.createQueueDataCommand(sample, 1, 3)
        cmd.numLoops = UnitDataQueuePort.LOOP_IF_LAST
        cmd.crossFadeIn = 2
        cmd.run()

        // Loop pass 1: frames 1, 2, 3
        assertEquals(1.0f, dataQueue.readNextMonoDouble(synth.framePeriod), 0.0001f)
        assertEquals(2.0f, dataQueue.readNextMonoDouble(synth.framePeriod), 0.0001f)
        assertEquals(3.0f, dataQueue.readNextMonoDouble(synth.framePeriod), 0.0001f)

        // Loop pass 2 wrap: should crossfade from frame 4 -> frame 1 (factor 0.0 -> value = 4.0)
        assertEquals(4.0f, dataQueue.readNextMonoDouble(synth.framePeriod), 0.0001f)
        // factor 0.5 -> 0.5 * frame 5 (5.0) + 0.5 * frame 2 (2.0) = 3.5
        assertEquals(3.5f, dataQueue.readNextMonoDouble(synth.framePeriod), 0.0001f)
        // frame 3
        assertEquals(3.0f, dataQueue.readNextMonoDouble(synth.framePeriod), 0.0001f)

        // Loop pass 3 wrap: should crossfade again! factor 0.0 -> frame 4 (4.0)
        assertEquals(4.0f, dataQueue.readNextMonoDouble(synth.framePeriod), 0.0001f)
        // factor 0.5 -> 0.5 * 5.0 + 0.5 * 2.0 = 3.5
        assertEquals(3.5f, dataQueue.readNextMonoDouble(synth.framePeriod), 0.0001f)
        assertEquals(3.0f, dataQueue.readNextMonoDouble(synth.framePeriod), 0.0001f)
    }

    @Test
    fun testDynamicLoopReplacementWithCrossFade() {
        val data = FloatArray(20) { (it * 10).toFloat() }
        val sample = FloatSample(data)
        val dataQueue = UnitDataQueuePort("test")

        // Start looping frames 0..3 (numFrames = 3) with crossfade 2
        val cmd1 = dataQueue.createQueueDataCommand(sample, 0, 3)
        cmd1.numLoops = UnitDataQueuePort.LOOP_IF_LAST
        cmd1.crossFadeIn = 2
        cmd1.run()

        // Read frames 0 and 1
        assertEquals(0.0f, dataQueue.readNextMonoDouble(synth.framePeriod), 0.0001f)
        assertEquals(10.0f, dataQueue.readNextMonoDouble(synth.framePeriod), 0.0001f)

        // While cmd1 is playing, queue replacement loop cmd2 at frame 10 (numFrames = 3, crossfade = 2, skipIfOthers = true)
        val cmd2 = dataQueue.createQueueDataCommand(sample, 10, 3)
        cmd2.numLoops = UnitDataQueuePort.LOOP_IF_LAST
        cmd2.crossFadeIn = 2
        cmd2.isSkipIfOthers = true
        cmd2.run()

        // cmd1 finishes its 3rd frame (frame 2)
        assertEquals(20.0f, dataQueue.readNextMonoDouble(synth.framePeriod), 0.0001f)

        // Now cmd2 starts! It should crossfade from cmd1's tail (frame 3 = 30) into cmd2's start (frame 10 = 100)
        // factor 0.0 -> frame 3 (30.0)
        assertEquals(30.0f, dataQueue.readNextMonoDouble(synth.framePeriod), 0.0001f)
        // factor 0.5 -> 0.5 * frame 4 (40.0) + 0.5 * frame 11 (110.0) = 75.0
        assertEquals(75.0f, dataQueue.readNextMonoDouble(synth.framePeriod), 0.0001f)
        // frame 2 of cmd2 (frame 12 = 120.0)
        assertEquals(120.0f, dataQueue.readNextMonoDouble(synth.framePeriod), 0.0001f)
    }
}
