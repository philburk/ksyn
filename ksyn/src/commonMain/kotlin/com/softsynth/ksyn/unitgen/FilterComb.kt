/*
 * Copyright 2026 Phil Burk
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
import com.softsynth.ksyn.dsp.AllPassFractionalDelay
import com.softsynth.ksyn.dsp.FractionalDelay
import com.softsynth.ksyn.dsp.LagrangeFractionalDelay
import com.softsynth.ksyn.ports.UnitInputPort
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Feedback comb filter, tuned to a frequency.
 *
 * The output is the input plus a delayed copy of the output:
 *
 * <pre>
 * y(n) = x(n) + gain * y(n - frameRate / frequency)
 * </pre>
 *
 * It resonates at the [frequency] and all of its harmonics.
 * The delay can be a fractional number of frames, so the tuning is exact.
 * The feedback loop runs one frame at a time inside this unit, so the block size adds no latency.
 *
 * The [decay] port sets the time in seconds for the resonance to decay by 60 dB.
 * The gain is calculated from the decay and the frequency.
 *
 * @param minFrequency lowest frequency supported, determines the size of the delay line
 * @param interpolation how the fractional delay is calculated.
 *     Use [Interpolation.LAGRANGE] if the frequency will change smoothly, for example with an LFO.
 *     Use [Interpolation.ALL_PASS] for fixed frequencies, because it has a perfectly flat gain.
 * @see StringResonator
 */
open class FilterComb(
    val minFrequency: Double = DEFAULT_MIN_FREQUENCY,
    val interpolation: Interpolation = Interpolation.LAGRANGE,
) : UnitFilter(), SupportsAutoDisable {

    enum class Interpolation {
        /** First-order allpass. Flat gain, but changing the frequency can cause a small transient. */
        ALL_PASS,

        /** 3rd order Lagrange. Smooth when the frequency changes, with a little high frequency loss. */
        LAGRANGE,
    }

    /** Fundamental frequency in Hz. */
    val frequency = UnitInputPort("Frequency", 220.0)

    /** Time in seconds for the fundamental to decay by 60 dB. */
    val decay = UnitInputPort("Decay", 2.0)

    private val fractionalDelay: FractionalDelay
    private var loopGain = 0.0f
    private var denormalOffset = DENORMAL_OFFSET

    /** Delay of the loop filter at the fundamental, in frames. */
    private var filterDelay = 0.0

    /** Range of frequencies that fit in the delay line at the current frame rate. */
    protected var lowestFrequency = minFrequency
        private set
    protected var highestFrequency = 1000.0
        private set

    // Parameters for the current block. Recalculate only when they change.
    private var blockFrequency = -1.0
    private var blockDecay = -1.0

    private var autoDisableTarget: UnitGenerator? = null
    private var autoDisableEnabled = false

    override val isAutoDisableEnabled: Boolean
        get() = autoDisableEnabled

    /** The unit is disabled when the signal in the loop stays below this level. Default is -90 dB. */
    var autoDisableThreshold = 3.0e-5f

    /** Minimum time in seconds that the loop must be quiet before it is disabled. */
    var autoDisableQuietTime = 0.01
    private var quietFrames = 0

    init {
        val maxDelay = ceil(MAX_FRAME_RATE / minFrequency).toInt()
        fractionalDelay = when (interpolation) {
            Interpolation.ALL_PASS -> AllPassFractionalDelay(maxDelay)
            Interpolation.LAGRANGE -> LagrangeFractionalDelay(maxDelay)
        }
        addPort(frequency)
        addPort(decay)
        frequency.setup(minFrequency, 220.0, 2000.0)
        decay.setup(0.01, 2.0, 30.0)
    }

    /**
     * Called at the start of every block so a subclass can update a filter inside the loop.
     *
     * @param frequency fundamental, already limited to the supported range
     * @param omega radian frequency of the fundamental, 2 * PI * frequency / frameRate
     * @return true if the filter changed, so that the tuning and gain are recalculated
     */
    protected open fun updateLoopFilter(frequency: Double, omega: Double): Boolean = false

    /** Filter one frame inside the loop. The default does nothing. */
    protected open fun filterLoop(input: Float): Float = input

    /** @return delay of the loop filter in frames at this radian frequency */
    protected open fun loopFilterDelayAt(omega: Double): Double = 0.0

    /** @return gain of the loop filter at this radian frequency */
    protected open fun loopFilterGainAt(omega: Double): Double = 1.0

    /** Set the state of the loop filter to zero. */
    protected open fun clearLoopFilter() {}

    /**
     * @return the delay in frames that the delay line must provide
     *    so that the whole loop is tuned to the frequency
     */
    private fun delayFor(frequency: Double): Double {
        // The filter delay is calculated once per block, at the fundamental.
        return (frameRate / frequency) - filterDelay
    }

    private fun limitFrequency(frequency: Double): Double {
        return frequency.coerceIn(lowestFrequency, highestFrequency)
    }

    override fun generate() {
        val inputs = input.getValues()
        val outputs = output.getValues()
        val frequencies = frequency.getValues()

        updateLoop(frequencies[0].toDouble())

        // Alternate the sign so the offset has no DC to build up in the loop.
        denormalOffset = -denormalOffset
        val followFrequency = fractionalDelay.isSmooth
        var peak = 0.0f
        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            if (followFrequency) {
                fractionalDelay.setDelay(delayFor(limitFrequency(frequencies[i].toDouble())), 0.0)
            }
            val feedback = loopGain * filterLoop(fractionalDelay.read())
            val excitation = inputs[i]
            val x = excitation + feedback + denormalOffset
            fractionalDelay.write(x)
            outputs[i] = x
            peak = max(peak, max(abs(x), abs(excitation)))
        }

        if (autoDisableEnabled) {
            checkAutoDisable(peak)
        }
    }

    private fun updateLoop(rawFrequency: Double) {
        val frameRateLocal = frameRate
        lowestFrequency = max(minFrequency, frameRateLocal / fractionalDelay.maxDelay)
        highestFrequency = frameRateLocal / MIN_PERIOD
        val currentFrequency = limitFrequency(rawFrequency)
        val currentDecay = decay.getValues()[0].toDouble()
        val omega = 2.0 * PI * currentFrequency / frameRateLocal

        val filterChanged = updateLoopFilter(currentFrequency, omega)
        val toneChanged = filterChanged || currentFrequency != blockFrequency
        if (toneChanged) {
            filterDelay = loopFilterDelayAt(omega)
            fractionalDelay.setDelay(delayFor(currentFrequency), omega)
        }
        if (toneChanged || currentDecay != blockDecay) {
            loopGain = if (currentDecay <= 0.0) {
                0.0f
            } else {
                // Loss per trip so the fundamental decays 60 dB in currentDecay seconds.
                val loss = 10.0.pow(-3.0 / (currentDecay * currentFrequency))
                // Compensate for the filter loss at the fundamental, but never let the loop gain reach 1.0.
                min(MAX_LOOP_GAIN, loss / loopFilterGainAt(omega)).toFloat()
            }
        }
        blockFrequency = currentFrequency
        blockDecay = currentDecay
    }

    private fun checkAutoDisable(peak: Float) {
        if (peak < autoDisableThreshold) {
            quietFrames += Synthesizer.FRAMES_PER_BLOCK
            // Wait at least one period so that a pulse traveling around the loop is not missed.
            val period = frameRate / max(blockFrequency, lowestFrequency)
            val framesNeeded = max(period, autoDisableQuietTime * frameRate)
            if (quietFrames >= framesNeeded) {
                clear()
                (autoDisableTarget ?: this).isEnabled = false
            }
        } else {
            quietFrames = 0
        }
    }

    /**
     * Disable the unit when the loop becomes quiet. This can be used to reduce CPU load.
     * The unit starts off disabled.
     *
     * A disabled unit is not run, so it cannot notice a new input signal.
     * Whatever starts the input must enable the unit again, for example in noteOn().
     *
     * @param unit the unit to disable, for example a Circuit containing this unit.
     *     Defaults to this unit.
     */
    override fun setupAutoDisable(unit: UnitGenerator) {
        autoDisableTarget = unit
        autoDisableEnabled = true
        quietFrames = 0
        unit.isEnabled = false
    }

    override fun setAutoDisableEnabled(enabled: Boolean) {
        val wasEnabled = autoDisableEnabled
        quietFrames = 0
        autoDisableEnabled = enabled
        if (wasEnabled && !enabled) {
            // Nothing would enable it again, so let it run.
            (autoDisableTarget ?: this).isEnabled = true
        }
    }

    /** Silence the resonance by clearing the delay line and filters. */
    fun clear() {
        fractionalDelay.clear()
        clearLoopFilter()
        quietFrames = 0
    }

    companion object {
        const val DEFAULT_MIN_FREQUENCY = 40.0

        /** The delay line is allocated so that minFrequency works up to this frame rate. */
        const val MAX_FRAME_RATE = 96000.0

        /** Highest frequency is frameRate / MIN_PERIOD. */
        const val MIN_PERIOD = 4.0

        /** Keeps the loop out of the slow denormal range as it decays. Inaudible at about -360 dB. */
        private const val DENORMAL_OFFSET = 1.0e-18f

        private const val MAX_LOOP_GAIN = 0.99999
    }
}
