/*
 * Copyright 2024 Phil Burk, Mobileer Inc
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
import com.softsynth.ksyn.unitgen.GrainFarm
import com.softsynth.ksyn.unitgen.LineOut
import com.softsynth.ksyn.unitgen.ScopeProbe
import com.softsynth.ksyn.unitgen.UnitGenerator

private class GrainFarmDemoPlayer() : KSynPlayable() {
    var ksynAudioBridge: KSynAudioBridge
    val grainFarm = GrainFarm()
    val lineOut = LineOut()
    val probe = ScopeProbe(numChannels = 1)

    init {
        val synth = KSyn.createSynthesizer()
        ksynAudioBridge = KSynAudioBridge(synth)
        synth.add(grainFarm)
        synth.add(probe)
        synth.add(lineOut)

        grainFarm.allocate(8)
        
        grainFarm.output.connect(0, lineOut.input, 0)
        grainFarm.output.connect(0, lineOut.input, 1)
        grainFarm.output.connect(probe.input)

        lineOut.start()
        probe.start()
    }

    override fun start(): AudioResult {
        return ksynAudioBridge.start()
    }

    override fun stop() {
        ksynAudioBridge.stop()
    }

    override fun getUnitGenerator(): UnitGenerator {
        return grainFarm
    }
    override fun getScopeProbe(): ScopeProbe = probe
}

class GrainFarmScreen() : AutoStartScreen(GrainFarmDemoPlayer(),
    title = "GrainFarm - granular synthesis using small bits of sine waves")
