package com.softsynth.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

class TestFourierMath {

    @Test
    fun testNumBits() {
        assertEquals(0, numBits(1))
        assertEquals(1, numBits(2))
        assertEquals(2, numBits(4))
        assertEquals(3, numBits(8))
        assertEquals(9, numBits(512))
        assertEquals(10, numBits(1024))
    }

    @Test
    fun testCalculateMagnitudesDouble() {
        val ar = doubleArrayOf(3.0, 0.0)
        val ai = doubleArrayOf(4.0, 0.0)
        val mag = DoubleArray(2)
        calculateMagnitudes(ar, ai, mag)
        assertEquals(5.0, mag[0], 0.0001)
        assertEquals(0.0, mag[1], 0.0001)
    }

    @Test
    fun testCalculateMagnitudesFloat() {
        val ar = floatArrayOf(3.0f, 0.0f)
        val ai = floatArrayOf(4.0f, 0.0f)
        val mag = FloatArray(2)
        calculateMagnitudes(ar, ai, mag)
        assertEquals(5.0f, mag[0], 0.0001f)
        assertEquals(0.0f, mag[1], 0.0001f)
    }

    @Test
    fun testFFTDoubleRoundTrip() {
        val n = 32
        val inputR = DoubleArray(n) { Random.nextDouble() }
        val inputI = DoubleArray(n) { Random.nextDouble() }
        val ar = inputR.clone()
        val ai = inputI.clone()

        fft(n, ar, ai)
        ifft(n, ar, ai)

        for (i in 0 until n) {
            assertEquals(inputR[i], ar[i], 0.0001, "Real part at index $i mismatch")
            assertEquals(inputI[i], ai[i], 0.0001, "Imaginary part at index $i mismatch")
        }
    }

    @Test
    fun testFFTFloatRoundTrip() {
        val n = 32
        val inputR = FloatArray(n) { Random.nextFloat() }
        val inputI = FloatArray(n) { Random.nextFloat() }
        val ar = inputR.clone()
        val ai = inputI.clone()

        fft(n, ar, ai)
        ifft(n, ar, ai)

        for (i in 0 until n) {
            assertEquals(inputR[i], ar[i], 0.0001f, "Real part at index $i mismatch")
            assertEquals(inputI[i], ai[i], 0.0001f, "Imaginary part at index $i mismatch")
        }
    }

    @Test
    fun testFFTDoubleDC() {
        val n = 8
        val ar = DoubleArray(n) { 1.0 }
        val ai = DoubleArray(n) { 0.0 }

        fft(n, ar, ai)

        // Expected: Bin 0 should be 2.0 (based on analysis of 2/N scaling).
        assertEquals(2.0, ar[0], 0.0001, "DC component magnitude")
        assertEquals(0.0, ai[0], 0.0001, "DC component imaginary")

        for (i in 1 until n) {
            assertEquals(0.0, ar[i], 0.0001, "Real part at bin $i")
            assertEquals(0.0, ai[i], 0.0001, "Imag part at bin $i")
        }
    }

    @Test
    fun testFFTFloatSine() {
        val n = 16
        val ar = FloatArray(n)
        val ai = FloatArray(n)

        // Generate sine wave at bin k=2
        val k = 2
        for (i in 0 until n) {
            ar[i] = sin(i * k * 2.0 * PI / n).toFloat()
            ai[i] = 0.0f
        }

        fft(n, ar, ai)

        // Expected: Magnitude at bin 2 should be 1.0 (based on analysis).
        // Real part 0, Imag part 1.0 at k (because this FFT implementation uses exp(+i*theta)).
        assertEquals(0.0f, ar[k], 0.0001f, "Real part at bin $k")
        assertEquals(1.0f, ai[k], 0.0001f, "Imag part at bin $k")

        // Real part 0, Imag part -1.0 at N-k.
        assertEquals(0.0f, ar[n-k], 0.0001f, "Real part at bin ${n-k}")
        assertEquals(-1.0f, ai[n-k], 0.0001f, "Imag part at bin ${n-k}")

        // Check other bins near zero
        for (i in 0 until n) {
             if (i != k && i != (n-k)) {
                 assertEquals(0.0f, ar[i], 0.001f, "Real part at bin $i")
                 assertEquals(0.0f, ai[i], 0.001f, "Imag part at bin $i")
             }
        }
    }
}
