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

import com.softsynth.ksyn.data.Spectrum

/**
 * An output port that carries a complete Spectrum (complex FFT data) to a connected
 * [UnitSpectralInputPort].
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
class UnitSpectralOutputPort(name: String = "Output", size: Int = Spectrum.DEFAULT_SIZE) : UnitPort(name) {
    private val spectrum = Spectrum(size)
    private var available = false

    fun setSize(size: Int) {
        spectrum.setSize(size)
    }

    fun getSpectrum(): Spectrum = spectrum

    /** Call this after filling the spectrum to signal connected inputs that new data is ready. */
    fun advance() {
        available = true
    }

    fun isAvailable(): Boolean = available

    fun connect(other: UnitSpectralInputPort) {
        other.connect(this)
    }
}
