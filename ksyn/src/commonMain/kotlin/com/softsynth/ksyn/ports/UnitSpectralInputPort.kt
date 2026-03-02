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
 * An input port that receives a complete Spectrum from a connected [UnitSpectralOutputPort].
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
class UnitSpectralInputPort(name: String = "Input") : UnitPort(name) {
    private var other: UnitSpectralOutputPort? = null
    private var localSpectrum: Spectrum? = null

    fun setSpectrum(spectrum: Spectrum) {
        this.localSpectrum = spectrum
    }

    fun getSpectrum(): Spectrum {
        return other?.getSpectrum() ?: localSpectrum ?: Spectrum()
    }

    fun connect(output: UnitSpectralOutputPort) {
        this.other = output
    }

    fun disconnect(output: UnitSpectralOutputPort) {
        if (this.other === output) {
            this.other = null
        }
    }

    fun isAvailable(): Boolean {
        val connected = other
        return if (connected != null) {
            connected.isAvailable()
        } else {
            localSpectrum != null
        }
    }

    fun pullData(frameCount: Long) {
        other?.unitGenerator?.pullData(frameCount)
    }
}
