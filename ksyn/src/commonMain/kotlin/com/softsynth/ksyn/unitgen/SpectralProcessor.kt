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

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.data.Spectrum
import com.softsynth.ksyn.ports.UnitSpectralInputPort
import com.softsynth.ksyn.ports.UnitSpectralOutputPort

/**
 * Base class for implementing your own spectral processing units. Override [processSpectrum] to
 * apply your operation to the complex input spectrum and write results to the output spectrum.
 *
 * @author Phil Burk (C) 2014 Mobileer Inc
 * @see Spectrum
 */
abstract class SpectralProcessor(size: Int = Spectrum.DEFAULT_SIZE) : UnitGenerator() {
    val input: UnitSpectralInputPort
    val output: UnitSpectralOutputPort

    private var counter: Int = 0

    init {
        output = UnitSpectralOutputPort(size = size)
        addPort(output)
        input = UnitSpectralInputPort()
        addPort(input)
    }

    override fun generate() {
        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            if (counter == 0) {
                if (input.isAvailable()) {
                    val inputSpectrum = input.getSpectrum()
                    val outputSpectrum = output.getSpectrum()
                    processSpectrum(inputSpectrum, outputSpectrum)
                    output.advance()
                    counter = inputSpectrum.size() - 1
                }
            } else {
                counter--
            }
        }
    }

    /**
     * Override this to implement your own spectral processor.
     */
    abstract fun processSpectrum(inputSpectrum: Spectrum, outputSpectrum: Spectrum)
}

