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
import com.softsynth.ksyn.unitgen.WhiteNoise

private class WhiteNoisePlayer(): KSynPlayable() {
    var ksynAudioBridge: KSynAudioBridge
    val noise = WhiteNoise()
    val lineOut = LineOut()

    init {
        val synth = KSyn.createSynthesizer()
        ksynAudioBridge = KSynAudioBridge(synth)
        synth.add(noise)
        synth.add(lineOut)
        noise.output.connect(0, lineOut.input, 0)
        noise.output.connect(0, lineOut.input, 1)
        noise.amplitude.set(0.1)
        lineOut.start()
    }

    override fun start(): AudioResult {
        return ksynAudioBridge.start()
    }

    override fun stop() {
        ksynAudioBridge.stop()
    }
}


class PlayWhiteNoise() : AutoStartScreen(WhiteNoisePlayer(), title = "WhiteNoise - random values")
