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

import com.softsynth.ksyn.AudioBuffer
import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.data.Spectrum
import com.softsynth.math.transform
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort

/**
 * Periodically transform the complex input signal using an FFT to a complex spectral stream.
 *
 * @author Phil Burk (C) 2013 Mobileer Inc
 * @see IFFT
 */
abstract class FFTBase : UnitGenerator() {
    val inputReal: UnitInputPort
    val inputImaginary: UnitInputPort
    val outputReal: UnitOutputPort
    val outputImaginary: UnitOutputPort

    protected var realInput: AudioBuffer = AudioBuffer(Spectrum.DEFAULT_SIZE)
    protected var realOutput: AudioBuffer = AudioBuffer(Spectrum.DEFAULT_SIZE)
    protected var imaginaryInput: AudioBuffer = AudioBuffer(Spectrum.DEFAULT_SIZE)
    protected var imaginaryOutput: AudioBuffer = AudioBuffer(Spectrum.DEFAULT_SIZE)
    protected var cursor: Int = 0

    init {
        inputReal = UnitInputPort("InputReal")
        addPort(inputReal)
        inputImaginary = UnitInputPort("InputImaginary")
        addPort(inputImaginary)
        outputReal = UnitOutputPort("OutputReal")
        addPort(outputReal)
        outputImaginary = UnitOutputPort("OutputImaginary")
        addPort(outputImaginary)
        setSize(Spectrum.DEFAULT_SIZE)
    }

    fun setSize(size: Int) {
        realInput = AudioBuffer(size)
        realOutput = AudioBuffer(size)
        imaginaryInput = AudioBuffer(size)
        imaginaryOutput = AudioBuffer(size)
        cursor = 0
    }

    fun getSize(): Int = realInput.size

    override fun generate() {
        val inputRs = inputReal.getValues()
        val inputIs = inputImaginary.getValues()
        val outputRs = outputReal.getValues()
        val outputIs = outputImaginary.getValues()

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            realInput[cursor] = inputRs[i]
            imaginaryInput[cursor] = inputIs[i]
            outputRs[i] = realOutput[cursor]
            outputIs[i] = imaginaryOutput[cursor]
            cursor++
            // When full, do the FFT in place.
            if (cursor == realInput.size) {
                realInput.copyInto(realOutput)
                imaginaryInput.copyInto(imaginaryOutput)
                transform(getSign(), realOutput.size, realOutput, imaginaryOutput)
                cursor = 0
            }
        }
    }

    protected abstract fun getSign(): Int
}
