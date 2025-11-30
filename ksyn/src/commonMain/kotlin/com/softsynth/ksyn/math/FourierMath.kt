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

package com.softsynth.ksyn.math

import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.sqrt

//Simple Fast Fourier Transform.

private const val MAX_SIZE_LOG_2 = 16
private val reverseTables = arrayOfNulls<BitReverseTable>(MAX_SIZE_LOG_2)
private val sineTables = arrayOfNulls<DoubleSineTable>(MAX_SIZE_LOG_2)
private val floatSineTables = arrayOfNulls<FloatSineTable>(MAX_SIZE_LOG_2)

private class DoubleSineTable(numBits: Int) {
    val sineValues: DoubleArray
    init {
        val len = 1 shl numBits
        sineValues = DoubleArray(len)
        for (i in 0 until len) {
            sineValues[i] = sin((i * PI * 2.0) / len)
        }
    }
}

private fun getDoubleSineTable(n: Int): DoubleArray {
    var sineTable = sineTables[n]
    if (sineTable == null) {
        sineTable = DoubleSineTable(n)
        sineTables[n] = sineTable
    }
    return sineTable.sineValues
}

private class FloatSineTable(numBits: Int) {
    val sineValues: FloatArray
    init {
        val len = 1 shl numBits
        sineValues = FloatArray(len)
        for (i in 0 until len) {
            sineValues[i] = sin((i * PI * 2.0) / len).toFloat()
        }
    }
}

private fun getFloatSineTable(n: Int): FloatArray {
    var sineTable = floatSineTables[n]
    if (sineTable == null) {
        sineTable = FloatSineTable(n)
        floatSineTables[n] = sineTable
    }
    return sineTable.sineValues
}

private class BitReverseTable(numBits: Int) {
    val reversedBits: IntArray
    init {
        reversedBits = IntArray(1 shl numBits)
        for (i in reversedBits.indices) {
            reversedBits[i] = reverseBits(i, numBits)
        }
    }

    companion object {
        fun reverseBits(index: Int, numBits: Int): Int {
            var i = 0
            var rev = 0
            var idx = index

            while (i < numBits) {
                rev = (rev shl 1) or (idx and 1)
                idx = idx shr 1
                i++
            }

            return rev
        }
    }
}

private fun getReverseTable(n: Int): IntArray {
    var reverseTable = reverseTables[n]
    if (reverseTable == null) {
        reverseTable = BitReverseTable(n)
        reverseTables[n] = reverseTable
    }
    return reverseTable.reversedBits
}

/**
 * Calculate the amplitude of the sine wave associated with each bin of a complex FFT result.
 *
 * @param ar
 * @param ai
 * @param magnitudes
 */
fun calculateMagnitudes(ar: DoubleArray, ai: DoubleArray, magnitudes: DoubleArray) {
    for (i in magnitudes.indices) {
        magnitudes[i] = sqrt((ar[i] * ar[i]) + (ai[i] * ai[i]))
    }
}

/**
 * Calculate the amplitude of the sine wave associated with each bin of a complex FFT result.
 *
 * @param ar
 * @param ai
 * @param magnitudes
 */
fun calculateMagnitudes(ar: FloatArray, ai: FloatArray, magnitudes: FloatArray) {
    for (i in magnitudes.indices) {
        magnitudes[i] = sqrt((ar[i] * ar[i]) + (ai[i] * ai[i]))
    }
}

fun transform(sign: Int, n: Int, ar: DoubleArray, ai: DoubleArray) {
    val scale = if (sign > 0) (2.0 / n) else 0.5

    val numBits = numBits(n)
    val reverseTable = getReverseTable(numBits)
    val sineTable = getDoubleSineTable(numBits)
    val mask = n - 1
    val cosineOffset = n / 4 // phase offset between cos and sin

    var i: Int
    var j: Int
    for (i in 0 until n) {
        j = reverseTable[i]
        if (j >= i) {
            val tempr = ar[j] * scale
            val tempi = ai[j] * scale
            ar[j] = ar[i] * scale
            ai[j] = ai[i] * scale
            ar[i] = tempr
            ai[i] = tempi
        }
    }

    var mmax = 1
    var stride: Int
    val numerator = sign * n
    while (mmax < n) {
        stride = 2 * mmax
        var phase = 0
        val phaseIncrement = numerator / (2 * mmax)
        for (m in 0 until mmax) {
            val wr = sineTable[(phase + cosineOffset) and mask] // cosine
            val wi = sineTable[phase]

            for (i in m until n step stride) {
                j = i + mmax
                val tr = (wr * ar[j]) - (wi * ai[j])
                val ti = (wr * ai[j]) + (wi * ar[j])
                ar[j] = ar[i] - tr
                ai[j] = ai[i] - ti
                ar[i] += tr;
                ai[i] += ti;
            }

            phase = (phase + phaseIncrement) and mask
        }
        mmax = stride
    }
}

fun transform(sign: Int, n: Int, ar: FloatArray, ai: FloatArray) {
    val scale = if (sign > 0) (2.0f / n) else 0.5f

    val numBits = numBits(n)
    val reverseTable = getReverseTable(numBits)
    val sineTable = getFloatSineTable(numBits)
    val mask = n - 1
    val cosineOffset = n / 4 // phase offset between cos and sin

    var i: Int
    var j: Int
    for (i in 0 until n) {
        j = reverseTable[i]
        if (j >= i) {
            val tempr = ar[j] * scale
            val tempi = ai[j] * scale
            ar[j] = ar[i] * scale
            ai[j] = ai[i] * scale
            ar[i] = tempr
            ai[i] = tempi
        }
    }

    var mmax = 1
    var stride: Int
    val numerator = sign * n
    while (mmax < n) {
        stride = 2 * mmax
        var phase = 0
        val phaseIncrement = numerator / (2 * mmax)
        for (m in 0 until mmax) {
            val wr = sineTable[(phase + cosineOffset) and mask] // cosine
            val wi = sineTable[phase]

            for (i in m until n step stride) {
                j = i + mmax
                val tr = (wr * ar[j]) - (wi * ai[j])
                val ti = (wr * ai[j]) + (wi * ar[j])
                ar[j] = ar[i] - tr
                ai[j] = ai[i] - ti
                ar[i] += tr
                ai[i] += ti
            }

            phase = (phase + phaseIncrement) and mask
        }
        mmax = stride
    }
}

/**
 * Calculate log2(n)
 *
 * @param powerOf2 must be a power of two, for example 512 or 1024
 * @return for example, 9 for an input value of 512
 */
fun numBits(powerOf2: Int): Int {
    var i = 0
    // assert ((powerOf2 and (powerOf2 - 1)) == 0); // is it a power of 2?
    var p2 = powerOf2
    i = -1
    while (p2 > 0) {
        p2 = p2 shr 1
        i++
    }
    return i
}

/**
 * Calculate an FFT in place, modifying the input arrays.
 *
 * @param n
 * @param ar
 * @param ai
 */
fun fft(n: Int, ar: DoubleArray, ai: DoubleArray) {
    transform(1, n, ar, ai) // TODO -1 or 1
}

/**
 * Calculate an inverse FFT in place, modifying the input arrays.
 *
 * @param n
 * @param ar
 * @param ai
 */
fun ifft(n: Int, ar: DoubleArray, ai: DoubleArray) {
    transform(-1, n, ar, ai) // TODO -1 or 1
}
