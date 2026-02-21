/*
 * Copyright 2025 Phil Burk, Mobileer
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

package com.softsynth.ksyn

import com.mobileer.audiobridge.AudioResult
import com.softsynth.ksyn.unitgen.LineOut
import com.softsynth.ksyn.unitgen.SawtoothOscillator

private class SawtoothPlayer(private val frequency: Float): KSynPlayable {
    var ksynAudioBridge: KSynAudioBridge
    val sawtooth = SawtoothOscillator()
    val lineOut = LineOut()

    init {
        val synth = KSyn.createSynthesizer()
        ksynAudioBridge = KSynAudioBridge(synth)
        synth.add(sawtooth)
        synth.add(lineOut)
        sawtooth.output.connect(0, lineOut.input, 0)
        sawtooth.output.connect(0, lineOut.input, 1)
        sawtooth.frequency.set(frequency.toDouble())
        sawtooth.amplitude.set(0.1)
        lineOut.start()
    }

    override fun start(): AudioResult {
        return ksynAudioBridge.start()
    }

    override fun stop() {
        ksynAudioBridge.stop()
    }
}


class PlaySawtooth private constructor(private val player: SawtoothPlayer) : StartStopScreen(
    playable = player,
    customContent = {
        PortFader(
            port = player.sawtooth.frequency,
            minValue = 100.0f,
            maxValue = 8000.0f,
            isExponential = true
        )
    }
) {
    constructor() : this(SawtoothPlayer(440.0f))
}
