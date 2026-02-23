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
    
    val pseudoRandom = PseudoRandom()

    override fun nextDuration(suggestedDuration: Double): Double {
        return suggestedDuration
    }

    override fun nextGap(duration: Double, density: Double): Double {
        var dens = density
        if (dens < 0.00000001) {
            dens = 0.00000001
        }
        val gapRange = duration * (1.0 - dens) / dens
        return pseudoRandom.nextRandomDouble() * gapRange
    }
}
