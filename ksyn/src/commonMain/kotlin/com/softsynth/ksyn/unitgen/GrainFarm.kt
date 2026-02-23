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
class GrainFarm : UnitGenerator(), UnitSource {
    /** A scaler for playback rate. Nominally 1.0. */
    val rate = UnitInputPort("Rate", 1.0)
    val rateRange = UnitInputPort("RateRange", 0.0)
    val amplitude = UnitInputPort("Amplitude", 1.0)
    val amplitudeRange = UnitInputPort("AmplitudeRange", 0.0)
    val density = UnitInputPort("Density", 0.1)
    val duration = UnitInputPort("Duration", 0.01)
    val durationRange = UnitInputPort("DurationRange", 0.0)
    val output = UnitOutputPort("Output")

    private var states: Array<GrainState>? = null
    private var countScaler: AudioSample = 1.0f
    private val scheduler: GrainScheduler = StochasticGrainScheduler()
    private val randomizer = PseudoRandom()

    init {
        addPort(rate)
        addPort(amplitude)
        addPort(duration)
        addPort(rateRange)
        addPort(amplitudeRange)
        addPort(durationRange)
        addPort(density)
        addPort(output)
    }

    override fun getOutputPort() = output

    private inner class GrainState {
        lateinit var grain: Grain
        var countdown: Int = 0
        var lastDuration: Double = 0.0
        var state: Int = STATE_IDLE
        private var gapError: Double = 0.0

        fun next(i: Int): AudioSample {
            var out: AudioSample = 0.0f
            if (state == STATE_RUNNING) {
                if (grain.hasMoreValues()) {
                    out = grain.next()
                } else {
                    startGap(i)
                }
            } else if (state == STATE_GAP) {
                if (countdown > 0) {
                    countdown -= 1
                } else if (countdown == 0) {
                    state = STATE_RUNNING
                    grain.reset()

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
            dur = scheduler.nextDuration(dur)
            lastDuration = dur
            return dur
        }

        private fun startGap(i: Int) {
            state = STATE_GAP
            val dens = density.getValues()[i].toDouble()
            var gap = scheduler.nextGap(lastDuration, dens) * (synthesisEngine?.frameRate?.toDouble() ?: 44100.0)
            gap += gapError
            countdown = gap.toInt()
            gapError = gap - countdown
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

    fun setupGrain(grain: Grain, i: Int) {
        val temp = rate.getValues()[i].toDouble() * calculateOctaveScaler(rateRange.getValues()[i].toDouble())
        grain.setRate(temp)

        // Scale the amplitude range so that we never go above
        // original amplitude.
        val base = amplitude.getValues()[i].toDouble()
        val offset = base * randomizer.nextRandomDouble() * amplitudeRange.getValues()[i].toDouble()
        grain.amplitude = (base - offset).toFloat()
    }

    fun allocate(numGrains: Int) {
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
            var result: AudioSample = 0.0f

            // Mix the grains together.
            for (grainState in localStates) {
                result += grainState.next(i)
            }

            outputs[i] = result * amplitudes[i] * countScaler
        }
    }

    companion object {
        private const val STATE_IDLE = 0
        private const val STATE_GAP = 1
        private const val STATE_RUNNING = 2
    }
}
