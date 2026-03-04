/*
 * Copyright 1998 Phil Burk, Mobileer Inc
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

package com.softsynth.math

import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow

/**
 * Miscellaneous math functions useful in Audio
 * Converted to Kotlin Multiplatform for KSyn.
 */
object AudioMath {
    const val CONCERT_A_PITCH: Int = 69
    const val CONCERT_A_FREQUENCY: Double = 440.0
    
    var concertAFrequency: Double = CONCERT_A_FREQUENCY

    /**
     * Convert amplitude to decibels. 1.0 is zero dB. 0.5 is -6.02 dB.
     */
    fun amplitudeToDecibels(amplitude: Double): Double {
        return 20.0 * log10(amplitude)
    }

    /**
     * Convert decibels to amplitude. Zero dB is 1.0 and -6.02 dB is 0.5.
     */
    fun decibelsToAmplitude(decibels: Double): Double {
        return 10.0.pow(decibels / 20.0)
    }

    /**
     * Calculate MIDI pitch based on frequency in Hertz. Middle C is 60.0.
     */
    fun frequencyToPitch(frequency: Double): Double {
        return CONCERT_A_PITCH + 12.0 * log2(frequency / concertAFrequency)
    }

    /**
     * Calculate frequency in Hertz based on MIDI pitch. Middle C is 60.0. You can use fractional
     * pitches so 60.5 would give you a pitch half way between C and C#.
     */
    fun pitchToFrequency(pitch: Double): Double {
        return concertAFrequency * 2.0.pow((pitch - CONCERT_A_PITCH) * (1.0 / 12.0))
    }

    /** Convert a delta value in semitones to a frequency multiplier.
     * @param semitones
     * @return scaler For example 2.0 for an input of 12.0 semitones.
     */
    fun semitonesToFrequencyScaler(semitones: Double): Double {
        return 2.0.pow(semitones / 12.0)
    }
}
