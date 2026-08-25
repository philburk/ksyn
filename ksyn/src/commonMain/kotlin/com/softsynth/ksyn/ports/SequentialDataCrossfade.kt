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

import com.softsynth.ksyn.data.AudioSample

import com.softsynth.ksyn.data.SequentialData
import com.softsynth.ksyn.data.SequentialDataCommon

/**
 * A SequentialData object that will crossfade between two other SequentialData objects. The
 * crossfade is linear. This could, for example, be used to create a smooth transition between two
 * samples, or between two arbitrary regions in one sample. As an example, consider a sample that
 * has a length of 200000 frames. You could specify a sample loop that started arbitrarily at frame
 * 50000 and with a size of 30000 frames. Unless you got lucky with the zero crossings, it is likely
 * that you will hear a pop when this sample loops. To prevent the pop you could crossfade the
 * beginning of the loop with the region immediately after the end of the loop. To crossfade with
 * 5000 samples after the loop:
 *
 * `SequentialDataCrossfade xfade = new SequentialDataCrossfade(sample, (50000 + 30000), 5000, sample, 50000, 30000);`
 *
 * After the crossfade you will hear the rest of the target at full volume. There are two regions
 * that determine what is returned from readSample()
 * 1. Crossfade region with size crossFadeFrames. It fades smoothly from source to target.
 * 2. Steady region that is simply the target values with size (numFrames-crossFadeFrames).
 *
 * @author Phil Burk
 */
class SequentialDataCrossfade : SequentialDataCommon() {
    private var source: SequentialData? = null
    private var sourceStartIndex: Int = 0

    var target: SequentialData? = null
        private set
    var targetStartIndex: Int = 0
        private set

    var crossFadeFrames: Int = 0
        private set
    private var frameScaler: Double = 0.0

    /**
     * @param source SequentialData that will be at full volume at the beginning of the crossfade region.
     * @param sourceStartFrame Frame in source to begin the crossfade.
     * @param crossFadeFrames Number of frames in the crossfaded region.
     * @param target SequentialData that will be at full volume at the end of the crossfade region.
     * @param targetStartFrame Frame in target to begin the crossfade.
     * @param numFrames total number of frames in this data object.
     */
    fun setup(
        source: SequentialData, sourceStartFrame: Int, crossFadeFrames: Int,
        target: SequentialData, targetStartFrame: Int, numFrames: Int
    ) {
        var src = source
        var srcStartFrame = sourceStartFrame
        var tgt = target
        var tgtStartFrame = targetStartFrame
        
        // There is a danger that we might nest SequentialDataCrossfades deeply
        // as source. If past crossfade region then pull out the target.
        while (src is SequentialDataCrossfade) {
            val crossfade = src
            // If we are starting past the crossfade region then just use the
            // target.
            if (srcStartFrame >= crossfade.crossFadeFrames) {
                src = crossfade.target ?: break
                srcStartFrame += crossfade.targetStartIndex / src.channelsPerFrame
            } else {
                break
            }
        }

        while (tgt is SequentialDataCrossfade) {
            val crossfade = tgt
            tgt = crossfade.target ?: break
            tgtStartFrame += crossfade.targetStartIndex / tgt.channelsPerFrame
        }

        this.source = src
        this.target = tgt
        this.sourceStartIndex = srcStartFrame * src.channelsPerFrame
        this.crossFadeFrames = crossFadeFrames
        this.targetStartIndex = tgtStartFrame * tgt.channelsPerFrame

        frameScaler = if (crossFadeFrames == 0) 1.0 else (1.0 / crossFadeFrames)
        this.setNumFrames(numFrames)
    }

    override fun writeSample(index: Int, value: AudioSample) {}

    override fun readSample(index: Int): AudioSample {
        val src = source ?: return 0.0f
        val tgt = target ?: return 0.0f
        val frame = index / src.channelsPerFrame
        return if (frame < crossFadeFrames) {
            val factor = (frame * frameScaler).toFloat()
            var value = (1.0f - factor) * src.readSample(index + sourceStartIndex)
            value += (factor * tgt.readSample(index + targetStartIndex))
            value
        } else {
            tgt.readSample(index + targetStartIndex)
        }
    }

    override fun writeDouble(index: Int, value: Double) {}

    override fun readDouble(index: Int): Double {
        return readSample(index).toDouble()
    }

    override fun getRateScaler(index: Int, synthesisPeriod: Double): Double {
        return target?.getRateScaler(index, synthesisPeriod) ?: 1.0
    }

    override val channelsPerFrame: Int
        get() = target?.channelsPerFrame ?: 1
}
