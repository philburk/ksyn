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
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import kotlin.math.max
import kotlin.math.min

/**
 * Simulate reverberation in a room using a MultiTapDelay to model the early reflections
 * and a PlateReverb to provide diffusion.
 *
 * @author (C) 2022 Phil Burk, Mobileer Inc
 * @see MultiTapDelay
 * @see PlateReverb
 */
class RoomReverb(size: Double = 1.0) : Circuit() {
    /** Mono input. */
    val input: UnitInputPort

    /** Pre-delay time in milliseconds. */
    val preDelayMillis: UnitInputPort

    /** Approximate time in seconds to decay by -60 dB. */
    val time: UnitInputPort

    /** Damping factor for the feedback filters. Must be <= 1.0. Default is 0.5. */
    val damping: UnitInputPort

    /** Amount of multi-tap delay in the output mix. Must be between 0.0 and 1.0. */
    val multiTap: UnitInputPort

    /** Amount of diffusion in the output mix. Must be between 0.0 and 1.0. */
    val diffusion: UnitInputPort

    /** Stereo output. */
    val output: UnitOutputPort

    private val mPlateReverb: PlateReverb
    private val mMultiTapDelay: MultiTapDelay
    private val mRoomReverbMixer: RoomReverbMixer

    companion object {
        private const val SIZE_SCALER_MIN = 0.05
        private const val SIZE_SCALER_MAX = 5.0
        private val kPositions = intArrayOf(
            10, 197, 401,
            521, 733, 1117,
            1481, 2731, 4177,
            6073, 7927, 9463
        )
        // Gains based on attenuation in air after a pre-delay.
        private val kGains = floatArrayOf(
            0.1840f, -0.1543f, -0.1311f,
            0.1205f, -0.1054f, -0.0859f,
            -0.0731f, -0.0484f, 0.0347f,
            0.0254f, 0.0201f, -0.0171f
        )
    }

    init {
        val clippedSize = max(SIZE_SCALER_MIN, min(SIZE_SCALER_MAX, size))

        val positions = IntArray(kPositions.size)
        for (tap in kPositions.indices) {
            positions[tap] = (kPositions[tap] * clippedSize).toInt()
        }
        
        mMultiTapDelay = MultiTapDelay(positions, kGains, (4000 * clippedSize).toInt()) // roughly 80 msec max
        add(mMultiTapDelay)
        
        mPlateReverb = PlateReverb(1.0)
        add(mPlateReverb)
        
        mRoomReverbMixer = RoomReverbMixer()
        add(mRoomReverbMixer)

        mMultiTapDelay.output.connect(mPlateReverb.input)
        mMultiTapDelay.output.connect(mRoomReverbMixer.multiTapInput)
        mPlateReverb.output.connect(0, mRoomReverbMixer.diffusionInput, 0)
        mPlateReverb.output.connect(1, mRoomReverbMixer.diffusionInput, 1)

        // Assign ports
        input = mMultiTapDelay.input
        addPort(input)
        
        preDelayMillis = mMultiTapDelay.preDelayMillis
        addPort(preDelayMillis)
        
        time = mPlateReverb.time
        addPort(time)
        
        damping = mPlateReverb.damping
        addPort(damping)
        
        multiTap = mRoomReverbMixer.multiTapGain
        addPort(multiTap)
        
        diffusion = mRoomReverbMixer.diffusionGain
        addPort(diffusion)
        
        output = mRoomReverbMixer.output
        addPort(output)
    }

    // Custom mixer for room reverb.
    // This is faster than multiple small unit generators.
    internal class RoomReverbMixer : UnitGenerator() {
        val multiTapInput: UnitInputPort
        val diffusionInput: UnitInputPort
        val multiTapGain: UnitInputPort
        val diffusionGain: UnitInputPort
        val output: UnitOutputPort

        init {
            addPort(UnitInputPort("MultiTapInput").also { multiTapInput = it })
            addPort(UnitInputPort(2, "DiffusionInput").also { diffusionInput = it })
            addPort(UnitInputPort("MultiTap").also { multiTapGain = it })
            addPort(UnitInputPort(2, "Diffusion").also { diffusionGain = it })
            
            multiTapGain.setup(0.0, 1.0, 1.0)
            diffusionGain.setup(0.0, 1.0, 1.0)
            
            addPort(UnitOutputPort(2, "Output").also { output = it })
        }

        override fun generate() {
            val multiTapInputs = multiTapInput.getValues()
            val diffusionInputs0 = diffusionInput.getValues(0)
            val diffusionInputs1 = diffusionInput.getValues(1)
            
            // Only capture port variable once since it operates flatly across the buffer block
            val multiTapGainValue = multiTapGain.getValues()[0]
            val diffusionGainValue = diffusionGain.getValues()[0]
            val outputs0 = output.getValues(0)
            val outputs1 = output.getValues(1)

            for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
                val multiTapScaled = multiTapInputs[i] * multiTapGainValue
                outputs0[i] = multiTapScaled + (diffusionInputs0[i] * diffusionGainValue)
                outputs1[i] = multiTapScaled + (diffusionInputs1[i] * diffusionGainValue)
            }
        }
    }
}
