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
import com.softsynth.ksyn.dsp.BiquadLowPass
import com.softsynth.ksyn.dsp.SimpleDelay
import com.softsynth.ksyn.ports.UnitInputPort
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Base class for Karplus-Strong style string resonators.
 *
 * The signal on [input] excites a feedback loop made of a delay line, a fractional delay,
 * a two-pole Butterworth low pass filter and a loss factor. Use any signal as the excitation,
 * for example a burst of noise, an impulse, a sample or live audio.
 * The whole loop runs one sample at a time inside this unit, so the block size adds no latency.
 *
 * The [frequency] port sets the fundamental. All of the internal delays are included in the tuning.
 * The [decay] port sets the time in seconds for the fundamental to decay by 60 dB.
 * Lowering [decay] while the string rings acts like a damper.
 * The [brightness] port controls how quickly the upper harmonics decay relative to the fundamental.
 * It sets the cutoff of the loop filter relative to the fundamental, so the tone is similar
 * at every pitch. The filter is nearly flat up to the fundamental, so [decay] and [brightness]
 * are mostly independent. The loop gain must stay below 1.0, so a very long [decay]
 * with a dark setting may decay sooner than requested, especially at high pitches.
 *
 * Subclasses provide the fractional delay.
 *
 * @param minFrequency lowest frequency supported, determines the size of the delay line
 * @see FixedStringResonator
 * @see VariableStringResonator
 */
abstract class StringResonator(val minFrequency: Double = DEFAULT_MIN_FREQUENCY) : UnitFilter(),
    SupportsAutoDisable {
    /** Fundamental frequency in Hz. */
    val frequency = UnitInputPort("Frequency", 220.0)

    /** Time in seconds for the fundamental to decay by 60 dB. */
    val decay = UnitInputPort("Decay", 2.0)

    /**
     * 0.0 is dark, like a nylon string, with the loop filter cutoff at 3 times the fundamental.
     * 1.0 is bright, like a steel string, with the cutoff at 256 times the fundamental.
     */
    val brightness = UnitInputPort("Brightness", 0.5)

    protected val delayLine = SimpleDelay(ceil(MAX_FRAME_RATE / minFrequency).toInt() + EXTRA_FRAMES)
    private val loopFilter = BiquadLowPass()
    /** Delay of the loop filter at the fundamental, in frames. */
    private var filterDelay = 0.0
    private var loopGain = 0.0f
    private var denormalOffset = DENORMAL_OFFSET

    /** Range of frequencies that fit in the delay line at the current frame rate. */
    protected var lowestFrequency = minFrequency
        private set
    protected var highestFrequency = 1000.0
        private set

    // Parameters for the current block. Recalculate only when they change.
    private var blockFrequency = -1.0
    private var blockDecay = -1.0
    private var blockBrightness = -1.0

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
        addPort(frequency)
        addPort(decay)
        addPort(brightness)
        frequency.setup(minFrequency, 220.0, 2000.0)
        decay.setup(0.01, 2.0, 30.0)
        brightness.setup(0.0, 0.5, 1.0)
    }

    /**
     * Calculate the fractional delay for this frequency.
     * Called at the start of a block whenever the frequency or brightness changes.
     *
     * @param frequency already limited to the supported range
     */
    protected abstract fun updateTuning(frequency: Double)

    /**
     * Read from the delay line, including the fractional delay.
     * The delay should be [delayFor] the frequency.
     *
     * @param frequency value of the frequency port for this frame, not limited
     */
    protected abstract fun readDelayed(frequency: Float): Float

    /** Clear the state of the fractional delay. */
    protected open fun clearTuning() {}

    /**
     * @return the delay in frames that the delay line and fractional delay must provide
     *    so that the whole loop is tuned to the frequency
     */
    protected fun delayFor(frequency: Double): Double {
        // The filter delay is calculated once per block, at the fundamental.
        return max(MIN_DELAY, (frameRate / frequency) - filterDelay)
    }

    protected fun limitFrequency(frequency: Double): Double {
        return frequency.coerceIn(lowestFrequency, highestFrequency)
    }

    override fun generate() {
        val inputs = input.getValues()
        val outputs = output.getValues()
        val frequencies = frequency.getValues()

        updateLoop(frequencies[0].toDouble())

        // Alternate the sign so the offset has no DC to build up in the loop.
        denormalOffset = -denormalOffset
        var peak = 0.0f
        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            val delayed = readDelayed(frequencies[i])
            val feedback = loopGain * loopFilter.process(delayed)
            val excitation = inputs[i]
            val x = excitation + feedback + denormalOffset
            delayLine.write(x)
            delayLine.advance()
            outputs[i] = x
            peak = max(peak, max(abs(x), abs(excitation)))
        }

        if (autoDisableEnabled) {
            checkAutoDisable(peak)
        }
    }

    private fun updateLoop(rawFrequency: Double) {
        val frameRateLocal = frameRate
        // Leave room for the interpolation taps.
        val maxDelay = (delayLine.length - EXTRA_FRAMES).toDouble()
        lowestFrequency = max(minFrequency, frameRateLocal / maxDelay)
        highestFrequency = frameRateLocal / MIN_PERIOD
        val currentFrequency = limitFrequency(rawFrequency)
        val currentDecay = decay.getValues()[0].toDouble()
        val currentBrightness = brightness.getValues()[0].toDouble()

        val omega = 2.0 * PI * currentFrequency / frameRateLocal
        val toneChanged = currentFrequency != blockFrequency || currentBrightness != blockBrightness
        if (toneChanged) {
            val ratio = MIN_CUTOFF_RATIO * (MAX_CUTOFF_RATIO / MIN_CUTOFF_RATIO)
                .pow(currentBrightness.coerceIn(0.0, 1.0))
            loopFilter.setCutoff(currentFrequency * ratio, frameRateLocal)
            filterDelay = loopFilter.phaseDelayAt(omega)
            updateTuning(currentFrequency)
        }
        if (toneChanged || currentDecay != blockDecay) {
            loopGain = if (currentDecay <= 0.0) {
                0.0f
            } else {
                // Loss per trip so the fundamental decays 60 dB in currentDecay seconds.
                val loss = 10.0.pow(-3.0 / (currentDecay * currentFrequency))
                // Compensate for the filter loss at the fundamental, but never let the loop gain reach 1.0.
                min(MAX_LOOP_GAIN, loss / loopFilter.gainAt(omega)).toFloat()
            }
        }
        blockFrequency = currentFrequency
        blockDecay = currentDecay
        blockBrightness = currentBrightness
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
     * Disable the unit when the string becomes quiet. This can be used to reduce CPU load.
     * The unit starts off disabled.
     *
     * A disabled unit is not run, so it cannot notice a new excitation.
     * Whatever starts the excitation must enable the unit again, for example in noteOn().
     *
     * @param unit the unit to disable, for example a Circuit containing this resonator.
     *     Defaults to this resonator.
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

    /** Silence the string by clearing the delay line and filters. */
    fun clear() {
        delayLine.clear()
        loopFilter.clear()
        clearTuning()
        quietFrames = 0
    }

    companion object {
        const val DEFAULT_MIN_FREQUENCY = 40.0

        /** The delay line is allocated so that minFrequency works up to this frame rate. */
        const val MAX_FRAME_RATE = 96000.0

        /** Highest frequency is frameRate / MIN_PERIOD. */
        const val MIN_PERIOD = 4.0

        /** Extra frames for interpolation taps. */
        private const val EXTRA_FRAMES = 4

        /** Smallest delay the subclasses can interpolate. */
        private const val MIN_DELAY = 2.0

        /** Loop filter cutoff, relative to the fundamental, at brightness 0.0 and 1.0. */
        private const val MIN_CUTOFF_RATIO = 3.0
        private const val MAX_CUTOFF_RATIO = 256.0

        /** Keeps the loop out of the slow denormal range as it decays. Inaudible at about -360 dB. */
        private const val DENORMAL_OFFSET = 1.0e-18f

        private const val MAX_LOOP_GAIN = 0.99999
    }
}
