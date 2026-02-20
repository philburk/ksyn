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

package com.softsynth.ksyn.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log
import kotlin.math.sin
import kotlin.math.PI

/**
 * Multiple tables of sawtooth data.
 * organized by octaves below the Nyquist Rate.
 * used to generate band-limited Sawtooth, Impulse, Pulse, Square and Triangle BL waveforms
 *
 * <pre>
 * Analysis of octave requirements for tables.
 *
 * OctavesIndex    Frequency     Partials
 * 0               N/2  11025      1
 * 1               N/4   5512      2
 * 2               N/8   2756      4
 * 3               N/16  1378      8
 * 4               N/32   689      16
 * 5               N/64   344      32
 * 6               N/128  172      64
 * 7               N/256   86      128
 * </pre>
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
class MultiTable(numTables: Int, cycleSize: Int) {
    private val phaseScalar: Double
    private val tables: Array<FloatArray>

    init {
        val tableSize = cycleSize + 1
        tables = Array(numTables) { FloatArray(tableSize) }
        val sineTable = tables[0]
        phaseScalar = cycleSize * 0.5

        /* Fill initial sine table with values for -PI to PI. */
        for (j in 0 until tableSize) {
            sineTable[j] = sin(((j.toDouble() / cycleSize.toDouble()) * PI * 2.0) - PI).toFloat()
        }

        /* Build each table from scratch and scale partials by raised cosine* to eliminate Gibbs effect. */
        for (i in 1 until numTables) {
            val table = tables[i]
            val numPartials = 1 shl i
            val kGibbs = PI / (2 * numPartials)

            for (k in 0 until numPartials) {
                var sineIndex = 0
                val partial = k + 1
                val cGibbs = cos(k * kGibbs)
                /* Calculate amplitude for Nth partial */
                val ampl = cGibbs * cGibbs / partial

                for (j in 0 until tableSize) {
                    table[j] += (ampl * sineTable[sineIndex]).toFloat()
                    sineIndex += partial
                    /* Wrap index at end of table.. */
                    if (sineIndex >= cycleSize) {
                        sineIndex -= cycleSize
                    }
                }
            }
        }

        /* Normalize after */
        for (i in 1 until numTables) {
            normalizeArray(tables[i])
        }
    }

    /** Phase ranges from -1.0 to +1.0 */
    fun calculateSawtooth(currentPhase: Double, positivePhaseIncrement: Double, flevel: Double): Double {
        val tableBase: FloatArray
        val valOut: Double
        val hiSam: Double
        val loSam: Double
        val sam1: Double
        val sam2: Double

        /* Use Phase to determine sampleIndex into table. */
        val findex = (phaseScalar * currentPhase) + phaseScalar
        val sampleIndex = findex.toInt()
        val horizontalFraction = findex - sampleIndex
        val tableIndex = flevel.toInt()

        if (tableIndex > (NUM_TABLES - 2)) {
            val fraction = positivePhaseIncrement * LOWEST_PHASE_INC_INV
            tableBase = tables[NUM_TABLES - 1]

            sam1 = tableBase[sampleIndex].toDouble()
            sam2 = tableBase[sampleIndex + 1].toDouble()
            loSam = sam1 + (horizontalFraction * (sam2 - sam1))
            valOut = currentPhase + (fraction * (loSam - currentPhase))
        } else {
            val verticalFraction = flevel - tableIndex

            if (tableIndex < 0) {
                if (tableIndex < -1) {
                    valOut = 0.0
                } else {
                    tableBase = tables[0]
                    sam1 = tableBase[sampleIndex].toDouble()
                    sam2 = tableBase[sampleIndex + 1].toDouble()
                    hiSam = sam1 + (horizontalFraction * (sam2 - sam1))
                    valOut = verticalFraction * hiSam
                }
            } else {
                tableBase = tables[tableIndex + 1]
                sam1 = tableBase[sampleIndex].toDouble()
                sam2 = tableBase[sampleIndex + 1].toDouble()
                hiSam = sam1 + (horizontalFraction * (sam2 - sam1))

                val lowerTableBase = tables[tableIndex]
                val lowerSam1 = lowerTableBase[sampleIndex].toDouble()
                val lowerSam2 = lowerTableBase[sampleIndex + 1].toDouble()
                loSam = lowerSam1 + (horizontalFraction * (lowerSam2 - lowerSam1))

                valOut = loSam + (verticalFraction * (hiSam - loSam))
            }
        }
        return valOut
    }

    fun convertPhaseIncrementToLevel(positivePhaseIncrement: Double): Double {
        var pInc = positivePhaseIncrement
        if (pInc < 1.0e-30) {
            pInc = 1.0e-30
        }
        return -1.0 - (log(pInc, 2.0))
    }

    companion object {
        const val NUM_TABLES = 8
        const val CYCLE_SIZE = 1 shl 10
        private val LOWEST_PHASE_INC_INV = (1 shl NUM_TABLES).toDouble()

        val instance = MultiTable(NUM_TABLES, CYCLE_SIZE)

        fun normalizeArray(fdata: FloatArray): Float {
            var max = 0.0f
            for (i in fdata.indices) {
                val value = abs(fdata[i])
                if (value > max) max = value
            }
            if (max < 0.0000001f) max = 0.0000001f
            val gain = 1.0f / max
            for (i in fdata.indices) fdata[i] *= gain
            return gain
        }
    }
}
