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

/**
 * Complex spectrum with real and imaginary parts. The frequency associated with each bin of the
 * spectrum is:
 *
 * ```
 * frequency = binIndex * sampleRate / size
 * ```
 *
 * Note that the upper half of the spectrum is above the Nyquist frequency. Those frequencies are
 * mirrored around the Nyquist frequency.
 *
 * @author Phil Burk (C) 2013 Mobileer Inc
 */
class Spectrum(size: Int = DEFAULT_SIZE) {
    var real: FloatArray = FloatArray(size)
        private set
    var imaginary: FloatArray = FloatArray(size)
        private set

    companion object {
        const val DEFAULT_SIZE_LOG_2 = 9
        val DEFAULT_SIZE = 1 shl DEFAULT_SIZE_LOG_2
    }

    /**
     * If you change the size of the spectrum then the real and imaginary arrays will be
     * reallocated.
     */
    fun setSize(size: Int) {
        if (real.size != size) {
            real = FloatArray(size)
            imaginary = FloatArray(size)
        }
    }

    fun size(): Int = real.size

    /**
     * Copy this spectrum to another spectrum of the same length.
     */
    fun copyTo(destination: Spectrum) {
        require(size() == destination.size())
        real.copyInto(destination.real)
        imaginary.copyInto(destination.imaginary)
    }

    fun clear() {
        real.fill(0.0f)
        imaginary.fill(0.0f)
    }
}
