/*
 * Copyright 2012 Phil Burk, Mobileer Inc
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
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.util.AutoCorrelator
import com.softsynth.ksyn.util.SignalCorrelator
import kotlin.math.max

/**
 * Estimate the fundamental frequency of a monophonic signal. Analyzes an input signal and outputs
 * an estimated period in frames and a frequency in Hertz. The frequency is frameRate/period. The
 * confidence tells you how accurate the estimate is. When the confidence is low, you should ignore
 * the period. You can use a CompareUnit and a LatchUnit to hold values that you are confident of.
 * <P>
 * Note that a stable monophonic signal is required for accurate pitch tracking.
 *
 * @author (C) 2012 Phil Burk, Mobileer Inc
 */
class PitchDetector : UnitGenerator() {

    val input: UnitInputPort
    val period: UnitOutputPort
    val confidence: UnitOutputPort
    val frequency: UnitOutputPort
    val updated: UnitOutputPort

    protected var signalCorrelator: SignalCorrelator

    private var lastFrequency: Double = 440.0
    // result of analysis TODO update for 48000
    private var lastPeriod: Double = 44100.0 / lastFrequency 
    // Measure of confidence in the result.
    private var lastConfidence: Double = 0.0 

    companion object {
        private const val LOWEST_FREQUENCY = 40
        private const val HIGHEST_RATE = 48000
        private const val CYCLES_NEEDED = 2
    }

    init {
        input = UnitInputPort("Input")
        addPort(input)

        period = UnitOutputPort("Period")
        addPort(period)
        
        confidence = UnitOutputPort("Confidence")
        addPort(confidence)
        
        frequency = UnitOutputPort("Frequency")
        addPort(frequency)
        
        updated = UnitOutputPort("Updated")
        addPort(updated)
        
        signalCorrelator = createSignalCorrelator()
    }

    private fun createSignalCorrelator(): SignalCorrelator {
        val framesNeeded = HIGHEST_RATE * CYCLES_NEEDED / LOWEST_FREQUENCY
        return AutoCorrelator(framesNeeded)
    }

    override fun generate() {
        val inputs = input.getValues()
        val periods = period.getValues()
        val confidences = confidence.getValues()
        val frequencies = frequency.getValues()
        val updateds = updated.getValues()

        val frameRateLocal = synthesisEngine?.frameRate?.toFloat() ?: 44100.0f
        
        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            val current = inputs[i].toDouble()
            if (signalCorrelator.addSample(current)) {
                lastPeriod = max(0.1, signalCorrelator.period)
                val currentFrequency = frameRateLocal / lastPeriod
                val conf = signalCorrelator.confidence
                
                if (conf > 0.1) {
                    // Take weighted average with previous frequency.
                    val coefficient = conf * 0.2
                    lastFrequency = ((lastFrequency * (1.0 - coefficient)) + (currentFrequency * coefficient))
                }
                lastConfidence = conf
                updateds[i] = 1.0f
            } else {
                updateds[i] = 0.0f
            }
            periods[i] = lastPeriod.toFloat()
            confidences[i] = lastConfidence.toFloat()
            frequencies[i] = lastFrequency.toFloat()
        }
    }

    /**
     * For debugging only.
     *
     * @return internal array of correlation results.
     */
    fun getDiffs(): FloatArray {
        return signalCorrelator.diffs
    }
}
