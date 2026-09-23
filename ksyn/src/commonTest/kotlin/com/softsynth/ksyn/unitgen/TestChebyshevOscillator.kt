/*
 * Copyright 2026 Phil Burk, Mobileer Inc
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
import com.softsynth.math.T
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestChebyshevOscillator {

    /**
     * Runs the oscillator alongside a plain sine at the same frequency and drive, and checks
     * that every output sample is amplitude * T(order)(sine).
     */
    private fun checkMatchesPolynomial(order: Int, drive: Double, amplitude: Double) {
        val cheby = ChebyshevOscillator()
        cheby.order = order
        cheby.frequency.set(441.0)
        cheby.drive.set(drive)
        cheby.amplitude.set(amplitude)

        val reference = SineOscillator(441.0, drive)
        val polynomial = T(order)

        // Linear interpolation error grows with the curvature of T(n), which peaks near
        // the ends of the table at n^2 (n^2 - 1) / 3.
        val tolerance = 1.0e-3
        repeat(200) {
            cheby.generate()
            reference.generate()
            for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
                val x = reference.output.getValues()[i].toDouble()
                val expected = amplitude * polynomial.evaluate(x)
                val actual = cheby.output.getValues()[i].toDouble()
                assertTrue(
                    abs(expected - actual) < tolerance,
                    "order $order, drive $drive: expected $expected, got $actual"
                )
            }
        }
    }

    @Test
    fun testOutputIsTheChebyshevPolynomialOfASine() {
        for (order in ChebyshevOscillator.ORDERS) {
            checkMatchesPolynomial(order, drive = 1.0, amplitude = 0.5)
            checkMatchesPolynomial(order, drive = 0.5, amplitude = 1.0)
        }
    }

    @Test
    fun testOrderOneIsAPlainSine() {
        checkMatchesPolynomial(1, drive = 0.8, amplitude = 0.5)
    }

    @Test
    fun testEvenOrdersAreRejected() {
        val cheby = ChebyshevOscillator()
        for (order in listOf(0, 2, 4, 12, 17, -1)) {
            assertFailsWith<IllegalArgumentException> { cheby.order = order }
        }
        assertEquals(ChebyshevOscillator.DEFAULT_ORDER, cheby.order)
    }

    @Test
    fun testOddOrdersHaveNoDcOffset() {
        // Odd polynomials are odd functions, so a full cycle averages to zero at any drive.
        val cheby = ChebyshevOscillator()
        cheby.order = 11
        cheby.drive.set(0.7)
        cheby.amplitude.set(1.0)
        // 441 Hz at the default 44100 frame rate is exactly 100 frames per cycle.
        cheby.frequency.set(441.0)
        val framesPerCycle = 100
        val blocks = framesPerCycle * Synthesizer.FRAMES_PER_BLOCK // a whole number of cycles
        var sum = 0.0
        repeat(blocks) {
            cheby.generate()
            for (value in cheby.output.getValues().take(Synthesizer.FRAMES_PER_BLOCK)) {
                sum += value.toDouble()
            }
        }
        val mean = sum / (blocks * Synthesizer.FRAMES_PER_BLOCK)
        assertTrue(abs(mean) < 1.0e-4, "mean should be zero, got $mean")
    }

    @Test
    fun testTablesAreSharedBetweenOscillators() {
        for (order in ChebyshevOscillator.ORDERS) {
            assertSame(
                ChebyshevOscillator.tableForOrder(order),
                ChebyshevOscillator.tableForOrder(order)
            )
        }
    }

    @Test
    fun testPortsAreExposedByName() {
        val cheby = ChebyshevOscillator()
        assertSame(cheby.frequency, cheby.getPortByName("frequency"))
        assertSame(cheby.drive, cheby.getPortByName("drive"))
        assertSame(cheby.amplitude, cheby.getPortByName("amplitude"))
        assertSame(cheby.output, cheby.getPortByName("output"))
    }
}
