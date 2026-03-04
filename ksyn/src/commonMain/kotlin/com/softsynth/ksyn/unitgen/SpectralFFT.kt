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
import com.softsynth.math.fft
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitSpectralOutputPort

/**
 * Periodically transform the input signal using a windowed FFT and output the complete spectrum.
 *
 * @author Phil Burk (C) 2013 Mobileer Inc
 * @see SpectralIFFT
 * @see Spectrum
 * @see SpectralFilter
 */
class SpectralFFT(sizeLog2: Int = Spectrum.DEFAULT_SIZE_LOG_2) : UnitGenerator() {
    val input: UnitInputPort
    val output: UnitSpectralOutputPort

    private var buffer: FloatArray
    private var cursor: Int = 0
    private var window: SpectralWindow = RectangularWindow.getInstance()
    private var currentSizeLog2: Int = sizeLog2
    private var offset: Int = 0
    private var running: Boolean = false

    init {
        input = UnitInputPort("Input")
        addPort(input)
        output = UnitSpectralOutputPort("Output", 1 shl sizeLog2)
        addPort(output)
        setSizeLog2(sizeLog2)
        buffer = output.getSpectrum().real
    }

    /**
     * Please do not change the size of the FFT while the synthesizer is running.
     * @param sizeLog2 for example, pass 9 to get a 512 bin FFT
     */
    fun setSizeLog2(sizeLog2: Int) {
        currentSizeLog2 = sizeLog2
        output.setSize(1 shl sizeLog2)
        buffer = output.getSpectrum().real
        cursor = 0
    }

    fun getSizeLog2(): Int = currentSizeLog2

    /**
     * Multiply input data by this window before doing the FFT. The default is a RectangularWindow.
     */
    fun setWindow(window: SpectralWindow) {
        this.window = window
    }

    fun getWindow(): SpectralWindow = window

    /**
     * The FFT will be performed on a frame that is a multiple of the size plus this offset.
     */
    fun setOffset(offset: Int) {
        this.offset = offset
    }

    fun getOffset(): Int = offset

    override fun generate() {
        if (!running) {
            val mask = (1 shl currentSizeLog2) - 1
            if (((synthesisEngine!!.frameCount - offset) and mask.toLong()) == 0L) {
                running = true
                cursor = 0
            }
        }
        // Don't use "else" because "running" may have changed in the block above.
        if (running) {
            val inputs = input.getValues()
            for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
                buffer[cursor] = inputs[i] * window.get(cursor)
                cursor++
                // When full, do the FFT.
                if (cursor == buffer.size) {
                    val spectrum = output.getSpectrum()
                    spectrum.imaginary.fill(0.0f)
                    fft(buffer.size, spectrum.real, spectrum.imaginary)
                    output.advance()
                    cursor = 0
                }
            }
        }
    }
}
