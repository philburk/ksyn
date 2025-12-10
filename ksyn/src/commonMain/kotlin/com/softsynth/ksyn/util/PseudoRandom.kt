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

package com.softsynth.ksyn.util

import com.softsynth.ksyn.AudioSample
import com.softsynth.ksyn.toSample
import kotlin.random.Random
import kotlin.jvm.JvmOverloads

/**
 * Pseudo-random numbers using predictable and fast linear-congruential method.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc Translated from 'C' to Java by Lisa
 * Tolentino.
 */
class PseudoRandom @JvmOverloads constructor(seed: Int = Random.nextInt()) {
    private var seed: Long = 99887766

    init {
        setSeed(seed)
    }

    fun setSeed(seed: Int) {
        this.seed = seed.toLong()
    }

    fun getSeed(): Int {
        return seed.toInt()
    }

    /**
     * Returns the next random double from 0.0 to 1.0
     *
     * @return value from 0.0 to 1.0
     */
    fun random(): Double {
        val positiveInt = nextRandomInteger() and 0x7FFFFFFF
        return positiveInt * INT_TO_DOUBLE
    }

    /**
     * Returns the next random double from -1.0 to 1.0
     *
     * @return value from -1.0 to 1.0
     */
    fun nextRandomDouble(): Double {
        return nextRandomInteger() * INT_TO_DOUBLE
    }

    fun nextRandomSample(): AudioSample {
        return nextRandomInteger() * INT_TO_DOUBLE.toSample()
    }

    /** Calculate random 32 bit number using linear-congruential method.  */
    fun nextRandomInteger(): Int {
        // Use values for 64-bit sequence from MMIX by Donald Knuth.
        seed = (seed * 6364136223846793005L) + 1442695040888963407L
        return (seed shr 32).toInt() // The higher bits have a longer sequence.
    }

    fun choose(range: Int): Int {
        val positiveInt = (nextRandomInteger() and 0x7FFFFFFF).toLong()
        val temp = positiveInt * range
        return (temp shr 31).toInt()
    }

    companion object {
        // We must shift 1L or else we get a negative number!
        private val INT_TO_DOUBLE = (1.0 / (1L shl 31))
    }
}
