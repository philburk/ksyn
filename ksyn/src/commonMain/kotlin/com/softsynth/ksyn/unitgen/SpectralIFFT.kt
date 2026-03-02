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

package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.data.Spectrum
import com.softsynth.ksyn.data.SpectralWindow
import com.softsynth.ksyn.math.ifft
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.ports.UnitSpectralInputPort

/**
 * Periodically produce an output signal using an Inverse FFT on an incoming spectrum.
 *
 * @author Phil Burk (C) 2013 Mobileer Inc
 * @see SpectralFFT
 */
class SpectralIFFT : UnitGenerator() {
    val input: UnitSpectralInputPort
    val output: UnitOutputPort

    private var localSpectrum: Spectrum? = null
    private var buffer: FloatArray? = null
    private var cursor: Int = 0
    private var window: SpectralWindow = RectangularWindow.getInstance()

    init {
        output = UnitOutputPort()
        addPort(output)
        input = UnitSpectralInputPort("Input")
        addPort(input)
    }

    /**
     * Multiply output data by this window after doing the IFFT. The default is a RectangularWindow.
     */
    fun setWindow(window: SpectralWindow) {
        this.window = window
    }

    fun getWindow(): SpectralWindow = window

    override fun generate() {
        val outputs = output.getValues()

        if (buffer == null) {
            if (input.isAvailable()) {
                val spectrum = input.getSpectrum()
                val size = spectrum.size()
                localSpectrum = Spectrum(size)
                buffer = localSpectrum!!.real
                cursor = 0
            } else {
                outputs.fill(0.0f)
                return
            }
        }

        val buf = buffer ?: return
        val local = localSpectrum ?: return

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            if (cursor == 0) {
                val spectrum = input.getSpectrum()
                spectrum.copyTo(local)
                ifft(buf.size, local.real, local.imaginary)
            }
            outputs[i] = buf[cursor] * window.get(cursor)
            cursor++
            if (cursor == buf.size) {
                cursor = 0
            }
        }
    }
}
