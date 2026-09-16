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

import com.softsynth.ksyn.data.SequentialData

/**
 * A command that can be used to queue SequentialData to a UnitDataQueuePort.
 *
 * @author Phil Burk 2009 Mobileer Inc
 */
abstract class QueueDataCommand(
    port: UnitDataQueuePort,
    sequentialData: SequentialData,
    startFrame: Int,
    numFrames: Int
) : QueueDataEvent(port) {
    var crossfadeData: SequentialDataCrossfade = SequentialDataCrossfade()
    var currentData: SequentialData = sequentialData
    
    var callback: UnitDataQueueCallback? = null

    init {
        val maxFrames = sequentialData.numFrames
        require(startFrame + numFrames <= maxFrames) { "tried to queue past end of data, ${startFrame + numFrames}" }
        require(startFrame >= 0) { "tried to queue before start of data, $startFrame" }
        
        super.sequentialData = sequentialData
        super.startFrame = startFrame
        super.numFrames = numFrames
    }

    open val isEndBlock: Boolean
        get() {
            if (numLoops == UnitDataQueuePort.LOOP_IF_LAST || numLoops > 0) return false
            if (isAutoStop) return true
            val data = sequentialData ?: return true
            return startFrame + numFrames >= data.numFrames
        }

    abstract fun run()
}
