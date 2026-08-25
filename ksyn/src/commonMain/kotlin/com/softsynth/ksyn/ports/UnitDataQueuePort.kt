/*
 * Copyright 2010 Phil Burk, Mobileer Inc
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

import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.data.SequentialData
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.AudioSample

/**
 * Queue for SequentialData, samples or envelopes
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
class UnitDataQueuePort(name: String, var numChannels: Int = 1) : UnitPort(name) {
    private val blocks = mutableListOf<QueueDataEvent>()
    private var currentBlock: QueueDataCommand? = null
    private var frameIndex = 0
    var normalizedRate = 0.0
        private set
    var frameCount: Long = 0
        private set
    private var autoStopPending = false
    var isTargetValid = false
        private set
    private var finishingBlock: QueueDataCommand? = null
    private var loopingBlock: QueueDataCommand? = null

    companion object {
        const val LOOP_IF_LAST = -1
    }

    private inner class QueuedBlock(queueableData: SequentialData, startFrame: Int, numFrames: Int) :
        QueueDataCommand(this@UnitDataQueuePort, queueableData, startFrame, numFrames) {

        override fun run() {
            // Remove last block if it can be skipped.
            if (blocks.isNotEmpty()) {
                val lastBlock = blocks.last()
                if (lastBlock.isSkipIfOthers) {
                    blocks.removeLast()
                }
            }

            // If we are crossfading then figure out where to crossfade from.
            if (crossFadeIn > 0) {
                if (isImmediate) {
                    // Queue will be cleared so fade in from current.
                    currentBlock?.let { setupCrossFade(it, frameIndex, this) }
                } else {
                    val endBlock = getEndBlock()
                    if (endBlock != null) {
                        setupCrossFade(endBlock, endBlock.startFrame + endBlock.numFrames, this)
                    }
                }
            }

            if (isImmediate) {
                clearQueue()
            }

            blocks.add(this)
        }
    }

    private fun getUnderlyingDataAndFrame(data: SequentialData, startFrame: Int): Pair<SequentialData, Int> {
        var d = data
        var f = startFrame
        while (d is SequentialDataCrossfade) {
            val tgt = d.target ?: break
            f += d.targetStartIndex / tgt.channelsPerFrame
            d = tgt
        }
        return Pair(d, f)
    }

    protected fun setupCrossFade(sourceCommand: QueueDataCommand, sourceStartFrame: Int, targetCommand: QueueDataCommand) {
        var crossFrames = targetCommand.crossFadeIn
        val (sourceData, realSourceStartFrame) = getUnderlyingDataAndFrame(sourceCommand.currentData, sourceStartFrame)
        val (targetData, realTargetStartFrame) = getUnderlyingDataAndFrame(targetCommand.currentData, targetCommand.startFrame)

        var effectiveSourceStartFrame = realSourceStartFrame
        val remainingSource = (sourceData.numFrames - effectiveSourceStartFrame.coerceAtLeast(0)).coerceAtLeast(0)

        // If remainingSource is less than crossFrames (e.g. loop reached the end of the sample),
        // adjust effectiveSourceStartFrame backwards so we can crossfade from the tail of the sample.
        if (remainingSource < crossFrames && sourceData.numFrames >= crossFrames) {
            effectiveSourceStartFrame = (sourceData.numFrames - crossFrames).coerceAtLeast(0)
        } else if (crossFrames > remainingSource) {
            crossFrames = remainingSource
        }

        if (crossFrames > 0) {
            // The SequentialDataCrossfade should continue to the end of the target
            // so that we can crossfade from it to the target.
            val remainingTarget = (targetData.numFrames - realTargetStartFrame.coerceAtLeast(0)).coerceAtLeast(0)
            targetCommand.crossfadeData.setup(
                sourceData,
                effectiveSourceStartFrame,
                crossFrames,
                targetData,
                realTargetStartFrame,
                remainingTarget)
            targetCommand.currentData = targetCommand.crossfadeData
            targetCommand.startFrame = 0
        }
    }

    fun createQueueDataCommand(queueableData: SequentialData): QueueDataCommand {
        return createQueueDataCommand(queueableData, 0, queueableData.numFrames)
    }

    fun createQueueDataCommand(queueableData: SequentialData, startFrame: Int, numFrames: Int): QueueDataCommand {
        val data = if (queueableData.channelsPerFrame != this.numChannels && this.numChannels == 1 && queueableData is FloatSample) {
            queueableData.toMono()
        } else {
            queueableData
        }
        if (data.channelsPerFrame != this.numChannels) {
            throw RuntimeException("Tried to queue " + data.channelsPerFrame + " channel data to a " + numChannels + " channel port.")
        }
        return QueuedBlock(data, startFrame, numFrames)
    }

    fun getEndBlock(): QueueDataCommand? {
        if (blocks.isNotEmpty()) {
            return blocks.last() as QueueDataCommand
        } else if (currentBlock != null) {
            return currentBlock
        } else {
            return null
        }
    }

    fun setCurrentBlock(currentBlock: QueueDataCommand?) {
        this.currentBlock = currentBlock
    }

    fun firePendingCallbacks() {
        if (loopingBlock != null) {
            loopingBlock?.callback?.looped(currentBlock!!)
            loopingBlock = null
        }
        if (finishingBlock != null) {
            finishingBlock?.callback?.finished(currentBlock ?: finishingBlock!!) // FIXME - Should this pass finishingBlock?!
            finishingBlock = null
        }
    }

    fun hasMore(): Boolean {
        return (currentBlock != null) || blocks.isNotEmpty()
    }

    private fun checkBlock() {
        if (currentBlock == null && blocks.isNotEmpty()) {
            val block = blocks.removeAt(0) as QueueDataCommand
            setCurrentBlock(block)
            frameIndex = block.startFrame
            block.loopsLeft = block.numLoops
            block.callback?.started(block)
        }
    }

    private fun advanceFrameIndex() {
        frameIndex += 1
        frameCount += 1
        val cBlock = currentBlock ?: return
        
        // Are we done with this block?
        if (frameIndex >= (cBlock.startFrame + cBlock.numFrames)) {
            // Should we loop on this block based on a counter?
            if (cBlock.loopsLeft > 0) {
                cBlock.loopsLeft -= 1
                loopToStart()
            }
            // Should we loop forever on this block?
            else if (blocks.isEmpty() && cBlock.loopsLeft < 0) {
                loopToStart()
            }
            // We are done.
            else {
                if (cBlock.isAutoStop) {
                    autoStopPending = true
                }
                finishingBlock = cBlock
                setCurrentBlock(null)
            }
        }
    }

    private fun loopToStart() {
        val cBlock = currentBlock!!
        if (cBlock.crossFadeIn > 0) {
            setupCrossFade(cBlock, frameIndex, cBlock)
        }
        frameIndex = cBlock.startFrame
        loopingBlock = cBlock
    }

    fun readCurrentChannelDouble(channelIndex: Int): AudioSample {
        return currentBlock!!.currentData.readSample((frameIndex * numChannels) + channelIndex)
    }

    fun writeCurrentChannelDouble(channelIndex: Int, value: AudioSample) {
        currentBlock!!.currentData.writeSample((frameIndex * numChannels) + channelIndex, value)
    }

    fun beginFrame(synthesisPeriod: Double) {
        checkBlock()
        currentBlock?.let {
            normalizedRate = it.currentData.getRateScaler(frameIndex, synthesisPeriod)
        }
    }

    fun endFrame() {
        advanceFrameIndex()
        isTargetValid = true
    }

    fun readNextMonoDouble(synthesisPeriod: Double): AudioSample {
        beginFrame(synthesisPeriod)
        val value = currentBlock?.currentData?.readSample(frameIndex * numChannels) ?: 0.0f
        endFrame()
        return value
    }

    /** Clear the queue. Internal use only. */
    protected fun clearQueue() {
        blocks.clear()
        setCurrentBlock(null)
        isTargetValid = false
        autoStopPending = false
    }

    /** Queue the data to the port at a future time. */
    fun queue(queueableData: SequentialData, startFrame: Int, numFrames: Int, timeStamp: TimeStamp) {
        val command = createQueueDataCommand(queueableData, startFrame, numFrames)
        scheduleCommand(timeStamp.time) { command.run() }
    }

    /**
     * Queue the data to the port at a future time. Command will clear the queue before executing.
     */
    fun queueImmediate(queueableData: SequentialData, startFrame: Int, numFrames: Int, timeStamp: TimeStamp, crossFadeIn: Int = 0) {
        val command = createQueueDataCommand(queueableData, startFrame, numFrames)
        command.isImmediate = true
        command.crossFadeIn = crossFadeIn
        scheduleCommand(timeStamp.time) { command.run() }
    }

    /** Queue the data to the port at a future time. */
    fun queue(
        queueableData: SequentialData,
        startFrame: Int,
        numFrames: Int,
        timeStamp: TimeStamp,
        crossFadeIn: Int = 0,
        skipIfOthers: Boolean = false
    ) {
        val command = createQueueDataCommand(queueableData, startFrame, numFrames)
        command.crossFadeIn = crossFadeIn
        command.isSkipIfOthers = skipIfOthers
        scheduleCommand(timeStamp.time) { command.run() }
    }

    /** Queue the data to the port at a future time. */
    fun queueLoop(
        queueableData: SequentialData,
        startFrame: Int,
        numFrames: Int,
        timeStamp: TimeStamp,
        crossFadeIn: Int = 0,
        skipIfOthers: Boolean = false
    ) {
        queueLoop(queueableData, startFrame, numFrames, LOOP_IF_LAST, timeStamp, crossFadeIn, skipIfOthers)
    }

    /**
     * Queue the data to the port at a future time with a specified number of loops.
     */
    fun queueLoop(
        queueableData: SequentialData,
        startFrame: Int,
        numFrames: Int,
        numLoops: Int,
        timeStamp: TimeStamp,
        crossFadeIn: Int = 0,
        skipIfOthers: Boolean = false
    ) {
        val command = createQueueDataCommand(queueableData, startFrame, numFrames)
        command.numLoops = numLoops
        command.crossFadeIn = crossFadeIn
        command.isSkipIfOthers = skipIfOthers
        scheduleCommand(timeStamp.time) { command.run() }
    }

    /**
     * Queue the data to the port for looping at a future time. Command will clear the queue before executing.
     */
    fun queueLoopImmediate(queueableData: SequentialData, startFrame: Int, numFrames: Int, timeStamp: TimeStamp, crossFadeIn: Int = 0) {
        queueLoopImmediate(queueableData, startFrame, numFrames, LOOP_IF_LAST, timeStamp, crossFadeIn)
    }

    /**
     * Queue the data to the port for looping at a future time with a specified number of loops. Command will clear the queue before executing.
     */
    fun queueLoopImmediate(queueableData: SequentialData, startFrame: Int, numFrames: Int, numLoops: Int, timeStamp: TimeStamp, crossFadeIn: Int = 0) {
        val command = createQueueDataCommand(queueableData, startFrame, numFrames)
        command.isImmediate = true
        command.numLoops = numLoops
        command.crossFadeIn = crossFadeIn
        scheduleCommand(timeStamp.time) { command.run() }
    }

    /** Queue the entire data object for looping. */
    fun queueLoop(queueableData: SequentialData) {
        queueLoop(queueableData, 0, queueableData.numFrames)
    }

    /** Queue the data to the port for immediate use. */
    fun queueLoop(queueableData: SequentialData, startFrame: Int, numFrames: Int) {
        queueLoop(queueableData, startFrame, numFrames, LOOP_IF_LAST)
    }

    /**
     * Queue the data to the port for immediate use with a specified number of loops.
     */
    fun queueLoop(queueableData: SequentialData, startFrame: Int, numFrames: Int, numLoops: Int) {
        val command = createQueueDataCommand(queueableData, startFrame, numFrames)
        command.numLoops = numLoops
        queueCommand { command.run() }
    }

    /** Queue the data to the port for immediate looping. */
    fun queueLoopImmediate(queueableData: SequentialData, startFrame: Int, numFrames: Int) {
        queueLoopImmediate(queueableData, startFrame, numFrames, LOOP_IF_LAST)
    }

    /**
     * Queue the data to the port for immediate looping with a specified number of loops.
     */
    fun queueLoopImmediate(queueableData: SequentialData, startFrame: Int, numFrames: Int, numLoops: Int) {
        val command = createQueueDataCommand(queueableData, startFrame, numFrames)
        command.isImmediate = true
        command.numLoops = numLoops
        queueCommand { command.run() }
    }

    /**
     * Queue the data to the port at a future time. Request that the unit stop when this block is
     * finished.
     */
    fun queueStop(queueableData: SequentialData, startFrame: Int, numFrames: Int, timeStamp: TimeStamp) {
        val command = createQueueDataCommand(queueableData, startFrame, numFrames)
        command.isAutoStop = true
        scheduleCommand(timeStamp.time) { command.run() }
    }

    /** Queue the data to the port through the command queue ASAP. */
    fun queue(queueableData: SequentialData, startFrame: Int, numFrames: Int) {
        val command = createQueueDataCommand(queueableData, startFrame, numFrames)
        queueCommand { command.run() }
    }

    /**
     * Queue entire amount of data with no options.
     *
     * @param queueableData
     */
    fun queue(queueableData: SequentialData) {
        queue(queueableData, 0, queueableData.numFrames)
    }

    /** Schedule queueOn now! */
    fun queueOn(queueableData: SequentialData) {
        getSynthesisEngine()?.let {
            queueOn(queueableData, it.createTimeStamp())
        }
    }

    /** Schedule queueOff now! */
    fun queueOff(queueableData: SequentialData) {
        queueOff(queueableData, false)
    }

    /** Schedule queueOff now! */
    fun queueOff(queueableData: SequentialData, ifStop: Boolean) {
        getSynthesisEngine()?.let {
            queueOff(queueableData, ifStop, it.createTimeStamp())
        }
    }

    /**
     * Convenience method that will queue the attack portion of a channelData and the sustain loop
     * if it exists. This could be used to implement a NoteOn method.
     */
    fun queueOn(queueableData: SequentialData, timeStamp: TimeStamp) {
        if (queueableData.sustainBegin < 0) {
            // no sustain loop, handle release
            if (queueableData.releaseBegin < 0) {
                // No loops
                queueImmediate(queueableData, 0, queueableData.numFrames, timeStamp)
            } else {
                queueImmediate(queueableData, 0, queueableData.releaseEnd, timeStamp)
                val size = queueableData.releaseEnd - queueableData.releaseBegin
                queueLoop(queueableData, queueableData.releaseBegin, size, timeStamp)
            }
        } else {
            // yes sustain loop
            if (queueableData.sustainEnd > 0) {
                val frontSize = queueableData.sustainBegin
                // Is there an initial portion before the sustain loop?
                if (frontSize > 0) {
                    queueImmediate(queueableData, 0, frontSize, timeStamp)
                }
                val loopSize = queueableData.sustainEnd - queueableData.sustainBegin
                if (loopSize > 0) {
                    queueLoop(queueableData, queueableData.sustainBegin, loopSize, timeStamp)
                }
            }
        }
    }

    /**
     * Convenience method that will queue the decay portion of a SequentialData object, or the gap
     * and release loop portions if they exist. This could be used to implement a NoteOff method.
     *
     * @param ifStop Will setAutostop(true) if release portion queued without a release loop. This will
     *         stop execution of the unit.
     */
    fun queueOff(queueableData: SequentialData, ifStop: Boolean, timeStamp: TimeStamp) {
        if (queueableData.sustainBegin >= 0) { /* Sustain loop? */
            val relSize = queueableData.releaseEnd - queueableData.releaseBegin
            if (queueableData.releaseBegin < 0) { /* Sustain loop, no release loop. */
                var susEnd = queueableData.sustainEnd
                var size = queueableData.numFrames - susEnd
                if (size <= 0) {
                    size = 1
                    susEnd = queueableData.numFrames - 1
                }
                if (ifStop) {
                    queueStop(queueableData, susEnd, size, timeStamp)
                } else {
                    queue(queueableData, susEnd, size, timeStamp)
                }
            } else if (queueableData.releaseBegin > queueableData.sustainEnd) {
                // Queue gap between sustain and release loop.
                queue(queueableData, queueableData.sustainEnd, queueableData.releaseEnd - queueableData.sustainEnd, timeStamp)
                if (relSize > 0) queueLoop(queueableData, queueableData.releaseBegin, relSize, timeStamp)
            } else if (relSize > 0) {
                // No gap between sustain and release.
                queueLoop(queueableData, queueableData.releaseBegin, relSize, timeStamp)
            }
        }
    }

    fun clear(timeStamp: TimeStamp) {
        scheduleCommand(timeStamp.time) { clearQueue() }
    }

    fun clear() {
        queueCommand { clearQueue() }
    }

    fun writeNextSample(value: AudioSample) {
        checkBlock()
        currentBlock?.currentData?.writeSample(frameIndex, value)
        advanceFrameIndex()
    }

    fun testAndClearAutoStop(): Boolean {
        val temp = autoStopPending
        autoStopPending = false
        return temp
    }
}
