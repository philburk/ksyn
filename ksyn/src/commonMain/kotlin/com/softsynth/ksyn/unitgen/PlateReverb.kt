/*
 * Copyright 2022 Phil Burk
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
import com.softsynth.ksyn.dsp.AllPassDelay
import com.softsynth.ksyn.dsp.SimpleDelay
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.util.PseudoRandom
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Simple reverberation effect based on a "figure eight"
 * network of all-pass filters and delays.
 *
 * This reverb does not have a pre-delay or early reflections.
 * It can be used as the "tail" of a more complex reverb that
 * adds those functions.
 *
 * The algorithm is based on
 * "Effect Design Part 1: Reverberator and Other Filters"
 * by Jon Dattorro, CCRMA, Stanford University 1996
 *
 * @see InterpolatingDelay
 */
class PlateReverb(size: Double = 1.0) : UnitGenerator() {

    /**
     * Mono input.
     */
    val input: UnitInputPort

    /**
     * Approximate time in seconds to decay by -60 dB.
     */
    val time: UnitInputPort

    /**
     * Damping factor for the feedback filters.
     * Must be between 0.0 and 1.0. Default is 0.5.
     */
    val damping: UnitInputPort

    /**
     * Stereo output.
     */
    val output: UnitOutputPort

    companion object {
        private const val MAX_DECAY = 0.98
        // These default values are based on table-1 of the paper by Jon Dattorro.
        private const val DECAY_DIFFUSION_1 = 0.70f
        private const val DECAY_DIFFUSION_2 = 0.50f
        private const val INPUT_DIFFUSION_1 = 0.75f
        private const val INPUT_DIFFUSION_2 = 0.625f
        private const val DAMPING = 0.5f // Must match default comment above for damping port.
        private const val BANDWIDTH = 0.99995f
    }

    private class RandomModulator {
        private val randomNum = PseudoRandom()
        protected var prevNoise = 0.0f
        protected var currNoise = 0.0f
        private var mPhase = 0.0f
        private var mPhaseIncrement = 0.0f

        fun setFrequency(frequency: Float, sampleRate: Float) {
            mPhaseIncrement = frequency / sampleRate
        }

        // Generate ramps between random points between -1.0 and +1.0.
        fun generate(): Float {
            mPhase += mPhaseIncrement

            // calculate new random value whenever phase passes 1.0
            if (mPhase > 1.0f) {
                prevNoise = currNoise
                currNoise = randomNum.nextRandomDouble().toFloat()
                // reset phase for interpolation
                mPhase -= 1.0f
            }

            // interpolate current
            return prevNoise + (mPhase * (currNoise - prevNoise))
        }
    }

    /**
     * Allpass delay modulated by a random ramp.
     */
    private class VariableAllPassDelay(length: Int, coefficient: Float) {
        private val mModulator = RandomModulator()
        private val mBuffer: FloatArray = FloatArray(2 * length)
        private val mLength: Int = length
        private var mCursor: Int = 0
        private var mModulationDepth: Int = 0
        private val mCoefficient: Float = coefficient

        init {
            setModulationDepth(40)
        }

        fun setModulationDepth(depthInFrames: Int) {
            mModulationDepth = min(depthInFrames, mLength / 3)
        }

        fun setFrequency(frequency: Float, sampleRate: Float) {
            mModulator.setFrequency(frequency, sampleRate)
        }

        fun process(input: Float): Float {
            var readCursor = mCursor - mLength
            readCursor += (mModulator.generate() * mModulationDepth).toInt()
            if (readCursor < 0) readCursor += mBuffer.size

            val z = mBuffer[readCursor]

            val x = input - (z * mCoefficient)
            mBuffer[mCursor] = x
            mCursor++
            if (mCursor >= mBuffer.size) mCursor = 0
            return z + (x * mCoefficient)
        }
    }

    // y = x*c + y*(1-c)
    private class OnePoleLowPassFilter(coefficient: Float) {
        private var mDelay: Float = 0.0f
        private var mCoefficient: Float = coefficient

        fun process(input: Float): Float {
            val output = (input * mCoefficient) + (mDelay * (1.0f - mCoefficient))
            mDelay = output
            return output
        }

        fun setCoefficient(coefficient: Float) {
            mCoefficient = coefficient
        }
    }

    // One side of the figure eight.
    private class ReverbSide(d1: Int, d2: Int, d3: Int, d4: Int) {
        val variableDelay: VariableAllPassDelay
        val mLowPass = OnePoleLowPassFilter(1.0f - DAMPING)
        val mDelay1: SimpleDelay
        val mAllPassDelay: AllPassDelay
        val mDelay2: SimpleDelay
        private val outputScaler = 0.6f
        var mOutput: Float = 0.0f
        var mDecay: Float = 0.0f

        init {
            // This all pass reverses the signs.
            variableDelay = VariableAllPassDelay(d1, 0.0f - DECAY_DIFFUSION_1)
            mDelay1 = SimpleDelay(d2)
            mAllPassDelay = AllPassDelay(d3, DECAY_DIFFUSION_2)
            mDelay2 = SimpleDelay(d4)
        }

        fun setFrequency(frequency: Float, sampleRate: Float) {
            variableDelay.setFrequency(frequency, sampleRate)
        }

        fun process(input: Float): Float {
            var temp = variableDelay.process(input)
            mOutput = temp
            temp = mDelay1.process(temp)
            mOutput -= temp
            temp = mLowPass.process(temp)
            temp *= mDecay
            temp = mAllPassDelay.process(temp)
            mOutput += temp
            temp = mDelay2.process(temp)
            temp *= mDecay
            mOutput -= temp
            return temp
        }

        fun getOutput(): Float {
            return mOutput * outputScaler
        }

        fun setDamping(damping: Float) {
            mLowPass.setCoefficient(1.0f - damping)
        }
    }

    private var mDecay: Float = 0.0f
    private var mLeftFeedback: Float = 0.0f
    private var mRightFeedback: Float = 0.0f
    private var mSize: Double = 1.0
    private var mPreviousTime: Double = -1.0

    private val mBandwidthLowPass = OnePoleLowPassFilter(BANDWIDTH)
    private val mDiffusion1: AllPassDelay
    private val mDiffusion2: AllPassDelay
    private val mDiffusion3: AllPassDelay
    private val mDiffusion4: AllPassDelay
    private val mLeftSide: ReverbSide
    private val mRightSide: ReverbSide

    init {
        addPort(UnitInputPort("Input").also { input = it })

        mSize = max(0.05, min(5.0, size))
        
        addPort(UnitInputPort("Time").also { time = it })
        time.setup(0.01, 2.0, 30.0)
        
        addPort(UnitInputPort("Damping").also { damping = it })
        damping.setup(0.0001, DAMPING.toDouble(), 1.0)

        addPort(UnitOutputPort(2, "Output").also { output = it })

        // delay line sizes
        // These are aligned to nearby primes from the Jon Dattorro paper.
        val zs = intArrayOf(
            149, 107, 379, 277, // diffusion
            677, 4453, 1801, 3727, // left
            911, 4217, 2657, 3169 // right
        )

        mDiffusion1 = AllPassDelay((zs[0] * mSize).toInt(), INPUT_DIFFUSION_1)
        mDiffusion2 = AllPassDelay((zs[1] * mSize).toInt(), INPUT_DIFFUSION_1)
        mDiffusion3 = AllPassDelay((zs[2] * mSize).toInt(), INPUT_DIFFUSION_2)
        mDiffusion4 = AllPassDelay((zs[3] * mSize).toInt(), INPUT_DIFFUSION_2)
        
        mLeftSide = ReverbSide(
            (zs[4] * mSize).toInt(), (zs[5] * mSize).toInt(),
            (zs[6] * mSize).toInt(), (zs[7] * mSize).toInt()
        )
        mRightSide = ReverbSide(
            (zs[8] * mSize).toInt(), (zs[9] * mSize).toInt(),
            (zs[10] * mSize).toInt(), (zs[11] * mSize).toInt()
        )
    }

    override fun generate() {
        val frameRateLocal = (synthesisEngine?.frameRate ?: 44100.0).toFloat()
        mLeftSide.setFrequency(0.7f, frameRateLocal)
        mRightSide.setFrequency(1.2f, frameRateLocal)
        
        val inputs = input.getValues()
        val leftOutputs = output.getValues(0)
        val rightOutputs = output.getValues(1)

        val timeValue = time.getValues()[0].toDouble()
        if (timeValue != mPreviousTime) {
            mDecay = convertTimeToDecay(mSize, timeValue).toFloat()
            mPreviousTime = timeValue
            
            // Pass decay down to the sub-circuits
            mLeftSide.mDecay = mDecay
            mRightSide.mDecay = mDecay
        }
        
        val dampingValue = damping.getValues()[0]
        mLeftSide.setDamping(dampingValue)
        mRightSide.setDamping(dampingValue)
        
        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            process(inputs[i])
            leftOutputs[i] = mLeftSide.getOutput()
            rightOutputs[i] = mRightSide.getOutput()
        }
    }

    private fun process(input: Float) {
        var x = mBandwidthLowPass.process(input)
        x = mDiffusion1.process(x)
        x = mDiffusion2.process(x)
        x = mDiffusion3.process(x)
        x = mDiffusion4.process(x)
        
        // left side of the figure eight uses right side feedback
        val leftSum = x + mRightFeedback
        mLeftFeedback = mLeftSide.process(leftSum)
        
        // right side of the figure eight uses left side feedback
        val rightSum = x + mLeftFeedback
        mRightFeedback = mRightSide.process(rightSum)
    }

    private fun convertTimeToDecay(size: Double, time: Double): Double {
        val exponent = (0.52 - (time / size)) / 4.7
        val square = 1.001 - exp(exponent)
        val decay = sqrt(max(0.0, square)) // avoid sqrt(negative)
        return min(MAX_DECAY, decay)
    }
}
