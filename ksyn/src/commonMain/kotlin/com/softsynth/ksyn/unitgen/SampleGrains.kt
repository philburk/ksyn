/*
 * Copyright 2011 Phil Burk, Mobileer Inc
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

import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.ports.UnitInputPort

/**
 * A [GrainFarm] that uses a [FloatSample] as source material. Use a ramp or fader to move
 * smoothly through the sample by connecting to the [position] port.
 *
 * <pre><code>
 *    synth.add(sampleGrains = SampleGrains())
 *    sampleGrains.setSample(sample)
 *    sampleGrains.allocate(NUM_GRAINS)
 * </code></pre>
 *
 * @author Phil Burk (C) 2011 Mobileer Inc
 */
class SampleGrains : GrainFarm() {
    /** Normalized position in the sample from -1.0 (start) to +1.0 (end). */
    val position = UnitInputPort("Position", 0.0)
    /** Random variation added around [position] when starting each grain. */
    val positionRange = UnitInputPort("PositionRange", 0.1)

    private var sample: FloatSample? = null

    init {
        addPort(position)
        addPort(positionRange)
        position.setup(-1.0, 0.0, 1.0)
        positionRange.setup(0.0, 0.1, 1.0)
    }

    override fun allocate(numGrains: Int) {
        val grainArray = Array(numGrains) {
            val source = GrainSourceSample(sample)
            Grain(source, RaisedCosineEnvelope())
        }
        setGrainArray(grainArray)
    }

    override fun setupGrain(grain: Grain, i: Int) {
        val sampleGrainSource = grain.source as? GrainSourceSample
        if (sampleGrainSource != null) {
            sampleGrainSource.setSample(sample)
            val basePos = position.getValues()[i].toDouble()
            val range = positionRange.getValues()[i].toDouble()
            val randomizedPos = (basePos + range * randomizer.nextRandomTriangle()).coerceIn(-1.0, 1.0)
            sampleGrainSource.setPosition(randomizedPos)
        }
        super.setupGrain(grain, i)
    }

    fun setSample(sample: FloatSample?) {
        this.sample = sample
    }

    fun getSample(): FloatSample? = sample
}
