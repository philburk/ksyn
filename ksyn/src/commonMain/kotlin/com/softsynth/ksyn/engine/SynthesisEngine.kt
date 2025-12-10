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

package com.softsynth.ksyn.engine

import com.softsynth.ksyn.KSyn
import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.shared.time.ScheduledQueue
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.UnitGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.math.pow

//TODO Resolve problem with HearDAHDSR where "Rate" port.set is not reflected in knob. Engine not running.
//TODO new tutorial and docs on website
//TODO AutoStop on DAHDSR
//TODO Test/example SequentialData queueOn and queueOff

//TODO Abstract device interface. File device!
//TODO Measure thread switching sync, performance for multi-core synthesis. Use 4 core pro.
//TODO Optimize SineOscillatorPhaseModulated
//TODO More circuits.
//TODO DC blocker
//TODO Swing scope probe UIs, auto ranging

typealias SynthCommand = () -> Unit

/**
 * Internal implementation of JSyn Synthesizer. The public API is in the Synthesizer interface. This
 * class might be used directly internally.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 * @see Synthesizer
 */
class SynthesisEngine() : Synthesizer {
//
//    private var engineThread: EngineThread? = null
    private val commandQueue = ScheduledQueue<SynthCommand>()
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var inputBuffer: InterleavingBuffer? = null
    private var outputBuffer: InterleavingBuffer? = null
    private var inverseNyquist = 0.0
    override var frameCount: Long = 0
        private set

    var isPullDataEnabled = true

    @Volatile
    private var started = false
    override var frameRate = DEFAULT_FRAME_RATE
        private set(value) {
            field = value
            framePeriod = 1.0 / value
        }
    override var framePeriod = 1.0 / DEFAULT_FRAME_RATE
        private set

    // List of all units added to the synth.
    private val allUnitList = mutableListOf<UnitGenerator>()

    // List of running units.
    private val runningUnitList = mutableListOf<UnitGenerator>()
    private val runningUnitListMutex = Mutex()

    // List of units stopping because of autoStop.
    private val stoppingUnitList = mutableListOf<UnitGenerator>()
    private val stoppingUnitListMutex = Mutex()

    private var loadAnalyzer: LoadAnalyzer? = null
    private val audioTasks = mutableListOf<Runnable>()

    @Volatile
    var outputLatency = 0.0
        private set

    @Volatile
    var inputLatency = 0.0
        private set

    override val version = KSyn.VERSION
    override val versionCode = KSyn.VERSION_CODE

    override fun toString() = "KSyn ${KSyn.VERSION_TEXT}"

    private fun setupAudioBuffers(numInputChannels: Int, numOutputChannels: Int) {
        inputBuffer = if (numInputChannels > 0) {
            InterleavingBuffer(FRAMES_PER_BUFFER, Synthesizer.FRAMES_PER_BLOCK, numInputChannels)
        } else null
        outputBuffer = if (numOutputChannels > 0) {
            InterleavingBuffer(FRAMES_PER_BUFFER, Synthesizer.FRAMES_PER_BLOCK, numOutputChannels)
        } else null
    }

    fun terminate() {}

    inner class InterleavingBuffer(framesPerBuffer: Int, framesPerBlock: Int, val samplesPerFrame: Int) {
        val interleavedBuffer = DoubleArray(framesPerBuffer * samplesPerFrame)
        private val blockBuffers = Array(samplesPerFrame) { ChannelBlockBuffer(framesPerBlock) }

        fun deinterleave(inIndex: Int): Int {
            var currentInIndex = inIndex
            for (jf in 0 until Synthesizer.FRAMES_PER_BLOCK) {
                for (iob in 0 until samplesPerFrame) {
                    blockBuffers[iob].values[jf] = interleavedBuffer[currentInIndex++]
                }
            }
            return currentInIndex
        }

        fun interleave(outIndex: Int): Int {
            var currentOutIndex = outIndex
            for (jf in 0 until Synthesizer.FRAMES_PER_BLOCK) {
                for (iob in 0 until samplesPerFrame) {
                    interleavedBuffer[currentOutIndex++] = blockBuffers[iob].values[jf]
                }
            }
            return currentOutIndex
        }

        fun getChannelBuffer(i: Int): DoubleArray = blockBuffers[i].values

        fun clear() {
            blockBuffers.forEach { it.clear() }
        }
    }

    class ChannelBlockBuffer(framesPerBlock: Int) {
        val values = DoubleArray(framesPerBlock)

        fun clear() {
            values.fill(0.0)
        }
    }

    override fun start() {
        start(DEFAULT_FRAME_RATE, 0, 2)
    }

    // TODO @Synchronized
    override fun start(frameRate: Int, numInputChannels: Int, numOutputChannels: Int) {
        if (started) return

        this.frameRate = frameRate
        this.framePeriod = 1.0 / frameRate
        inverseNyquist = 2.0 / frameRate
        setupAudioBuffers(numInputChannels, numOutputChannels)
        started = true
    }

    override val isRunning: Boolean
        get() = started

    // TODO @Synchronized
    override fun stop() {
        if (!started) {
            println("KSyn already stopped.")
            return
        }

        // TODO: Coroutine-based thread joining

        // runningUnitListMutex.withLock { // Use withLock for coroutines
        //     runningUnitList.clear()
        // }
        started = false
    }

//    private inner class EngineThread(
//        private val inputDeviceID: Int,
//        private val numInputChannels: Int,
//        private val outputDeviceID: Int,
//        private val numOutputChannels: Int
//    ) : Thread() {
//        @Volatile
//        private var go = true
//
//        fun requestStop() {
//            go = false
//            interrupt() // Interrupt sleep
//        }
//
//        override fun run() {
//            // Audio device management will be removed
//            // For now, this part remains platform-specific
//
//            try {
//                loadAnalyzer = LoadAnalyzer()
//                while (go) {
//                    // Non-blocking sleep for non-realtime
//                    if (!isRealTime) {
//                        // In a coroutine world, this would be `delay`
//                        sleep(2)
//                    }
//
//                    loadAnalyzer?.start()
//                    runAudioTasks()
//                    generateNextBuffer()
//                    loadAnalyzer?.stop()
//                }
//            } catch (e: Throwable) {
//                e.printStackTrace()
//                go = false
//            } finally {
//                println("KSyn synthesis thread exiting.")
//            }
//        }
//    }

    private fun runAudioTasks() {
        audioTasks.forEach { it.run() }
    }

    suspend fun generateNextBuffer() {
        var outIndex = 0
        var inIndex = 0
        for (i in 0 until BLOCKS_PER_BUFFER) {
            inputBuffer?.let { inIndex = it.deinterleave(inIndex) }

            val timeStamp = createTimeStamp()
            processScheduledCommands(timeStamp)
            clearBlockBuffers()
            synthesizeBuffer()

            outputBuffer?.let { outIndex = it.interleave(outIndex) }
            frameCount += Synthesizer.FRAMES_PER_BLOCK
        }
    }

    override val currentTime: Double
        get() = frameCount * framePeriod

    override fun createTimeStamp(): TimeStamp = TimeStamp(currentTime)

    private fun processScheduledCommands(timeStamp: TimeStamp) {
        var timeList = commandQueue.removeNextList(timeStamp)
        while (timeList != null) {
            for (command in timeList) {
                command()
            }
            timeList = commandQueue.removeNextList(timeStamp)
        }
    }

    override fun scheduleCommand(timeStamp: TimeStamp, command: SynthCommand) {
        // This check is problematic for multi-threaded/coroutine environments
        // if (Thread.currentThread() == engineThread && timeStamp.time <= currentTime) {
        //     command.run()
        // } else {
        commandQueue.add(timeStamp, command)
        // }
    }

    override fun scheduleCommand(time: Double, command: SynthCommand) {
        scheduleCommand(TimeStamp(time), command)
    }

    override fun queueCommand(command: SynthCommand) {
        scheduleCommand(createTimeStamp(), command)
    }

    override fun clearCommandQueue() {
        commandQueue.clear()
    }

    private fun clearBlockBuffers() {
        outputBuffer?.clear()
    }

    private suspend fun synthesizeBuffer() {
        stoppingUnitListMutex.withLock {
            runningUnitListMutex.withLock {
                val iterator = runningUnitList.listIterator()
                while (iterator.hasNext()) {
                    val unit = iterator.next()
                    if (isPullDataEnabled) {
                        unit.pullData(frameCount)
                    } else {
                        unit.generate()
                    }
                }
                // Remove any units that got auto stopped.
                for (ugen in stoppingUnitList) {
                    runningUnitList.remove(ugen)
                    ugen.flattenOutputs()
                }
            }
            stoppingUnitList.clear()
        }
    }

    fun getInputBuffer(i: Int): DoubleArray {
        return inputBuffer?.getChannelBuffer(i)
            ?: throw RuntimeException("Audio Input not configured in start() method.")
    }

    fun getOutputBuffer(i: Int): DoubleArray {
        return outputBuffer?.getChannelBuffer(i)
            ?: throw RuntimeException("Audio Output not configured in start() method.")
    }

    fun getInterleavedBuffer(): DoubleArray {
        return outputBuffer?.interleavedBuffer
            ?: throw RuntimeException("Audio Output not configured in start() method.")
    }

    private suspend fun internalStopUnit(unit: UnitGenerator) {
        runningUnitListMutex.withLock {
            runningUnitList.remove(unit)
        }
        unit.flattenOutputs()
    }

    suspend fun autoStopUnit(unitGenerator: UnitGenerator) {
        stoppingUnitListMutex.withLock {
            stoppingUnitList.add(unitGenerator)
        }
    }

    override fun startUnit(unit: UnitGenerator, time: Double) {
        startUnit(unit, TimeStamp(time))
    }

    override fun stopUnit(unit: UnitGenerator, time: Double) {
        stopUnit(unit, TimeStamp(time))
    }

    override fun startUnit(unit: UnitGenerator, timeStamp: TimeStamp) {
        if (unit.circuit == null) {
            scheduleCommand(timeStamp) { internalStartUnit(unit) }
        }
    }

    override fun stopUnit(unit: UnitGenerator, timeStamp: TimeStamp) {
        scheduleCommand(timeStamp) {
            // Launch a non-blocking coroutine instead of using runBlocking
            engineScope.launch {
                internalStopUnit(unit)
            }
        }
    }
    override fun startUnit(unit: UnitGenerator) {
        startUnit(unit, createTimeStamp())
    }

    override fun stopUnit(unit: UnitGenerator) {
        stopUnit(unit, createTimeStamp())
    }

    private fun internalStartUnit(unit: UnitGenerator) {
        if (unit.circuit == null) {
            // Use a coroutine-safe way to add to the list
            // runningUnitListMutex.withLock {
            if (!runningUnitList.contains(unit)) {
                runningUnitList.add(unit)
            }
            // }
        }
    }

    fun getInverseNyquist(): Double = inverseNyquist

    // TODO rename to convertTimeToExponentialDecayScaler
    fun convertTimeToExponentialScaler(duration: Double): Double {
        val numFrames = duration * frameRate
        return DB90.pow(1.0 / numFrames)
    }

//    override fun addAudioTask(blockTask: Runnable) {
//        audioTasks.add(blockTask)
//    }
//
//    override fun removeAudioTask(blockTask: Runnable) {
//        audioTasks.remove(blockTask)
//    }

    override val usage: Double
        get() = loadAnalyzer?.averageLoad ?: 0.0

    override var isRealTime: Boolean = true

    override fun add(ugen: UnitGenerator) {
        ugen.synthesisEngine = this
        allUnitList.add(ugen)
    }

    override fun remove(ugen: UnitGenerator) {
        allUnitList.remove(ugen)
    }

    override suspend fun sleepUntil(time: Double) {
        var timeToSleep = time - currentTime
        while (timeToSleep > 0.0) {
            if (isRealTime) {
                val msecToSleep = (1000 * timeToSleep).toLong()
                if (msecToSleep > 0) {
                    kotlinx.coroutines.delay(msecToSleep)
                }
            } else {
                generateNextBuffer()
            }
            timeToSleep = time - currentTime
        }
    }

    override suspend fun sleepFor(duration: Double) {
        sleepUntil(currentTime + duration)
    }

    override suspend fun renderBuffer(): DoubleArray {
        generateNextBuffer()
        return getInterleavedBuffer()
    }

    fun printConnections() {
        if (isPullDataEnabled) {
            runningUnitList.forEach { it.printConnections() }
        }
    }

    companion object {
        const val BLOCKS_PER_BUFFER = 8
        const val FRAMES_PER_BUFFER = Synthesizer.FRAMES_PER_BLOCK * BLOCKS_PER_BUFFER
        const val MAX_THREAD_STOP_TIME = 2000
        const val DEFAULT_FRAME_RATE = 44100

        /** A fraction corresponding to exactly -96 dB. */
        const val DB96 = 1.0 / 63095.73444801943

        /** A fraction that is approximately -90.3 dB. Defined as 1 bit of an S16. */
        const val DB90 = 1.0 / (1 shl 15)

        /** Convert a short value to a double in the range -1.0 to almost 1.0. */
        fun convertShortToDouble(sdata: Short): Double = sdata * (1.0 / Short.MAX_VALUE)

        /**
         * Convert a double value in the range -1.0 to almost 1.0 to a short.
         * Double value is clipped before converting.
         */
        fun convertDoubleToShort(d: Double): Short {
            val maxValue = (Short.MAX_VALUE - 1).toDouble() / Short.MAX_VALUE
            val clippedD = d.coerceIn(-1.0, maxValue)
            return (clippedD * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
