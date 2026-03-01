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

import com.softsynth.ksyn.util.PseudoRandom

/**
 * Use a random function to schedule grains.
 * 
 * @author Phil Burk (C) 2011 Mobileer Inc
 */
class StochasticGrainScheduler : GrainScheduler {
    val MIN_DENSITY = 0.00000001
    val MIN_GAP_RANGE = 0.00000001

    val pseudoRandom = PseudoRandom()

    override fun nextDuration(suggestedDuration: Double): Double {
        return suggestedDuration
    }

    override fun nextGap(duration: Double, density: Double): Double {
        val dens = if (density < MIN_DENSITY) MIN_DENSITY else density
        val rawGap = duration * (1.0 - dens) / dens
        val gapRange = rawGap.coerceAtLeast(MIN_GAP_RANGE)
        return pseudoRandom.nextRandomDouble() * gapRange
    }
}
