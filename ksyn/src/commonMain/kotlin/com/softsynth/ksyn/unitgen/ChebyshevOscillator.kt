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

import com.softsynth.ksyn.data.DoubleTable
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.math.PolynomialTableData
import com.softsynth.math.T

/**
 * Waveshaping oscillator: a sine wave passed through a Chebyshev polynomial.
 *
 * Feeding cos(θ) into the Chebyshev polynomial T(n) produces exactly cos(nθ), the n-th
 * harmonic. Lowering [drive] sweeps less of the polynomial and blends in the lower
 * harmonics, so it behaves like the drive control on a distortion unit: more drive,
 * brighter tone.
 *
 * Only odd orders are offered. An even order is built from even powers of its input,
 * which leaves a DC offset whenever [drive] is below 1.0.
 *
 * The polynomial tables are built once and shared by every oscillator of the same order.
 *
 * This is the oscillator from [com.softsynth.ksyn.instruments.WaveShapingVoice] without
 * its envelopes.
 *
 * @author Phil Burk (C) 2026 Mobileer Inc
 */
class ChebyshevOscillator : Circuit(), UnitSource {
    private val osc = SineOscillator()
    private val waveShaper = FunctionEvaluator()

    /** Frequency in Hertz. */
    val frequency: UnitInputPort = osc.frequency

    /** How much of the polynomial the sine sweeps, 0.1 to 1.0. Higher is brighter. */
    val drive: UnitInputPort = osc.amplitude

    val amplitude: UnitInputPort = waveShaper.amplitude
    val output: UnitOutputPort = waveShaper.output

    /**
     * Order of the Chebyshev polynomial. Must be one of [ORDERS].
     *
     * The new table takes effect at the start of the next block when the engine is running,
     * so it never changes underneath [generate].
     */
    var order: Int = DEFAULT_ORDER
        set(value) {
            require(isValidOrder(value)) { "Chebyshev order must be one of $ORDERS, was $value" }
            field = value
            val table = tableForOrder(value)
            val engine = synthesisEngine
            if (engine != null && engine.isRunning) {
                engine.queueCommand { waveShaper.function.set(table) }
            } else {
                waveShaper.function.set(table)
            }
        }

    init {
        add(osc)
        add(waveShaper)

        addPort(frequency, PORT_NAME_FREQUENCY)
        addPort(drive, PORT_NAME_DRIVE)
        addPort(amplitude, PORT_NAME_AMPLITUDE)
        addPort(output, PORT_NAME_OUTPUT)

        osc.output.connect(waveShaper.input)

        drive.setup(0.1, 0.8, 1.0)
        amplitude.setup(0.0, 0.5, 1.0)

        waveShaper.function.set(tableForOrder(DEFAULT_ORDER))
    }

    override fun getOutputPort(): UnitOutputPort = output

    companion object {
        const val PORT_NAME_DRIVE = "Drive"

        /** The odd orders that can be selected. */
        val ORDERS: List<Int> = listOf(1, 3, 5, 7, 9, 11, 13, 15)

        /** Matches the voice used in the "Chebyshev Song" demo. */
        const val DEFAULT_ORDER = 11

        /**
         * Points per table. T(15) crosses zero 15 times across the table, and linear
         * interpolation error shrinks with the square of the spacing, so a generous size
         * keeps the high orders clean. The tables are shared, so the memory is small.
         */
        private const val TABLE_SIZE = 4096

        fun isValidOrder(order: Int): Boolean = order in ORDERS

        /** Built together on first use; lazy initialization makes this thread safe. */
        private val tables: List<DoubleTable> by lazy {
            ORDERS.map { DoubleTable(PolynomialTableData(T(it), TABLE_SIZE).data) }
        }

        internal fun tableForOrder(order: Int): DoubleTable = tables[ORDERS.indexOf(order)]
    }
}
