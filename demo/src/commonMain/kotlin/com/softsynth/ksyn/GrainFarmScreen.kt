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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.softsynth.audiobridge.AudioInputBridge
import com.softsynth.audiobridge.AudioPermissionState
import com.softsynth.audiobridge.AudioResult
import com.softsynth.ksyn.compose.Oscilloscope
import com.softsynth.ksyn.compose.PortFader
import com.softsynth.ksyn.compose.UnitGeneratorFaders
import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.unitgen.DelayGrains
import com.softsynth.ksyn.unitgen.FixedRateMonoReader
import com.softsynth.ksyn.unitgen.GrainFarm
import com.softsynth.ksyn.unitgen.LineIn
import com.softsynth.ksyn.unitgen.LineOut
import com.softsynth.ksyn.unitgen.Multiply
import com.softsynth.ksyn.unitgen.SampleGrains
import com.softsynth.ksyn.unitgen.ScopeProbe
import com.softsynth.ksyn.unitgen.SineGrains
import kotlinx.coroutines.launch

enum class GrainDemoMode(val label: String) {
    SINE("Sine Grains"),
    SAMPLE("Sample Grains"),
    DELAY("Delay Line Grains")
}

class GrainFarmDemoPlayer : KSynPlayable() {
    val synth = KSyn.createSynthesizer()
    val duplexBridge = KSynDuplexAudioBridge(synth)

    val sineGrains = SineGrains()
    val sampleGrains = SampleGrains()
    val delayGrains = DelayGrains()
    val delayInputGain = Multiply()

    val lineIn = LineIn()
    val sampleLooper = FixedRateMonoReader()
    val lineOut = LineOut()
    val probe = ScopeProbe(numChannels = 1)

    var currentMode by mutableStateOf(GrainDemoMode.SAMPLE)
    var useLiveMicForDelay by mutableStateOf(false)

    var sample: FloatSample = createDefaultTestSample()
        private set
    var sampleName by mutableStateOf("Default Harmonic Sweep")

    init {
        synth.add(sineGrains)
        synth.add(sampleGrains)
        synth.add(delayGrains)
        synth.add(delayInputGain)
        synth.add(lineIn)
        synth.add(sampleLooper)
        synth.add(probe)
        synth.add(lineOut)

        delayInputGain.inputB.setup(0.1, 1.0, 20.0)

        sineGrains.allocate(12)
        sampleGrains.setSample(sample)
        sampleGrains.allocate(12)
        sampleGrains.duration.set(0.08)
        sampleGrains.density.set(0.5)
        sampleGrains.position.set(0.0)
        sampleGrains.positionRange.set(0.15)

        // 2 seconds at the 44100 Hz that KSynDuplexAudioBridge runs at.
        delayGrains.allocateDelay(2 * 44100)
        delayGrains.allocate(12)
        delayGrains.duration.set(0.08)
        delayGrains.density.set(0.6)
        delayGrains.delay.set(0.25)
        delayGrains.delayRange.set(0.1)

        queueLoopingSample(sample)
        updateRouting()

        lineOut.start()
        probe.start()
    }

    private fun queueLoopingSample(s: FloatSample) {
        sampleLooper.dataQueue.clear()
        sampleLooper.dataQueue.queueLoop(s)
    }

    fun updateSample(newSample: FloatSample, name: String) {
        val mono = newSample.toMono()
        sample = mono
        sampleName = name
        synth.queueCommand {
            sampleGrains.setSample(mono)
            queueLoopingSample(mono)
        }
    }

    fun updateRouting() {
        synth.queueCommand {
            sineGrains.output.disconnectAll()
            sampleGrains.output.disconnectAll()
            delayGrains.output.disconnectAll()
            delayInputGain.output.disconnectAll()
            lineIn.output.disconnectAll()
            sampleLooper.output.disconnectAll()

            val activeFarm: GrainFarm = when (currentMode) {
                GrainDemoMode.SINE -> sineGrains
                GrainDemoMode.SAMPLE -> sampleGrains
                GrainDemoMode.DELAY -> {
                    if (useLiveMicForDelay) {
                        lineIn.output.connect(0, delayInputGain.inputA, 0)
                    } else {
                        sampleLooper.output.connect(0, delayInputGain.inputA, 0)
                    }
                    delayInputGain.output.connect(0, delayGrains.input, 0)
                    delayGrains
                }
            }

            activeFarm.output.connect(0, lineOut.input, 0)
            activeFarm.output.connect(0, lineOut.input, 1)
            activeFarm.output.connect(probe.input)
        }
    }

    val activeFarm: GrainFarm
        get() = when (currentMode) {
            GrainDemoMode.SINE -> sineGrains
            GrainDemoMode.SAMPLE -> sampleGrains
            GrainDemoMode.DELAY -> delayGrains
        }

    override fun start(): AudioResult {
        return duplexBridge.start()
    }

    override fun stop() {
        duplexBridge.stop()
    }

    fun restartWithInputMode(enableMic: Boolean) {
        duplexBridge.stop()
        duplexBridge.enableInput = enableMic
        duplexBridge.start()
    }

    override fun getUnitGenerator() = activeFarm
    override fun getScopeProbe(): ScopeProbe = probe
}

class GrainFarmScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val player = remember { GrainFarmDemoPlayer() }
        val scope = rememberCoroutineScope()
        val context = getPlatformContext()
        val scrollState = rememberScrollState()

        LaunchedEffect(Unit) {
            val clarinet = loadClarinetSample()
            if (clarinet != null) {
                player.updateSample(clarinet, "Clarinet.wav")
            }
            player.start()
        }

        DisposableEffect(Unit) {
            onDispose {
                player.stop()
            }
        }

        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(12.dp)
                    .verticalScroll(scrollState)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = { navigator.pop() }) {
                        Text("Go Back")
                    }
                    Text(
                        "Granular Synthesis Lab",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Mode selector chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GrainDemoMode.entries.forEach { mode ->
                        FilterChip(
                            selected = player.currentMode == mode,
                            onClick = {
                                player.currentMode = mode
                                player.updateRouting()
                            },
                            label = { Text(mode.label) }
                        )
                    }
                }

                // Sample loader / Mic recorder bar (shown for Sample and Delay modes)
                if (player.currentMode != GrainDemoMode.SINE) {
                    SampleSourceBar(
                        sampleName = player.sampleName,
                        sampleFrames = player.sample.numFrames,
                        sampleRate = player.sample.frameRate,
                        onSampleLoaded = { newSample, name ->
                            player.updateSample(newSample, name)
                        }
                    )
                }

                // Delay line input source toggle (Looping Sample vs Live Microphone)
                if (player.currentMode == GrainDemoMode.DELAY && AudioInputBridge.isSupported()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Switch(
                            checked = player.useLiveMicForDelay,
                            onCheckedChange = { useMic ->
                                scope.launch {
                                    if (useMic) {
                                        var perm = AudioInputBridge.getPermissionState(context)
                                        if (perm != AudioPermissionState.GRANTED) {
                                            perm = AudioInputBridge.requestPermission(context)
                                        }
                                        if (perm != AudioPermissionState.GRANTED) {
                                            return@launch
                                        }
                                    }
                                    player.useLiveMicForDelay = useMic
                                    player.updateRouting()
                                    player.restartWithInputMode(useMic)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (player.useLiveMicForDelay) {
                                "Delay Input: Live Microphone (use headphones!)"
                            } else {
                                "Delay Input: Looping ${player.sampleName}"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (player.currentMode == GrainDemoMode.DELAY) {
                    PortFader(
                        port = player.delayInputGain.inputB,
                        minValue = 0.1f,
                        maxValue = 20.0f,
                        isExponential = true,
                        name = "Input Gain"
                    )
                }

                UnitGeneratorFaders(
                    unitGenerator = player.activeFarm,
                    presetKey = player.currentMode.ordinal
                )

                Oscilloscope(
                    probe = player.probe,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .padding(top = 8.dp)
                )
            }
        }
    }
}
