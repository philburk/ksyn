/*
 * Copyright 2024 Phil Burk, Mobileer Inc
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
import com.softsynth.math.FourierMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestSpectral : NonRealTimeTestCase() {

    companion object {
        // FFT size log2 = 9 → size = 512
        private const val SIZE_LOG2 = Spectrum.DEFAULT_SIZE_LOG_2
        private val FFT_SIZE = Spectrum.DEFAULT_SIZE
        private const val TARGET_BIN = 7
    }

    // Frequency of bin N = N * sampleRate / fftSize
    private fun binFrequency(bin: Int): Double =
        bin.toDouble() * synthesisEngine.frameRate / FFT_SIZE

    /**
     * Test SpectralFFT: drive it with a SineOscillator at the exact bin-7 frequency and confirm
     * that bin 7 contains more energy than any other bin in the output spectrum.
     */
    @Test
    fun testSpectralFFTEnergyInBin7() {
        val targetFrequency = binFrequency(TARGET_BIN)

        val sine = SineOscillator(targetFrequency, 1.0)
        val fft = SpectralFFT(SIZE_LOG2)

        synthesisEngine.add(sine)
        synthesisEngine.add(fft)
        sine.output.connect(fft.input)

        synthesisEngine.start()
        fft.start()  // pull-mode: we start the leaves

        // Wait for at least 3 complete FFT windows to ensure a stable spectrum.
        val fftDuration = FFT_SIZE.toDouble() / synthesisEngine.frameRate
        checkSleepUntil(fftDuration * 3.0 + 0.010)

        // Verify the FFT has produced output.
        assertTrue(fft.output.isAvailable(), "SpectralFFT should have produced at least one spectrum")

        synthesisEngine.stop()

        // Compute the energy (magnitude squared) in each bin.
        val spectrum = fft.output.getSpectrum()
        val real = spectrum.real
        val imag = spectrum.imaginary

        // Find the peak energy bin in the lower half of the spectrum.
        // (upper half is the mirror image for real signals; skip DC bin 0)
        var maxEnergy = 0.0f
        var maxBin = -1
        for (i in 1 until (FFT_SIZE / 2)) {
            val energy = real[i] * real[i] + imag[i] * imag[i]
            if (energy > maxEnergy) {
                maxEnergy = energy
                maxBin = i
            }
        }

        val targetEnergy = real[TARGET_BIN] * real[TARGET_BIN] + imag[TARGET_BIN] * imag[TARGET_BIN]

        println("SpectralFFT target bin=$TARGET_BIN, freq=${String.format("%.2f", targetFrequency)} Hz")
        println("  peak bin=$maxBin, energy=$maxEnergy")
        println("  bin[$TARGET_BIN] energy=$targetEnergy")

        // Bin 7 must be the peak energy bin in the lower half of the spectrum.
        // A rectangular window spreads some energy via leakage, but since the sine is exactly
        // at the bin-7 centre frequency, bin 7 will still be the peak.
        assertEquals(TARGET_BIN, maxBin, "Bin $TARGET_BIN should have the highest energy in the spectrum")
    }

    /**
     * Test SpectralIFFT: inject energy into bin 7 of a Spectrum, run it through SpectralIFFT,
     * and verify the output frequency using PitchDetector over at least 0.5 seconds.
     */
    @Test
    fun testSpectralIFFTGeneratesSineAtBin7() {
        val targetFrequency = binFrequency(TARGET_BIN)

        // Build a spectrum with unit amplitude only in bin 7 (and its mirror for real signal).
        val injectSpectrum = Spectrum(FFT_SIZE)
        // A real-valued sine in the time domain requires conjugate-symmetric spectrum.
        // For a pure cosine at bin k: real[k] = 1, real[N-k] = 1, imaginary = 0,
        // but the IFFT scale factor is 0.5, so set amplitude large enough to hear.
        injectSpectrum.real[TARGET_BIN] = (FFT_SIZE / 2).toFloat()  // scale for IFFT normalisation
        injectSpectrum.real[FFT_SIZE - TARGET_BIN] = (FFT_SIZE / 2).toFloat()

        val ifft = SpectralIFFT()
        val pitchDetector = PitchDetector()

        // Inject our pre-built spectrum so the IFFT has data immediately.
        ifft.input.setSpectrum(injectSpectrum)

        synthesisEngine.add(ifft)
        synthesisEngine.add(pitchDetector)
        ifft.output.connect(pitchDetector.input)

        synthesisEngine.start()
        pitchDetector.start()  // start head of tree

        // Poll the pitch detector over at least 0.5 seconds.
        var confidence = 0.0f
        var detectedPeriod = 0.0
        val startTime = synthesisEngine.currentTime
        while (synthesisEngine.currentTime - startTime < 0.55) {
            checkSleepUntil(synthesisEngine.currentTime + 0.05)
            confidence = pitchDetector.confidence.value
            detectedPeriod = pitchDetector.period.value.toDouble()
            println("  SpectralIFFT pitch: period=$detectedPeriod, conf=$confidence, freq=${synthesisEngine.frameRate / detectedPeriod} Hz")
            if (confidence > 0.5) break
        }

        synthesisEngine.stop()

        assertTrue(confidence > 0.5, "PitchDetector confidence should be >0.5, got $confidence")

        // Expected period in frames: sampleRate / targetFrequency = fftSize / targetBin
        val expectedPeriod = synthesisEngine.frameRate.toDouble() / targetFrequency
        println("SpectralIFFT: expected period=$expectedPeriod (freq=$targetFrequency Hz), detected=$detectedPeriod")
        assertEquals(
            expectedPeriod, detectedPeriod, expectedPeriod * 0.05,
            "Detected period should be within 5% of expected (bin $TARGET_BIN = $targetFrequency Hz)"
        )
    }
}
