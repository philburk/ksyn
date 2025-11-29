package com.softsynth.ksyn.math

import kotlin.math.ln
import kotlin.math.pow

// use scalar to convert natural log to log_base_10
private val a2dScalar = 20.0 / ln(10.0)
const val CONCERT_A_PITCH = 69
const val CONCERT_A_FREQUENCY = 440.0
private var mConcertAFrequency = CONCERT_A_FREQUENCY

/**
 * Convert amplitude to decibels. 1.0 is zero dB. 0.5 is -6.02 dB.
 */
fun amplitudeToDecibels(amplitude: Double): Double {
    return ln(amplitude) * a2dScalar
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
    return CONCERT_A_PITCH + 12 * ln(frequency / mConcertAFrequency) / ln(2.0)
}

/**
 * Calculate frequency in Hertz based on MIDI pitch. Middle C is 60.0. You can use fractional
 * pitches so 60.5 would give you a pitch half way between C and C#.
 */
fun pitchToFrequency(pitch: Double): Double {
    return mConcertAFrequency * 2.0.pow((pitch - CONCERT_A_PITCH) * (1.0 / 12.0))
}

/**
 * This can be used to globally adjust the tuning in JSyn from Concert A at 440.0 Hz to
 * a slightly different frequency. Some orchestras use a higher frequency, eg. 441.0.
 * This value will be used by pitchToFrequency() and frequencyToPitch().
 *
 * @param concertAFrequency
 */
fun setConcertAFrequency(concertAFrequency: Double) {
    mConcertAFrequency = concertAFrequency
}

fun getConcertAFrequency(): Double {
    return mConcertAFrequency
}

/** Convert a delta value in semitones to a frequency multiplier.
 * @param semitones
 * @return scaler For example 2.0 for an input of 12.0 semitones.
 */
fun semitonesToFrequencyScaler(semitones: Double): Double {
    return 2.0.pow(semitones / 12.0)
}
