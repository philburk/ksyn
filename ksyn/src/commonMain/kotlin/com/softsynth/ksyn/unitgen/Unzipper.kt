/*
 * Copyright 2014 Phil Burk, Mobileer Inc
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

import com.softsynth.ksyn.data.AudioSample

/**
 * Used inside UnitGenerators for fast smoothing of inputs.
 * 
 * @author Phil Burk (C) 2014 Mobileer Inc
 */
class Unzipper {
    private var target: AudioSample = 0.0f
    private var delta: AudioSample = 0.0f
    private var current: AudioSample = 0.0f
    private var counter: Int = 0

    companion object {
        // About 30 msec. Power of 2 so divide should be faster.
        private const val NUM_STEPS = 1024
    }

    // We can use float here because the number of steps is small.
    fun smooth(input: AudioSample): AudioSample {
        if (input != target) {
            target = input
            delta = (target - current) / NUM_STEPS
            counter = NUM_STEPS
        }
        if (counter > 0) {
            if (--counter == 0) {
                current = target
            } else {
                current += delta
            }
        }
        return current
    }
}
