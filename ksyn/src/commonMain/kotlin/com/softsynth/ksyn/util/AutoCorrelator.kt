package com.softsynth.ksyn.util

import kotlin.math.pow

class AutoCorrelator(numFrames: Int) : SignalCorrelator {
    private val buffer: FloatArray = FloatArray(numFrames)
    private val maxWindowSize: Int = buffer.size / 2
    private val diffs1: FloatArray = FloatArray(2 + numFrames / 2)
    private val diffs2: FloatArray = FloatArray(diffs1.size)
    private var _diffs: FloatArray = diffs1
    private var cursor: Int = -1
    private var tau: Int = 0

    private var sumProducts: Float = 0.0f
    private var sumSquares: Float = 0.0f
    private var localMaximum: Float = 0.0f
    private var localPosition: Int = 0
    private var bestMaximum: Float = 0.0f
    private var bestPosition: Int = -1
    private var peakCounter: Int = 0
    var pitchCorrectionFactor: Float = 0.99988f

    override var period: Double = 2.0
        private set
    override var confidence: Double = 0.0
        private set

    private val minPeriod = 2
    private var bufferValid: Boolean = false
    private var previousSample: Double = 0.0
    private val noiseThreshold = 0.001f
    private var state: Int = STATE_SEEKING_NEGATIVE

    companion object {
        private const val SUB_OCTAVE_REJECTION_FACTOR = 0.0005f
        private const val STATE_SEEKING_NEGATIVE = 0
        private const val STATE_SEEKING_POSITIVE = 1
        private const val STATE_SEEKING_MAXIMUM = 2
        private val tauAdvanceByState = intArrayOf(4, 2, 1)

        fun interpolatePeak(d1: Double, d2: Double, d3: Double): Double {
            return 0.5 * (d1 - d3) / (d1 - (2.0 * d2) + d3)
        }
    }

    init {
        reset()
    }

    private fun rawDeltaScan(last1: Int, last2: Int, count: Int, stride: Int) {
        var k = 0
        while (k < count) {
            val d1 = buffer[last1 - k]
            val d2 = buffer[last2 - k]
            sumProducts += d1 * d2
            sumSquares += ((d1 * d1) + (d2 * d2))
            k += stride
        }
    }

    private fun splitDeltaScan(last1: Int, splitLast: Int, count: Int, stride: Int) {
        rawDeltaScan(last1, splitLast, splitLast, stride)
        rawDeltaScan(last1 - splitLast, buffer.size - 1, count - splitLast, stride)
    }

    private fun checkDeltaScan(last1: Int, last2: Int, count: Int, stride: Int) {
        if (count > last2) {
            checkDeltaScan(last2, last1, last2, stride)
            checkDeltaScan(buffer.size - 1, last1 - last2, count - last2, stride)
        } else if (count > last1) {
            splitDeltaScan(last2, last1, count, stride)
        } else {
            rawDeltaScan(last1, last2, count, stride)
        }
    }

    private fun topScan(last1: Int, tau: Int, count: Int, stride: Int): Float {
        val minimumResult = 0.00000001f

        var last2 = last1 - tau
        if (last2 < 0) {
            last2 += buffer.size
        }
        sumProducts = 0.0f
        sumSquares = 0.0f
        checkDeltaScan(last1, last2, count, stride)
        
        if (sumSquares < minimumResult) {
            return minimumResult
        }
        val correction = pitchCorrectionFactor.toDouble().pow(tau.toDouble()).toFloat()

        return ((2.0 * sumProducts / sumSquares) * correction).toFloat()
    }

    private fun reset() {
        switchDiffs()
        var i = 0
        while (i < minPeriod) {
            _diffs[i] = 1.0f
            i++
        }
        while (i < _diffs.size) {
            _diffs[i] = 0.0f
            i++
        }
        tau = minPeriod
        state = STATE_SEEKING_NEGATIVE
        peakCounter = 0
        bestMaximum = -1.0f
        bestPosition = -1
    }

    private fun nextPeakAnalysis(index: Int) {
        val value = _diffs[index] * (1.0f - (index * SUB_OCTAVE_REJECTION_FACTOR))
        when (state) {
            STATE_SEEKING_NEGATIVE -> {
                if (value < -0.01f) {
                    state = STATE_SEEKING_POSITIVE
                }
            }
            STATE_SEEKING_POSITIVE -> {
                if (value > 0.2f) {
                    state = STATE_SEEKING_MAXIMUM
                    localMaximum = value
                    localPosition = index
                }
            }
            STATE_SEEKING_MAXIMUM -> {
                if (value > localMaximum) {
                    localMaximum = value
                    localPosition = index
                } else if (value < -0.1f) {
                    peakCounter += 1
                    if (localMaximum > bestMaximum) {
                        bestMaximum = localMaximum
                        bestPosition = localPosition
                    }
                    state = STATE_SEEKING_POSITIVE
                }
            }
        }
    }

    private fun findPreciseMaximum(indexMax: Int): Double {
        if (indexMax < 3) return 3.0
        if (indexMax == (_diffs.size - 1)) return indexMax.toDouble()

        val d1 = _diffs[indexMax - 1].toDouble()
        val d2 = _diffs[indexMax].toDouble()
        val d3 = _diffs[indexMax + 1].toDouble()

        return interpolatePeak(d1, d2, d3) + indexMax
    }

    private fun incrementalAnalysis(): Boolean {
        var updated = false
        if (bufferValid) {
            val windowSize = ((tau * confidence) + (maxWindowSize * (1.0 - confidence))).toInt()
            val stride = 1

            _diffs[tau] = topScan(cursor, tau, windowSize, stride)

            if ((tau == minPeriod) && (sumProducts < noiseThreshold)) {
                val result = (confidence > 0.0)
                confidence = 0.0
                return result
            }

            nextPeakAnalysis(tau)

            tau += 1
            var advance = tauAdvanceByState[state] - 1
            while ((advance > 0) && (tau < _diffs.size)) {
                _diffs[tau] = _diffs[tau - 1]
                tau++
                advance--
            }

            if ((peakCounter >= 4) || (tau >= maxWindowSize)) {
                if (bestMaximum > 0.0) {
                    period = findPreciseMaximum(bestPosition)
                    confidence = if (bestMaximum < 0.0) 0.0 else bestMaximum.toDouble()
                } else {
                    confidence = 0.0
                }
                updated = true
                reset()
            }
        }
        return updated
    }

    override val diffs: FloatArray
        get() = if (_diffs === diffs1) diffs2 else diffs1

    private fun switchDiffs() {
        _diffs = if (_diffs === diffs1) diffs2 else diffs1
    }

    override fun addSample(value: Double): Boolean {
        val average = (value + previousSample) * 0.5
        previousSample = value

        cursor += 1
        if (cursor == buffer.size) {
            cursor = 0
            bufferValid = true
        }
        buffer[cursor] = average.toFloat()

        return incrementalAnalysis()
    }
}
