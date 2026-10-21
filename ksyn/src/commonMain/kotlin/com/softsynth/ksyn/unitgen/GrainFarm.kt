/*
 * Copyright 2011 Phil Burk, Mobileer Inc
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
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.toSample
import com.softsynth.ksyn.data.AudioSample
import com.softsynth.ksyn.util.PseudoRandom
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

/**
 * A unit generator that generates a cloud of sound using multiple Grains. Special thanks to my
 * friend Ross Bencina for his excellent article on Granular Synthesis. Several of his ideas are
 * reflected in this architecture. "Implementing Real-Time Granular Synthesis" by Ross Bencina,
 * Audio Anecdotes III, 2001.
 *
 * <pre><code>
 *    synth.add( sampleGrainFarm = GrainFarm() )
 *    grainFarm.allocate( NUM_GRAINS )
 * </code></pre>
 *
 * @author Phil Burk (C) 2011 Mobileer Inc
 * @see Grain
 * @see GrainSourceSine
 * @see RaisedCosineEnvelope
 */
open class GrainFarm : UnitGenerator(), UnitSource {
    /** A scaler for playback rate. Nominally 1.0. */
    val rate = UnitInputPort("Rate")
    val rateRange = UnitInputPort("RateRange")
    val amplitude = UnitInputPort("Amplitude")
    val amplitudeRange = UnitInputPort("AmplitudeRange")
    val density = UnitInputPort("Density")
    val duration = UnitInputPort("Duration")
    /** finalDuration = duration += duration * durationRange * randomNegativeOneToOne */
    val durationRange = UnitInputPort("DurationRange")
    val output = UnitOutputPort("Output")

    private var states: Array<GrainState>? = null
    private var countScaler: AudioSample = 1.0f
    private val scheduler: GrainScheduler = StochasticGrainScheduler()
    protected val randomizer = PseudoRandom()

    init {
        addPort(density)
        addPort(rate)
        addPort(rateRange)
        addPort(duration)
        addPort(durationRange)
        addPort(amplitude)
        addPort(amplitudeRange)
        addPort(output)

        // Setup sensible default ranges for the KSyn fader UI mapping
        rate.setup(0.1, 1.0, 8.0)
        rateRange.setup(0.0, 0.1, 2.0)
        amplitude.setup(0.0, 0.5, 1.0)
        amplitudeRange.setup(0.0, 0.0, 1.0)
        duration.setup(0.001, 0.005, 0.5)
        durationRange.setup(0.0, 0.0, 1.0)
        density.setup(0.001, 0.1, 1.0)
    }

    override fun getOutputPort() = output

    private inner class GrainState {
        lateinit var grain: Grain
        var countdown: Int = 0
        var lastDuration: Double = 0.0
        var state: Int = STATE_IDLE
        private var gapError: Double = 0.0
        /** The density when the current gap was chosen. */
        private var gapDensity: Double = 0.0

        fun next(i: Int): AudioSample {
            var out: AudioSample = 0.0f
            if (state == STATE_RUNNING) {
                if (grain.hasMoreValues()) {
                    out = grain.next()
                } else {
                    startGap(i)
                }
            } else if (state == STATE_GAP) {
                // A gap chosen at a very low density can be days long. If the density has
                // since risen a lot, for example an envelope coming back up from zero, choose
                // a new gap rather than waiting out the old one.
                if (density.getValues()[i] > gapDensity * GAP_RECHOOSE_RATIO) {
                    startGap(i)
                }
                if (countdown > 0) {
                    countdown -= 1
                }
                if (countdown <= 0) {
                    state = STATE_RUNNING
                    grain.reset()

                    // The rate is only known for sure once the synth is running, which is often
                    // after allocate(), and it changes if audio restarts on another device.
                    // Set it before the grain's rate and duration, which depend on it.
                    val currentFrameRate = frameRate.toFloat()
                    if (grain.frameRate != currentFrameRate) {
                        grain.frameRate = currentFrameRate
                    }

                    setupGrain(grain, i)

                    val dur = nextDuration(i)
                    grain.setDuration(dur)
                }
            } else if (state == STATE_IDLE) {
                nextDuration(i) // initialize lastDuration
                startGap(i)
            }
            return out
        }

        private fun nextDuration(i: Int): Double {
            var dur = duration.getValues()[i].toDouble()
            val range = durationRange.getValues()[i].toDouble()
            
            // Add a random range around the duration port value
            val offset = dur * randomizer.nextRandomDouble() * range
            dur += offset
            if (dur < 0.0001) dur = 0.0001 // Prevent negative or zero duration
            
            dur = scheduler.nextDuration(dur)
            lastDuration = dur
            return dur
        }

        private fun startGap(i: Int) {
            state = STATE_GAP
            val dens = density.getValues()[i].toDouble()
            gapDensity = dens
            var gap = scheduler.nextGap(lastDuration, dens) * frameRate
            gap += gapError
            val wholeFrames = floor(gap)
            countdown = min(wholeFrames, MAX_GAP_FRAMES).toInt()
            // Only the fraction of a frame carries over to the next gap. Carrying the part of a
            // huge gap that did not fit in countdown would make the next gap huge as well.
            gapError = gap - wholeFrames
        }
    }

    fun setGrainArray(grains: Array<Grain>) {
        countScaler = (1.0f / grains.size).toFloat()
        states = Array(grains.size) { GrainState() }
        for (i in states!!.indices) {
            states!![i].grain = grains[i]
            grains[i].frameRate = synthesisEngine?.frameRate?.toFloat() ?: 44100.0f
        }
    }

    open fun setupGrain(grain: Grain, i: Int) {
        val temp = rate.getValues()[i].toDouble() * calculateOctaveScaler(rateRange.getValues()[i].toDouble())
        grain.setRate(temp)

        // Scale the amplitude range so that we never go above
        // original amplitude.
        val base = amplitude.getValues()[i].toDouble()
        val offset = base * randomizer.random() * amplitudeRange.getValues()[i].toDouble()
        grain.amplitude = (base - offset).toFloat()
    }

    open fun allocate(numGrains: Int) {
        val grainArray = Array(numGrains) {
            Grain(GrainSourceSine(), RaisedCosineEnvelope())
        }
        setGrainArray(grainArray)
    }

    private fun calculateOctaveScaler(rangeValue: Double): Double {
        val octaveRange = 0.5 * randomizer.nextRandomDouble() * rangeValue
        return 2.0.pow(octaveRange)
    }

    override fun generate() {
        val outputs = output.getValues()
        val amplitudes = amplitude.getValues()
        
        val localStates = states ?: return // If no grains allocated, output nothing
        
        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            onFrame(i)
            var result: AudioSample = 0.0f

            // Mix the grains together.
            for (grainState in localStates) {
                result += grainState.next(i)
            }

            outputs[i] = result * amplitudes[i] * countScaler
        }
    }

    /**
     * Hook called once per frame inside [generate] before the active grains are mixed.
     * Subclasses such as [DelayGrainFarm] can override this to write incoming audio
     * into a delay buffer.
     */
    protected open fun onFrame(frameIndex: Int) {}

    companion object {
        /** Choose a new gap when the density rises above this multiple of the density the gap was chosen at. */
        private const val GAP_RECHOOSE_RATIO = 2.0

        /** Longest gap in frames, so a gap chosen at a density near zero still fits in an Int. */
        private const val MAX_GAP_FRAMES = 1.0e9

        private const val STATE_IDLE = 0
        private const val STATE_GAP = 1
        private const val STATE_RUNNING = 2
    }
}
