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

import com.softsynth.ksyn.data.Spectrum
import com.softsynth.ksyn.data.SpectralWindow
import com.softsynth.ksyn.data.SpectralWindowFactory
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.ports.UnitSpectralInputPort
import com.softsynth.ksyn.ports.UnitSpectralOutputPort

/**
 * Process a signal using multiple overlapping FFT and IFFT pairs. Connect the [UnitSpectralOutputPort]
 * from [getSpectralOutput] to a [SpectralProcessor]'s input, and the processor's output back to
 * [getSpectralInput] to insert processing in the spectral domain.
 *
 * ```kotlin
 * for (i in 0 until numFFTs) {
 *     filter.getSpectralOutput(i).connect(processors[i].input)
 *     processors[i].output.connect(filter.getSpectralInput(i))
 * }
 * ```
 *
 * @author Phil Burk (C) 2014 Mobileer Inc
 * @see SpectralProcessor
 */
class SpectralFilter(
    numFFTs: Int = 2,
    sizeLog2: Int = Spectrum.DEFAULT_SIZE_LOG_2
) : Circuit(), UnitSink, UnitSource {
    override var input: UnitInputPort
    val output: UnitOutputPort

    private val ffts: Array<SpectralFFT>
    private val iffts: Array<SpectralIFFT>
    private val inlet: PassThrough
    private val sum: PassThrough

    init {
        inlet = PassThrough()
        add(inlet)
        sum = PassThrough()
        add(sum)

        ffts = Array(numFFTs) { SpectralFFT(sizeLog2) }
        iffts = Array(numFFTs) { SpectralIFFT() }

        val offset = (1 shl sizeLog2) / numFFTs
        for (i in 0 until numFFTs) {
            add(ffts[i])
            inlet.output.connect(ffts[i].input)
            ffts[i].setOffset(i * offset)

            add(iffts[i])
            iffts[i].output.connect(sum.input)
        }

        setWindow(SpectralWindowFactory.getHammingWindow(sizeLog2))

        input = inlet.input
        addPort(input)
        output = sum.output
        addPort(output)
    }

    fun getWindow(): SpectralWindow = ffts[0].getWindow()

    /**
     * Specify one window to be used for all FFTs and IFFTs. Default is HammingWindow.
     */
    fun setWindow(window: SpectralWindow) {
        for (i in ffts.indices) {
            ffts[i].setWindow(window)
            iffts[i].setWindow(window)
        }
    }

    /** @return the spectral output of the i-th FFT for connection to a SpectralProcessor. */
    fun getSpectralOutput(i: Int): UnitSpectralOutputPort = ffts[i].output

    /** @return the spectral input of the i-th IFFT for connection from a SpectralProcessor. */
    fun getSpectralInput(i: Int): UnitSpectralInputPort = iffts[i].input

    override fun getOutputPort(): UnitOutputPort = output
}

