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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mobileer.audiobridge.AudioResult
import com.softsynth.ksyn.unitgen.GrainFarm
import com.softsynth.ksyn.unitgen.LineOut

private class GrainFarmDemoPlayer() : KSynPlayable {
    var ksynAudioBridge: KSynAudioBridge
    val grainFarm = GrainFarm()
    val lineOut = LineOut()

    init {
        val synth = KSyn.createSynthesizer()
        ksynAudioBridge = KSynAudioBridge(synth)
        synth.add(grainFarm)
        synth.add(lineOut)

        grainFarm.allocate(8)
        
        grainFarm.output.connect(0, lineOut.input, 0)
        grainFarm.output.connect(0, lineOut.input, 1)

        lineOut.start()
    }

    override fun start(): AudioResult {
        return ksynAudioBridge.start()
    }

    override fun stop() {
        ksynAudioBridge.stop()
    }
}

class GrainFarmScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val player = remember { GrainFarmDemoPlayer() }

        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Button(onClick = { navigator.pop() }) {
                    Text("Go Back")
                }
                UnitGeneratorFaders(unitGenerator = player.grainFarm)
            }
        }

        DisposableEffect(Unit) {
            val result = player.start()
            if (result != AudioResult.OK) {
                println("Failed to open audio bridge: $result")
            }
            onDispose {
                player.stop()
            }
        }
    }
}
