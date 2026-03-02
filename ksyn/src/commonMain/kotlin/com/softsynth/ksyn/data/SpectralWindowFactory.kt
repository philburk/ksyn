/*
 * Copyright 2013 Phil Burk, Mobileer Inc
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

package com.softsynth.ksyn.data

import kotlin.math.PI
import kotlin.math.cos

/**
 * Create shared windows as needed for use with FFTs and IFFTs.
 *
 * @author Phil Burk (C) 2013 Mobileer Inc
 */
object SpectralWindowFactory {
    private const val NUM_WINDOWS = 16
    private const val MIN_SIZE_LOG_2 = 2
    private val hammingWindows = arrayOfNulls<HammingWindow>(NUM_WINDOWS)
    private val hannWindows = arrayOfNulls<HannWindow>(NUM_WINDOWS)

    /** @return a shared standard HammingWindow */
    fun getHammingWindow(sizeLog2: Int): HammingWindow {
        val index = sizeLog2 - MIN_SIZE_LOG_2
        return hammingWindows[index] ?: HammingWindow(1 shl sizeLog2).also { hammingWindows[index] = it }
    }

    /** @return a shared HannWindow */
    fun getHannWindow(sizeLog2: Int): HannWindow {
        val index = sizeLog2 - MIN_SIZE_LOG_2
        return hannWindows[index] ?: HannWindow(1 shl sizeLog2).also { hannWindows[index] = it }
    }
}

/** A Hamming window for use with FFT-based processing. */
class HammingWindow(size: Int) : SpectralWindow {
    private val table = FloatArray(size) { i ->
        (0.54 - 0.46 * cos(2.0 * PI * i / size)).toFloat()
    }
    override fun get(index: Int): Float = table[index]
    override fun size(): Int = table.size
}

/** A Hann window for use with FFT-based processing. */
class HannWindow(size: Int) : SpectralWindow {
    private val table = FloatArray(size) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / size))).toFloat()
    }
    override fun get(index: Int): Float = table[index]
    override fun size(): Int = table.size
}
