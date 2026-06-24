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

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mobileer.audiobridge.AudioResult
import com.softsynth.ksyn.compose.Oscilloscope
import com.softsynth.ksyn.instruments.WaveShapingVoice
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.LineOut
import com.softsynth.ksyn.unitgen.PassThrough
import com.softsynth.ksyn.unitgen.RoomReverb
import com.softsynth.ksyn.unitgen.ScopeProbe
import com.softsynth.ksyn.unitgen.UnitVoice
import com.softsynth.ksyn.util.PseudoRandom
import com.softsynth.ksyn.util.VoiceAllocator
import com.softsynth.math.AudioMath
import kotlinx.coroutines.*

private class ChebyshevSongPlayer : KSynPlayable() {
    var ksynAudioBridge: KSynAudioBridge
    val synth = KSyn.createSynthesizer()
    val mixer = PassThrough()
    val reverb = RoomReverb(1.0)
    val lineOut = LineOut()
    val scope = ScopeProbe(numChannels = 2, displayBufferSize = 512)

    val maxVoices = 8
    val maxNotes = 5
    val allocator: VoiceAllocator
    val pseudo = PseudoRandom()

    val scale = intArrayOf(0, 2, 4, 7, 9) // pentatonic scale

    init {
        ksynAudioBridge = KSynAudioBridge(synth)

        synth.add(mixer)
        synth.add(lineOut)
        synth.add(reverb)
        synth.add(scope)

        mixer.output.connect(reverb.input)
        mixer.output.connect(0, lineOut.input, 0)  // dry
        reverb.output.connect(0, lineOut.input, 1) // wet
        mixer.output.connect(0, scope.input, 0)    // dry → scope ch0
        reverb.output.connect(0, scope.input, 1)   // wet → scope ch1

        val voices: Array<UnitVoice> = Array(maxVoices) { WaveShapingVoice() }.map { it as UnitVoice }.toTypedArray()
        for (i in 0 until maxVoices) {
            val voice = voices[i] as WaveShapingVoice
            synth.add(voice)
            voice.usePreset(0)
            voice.getOutputPort().connect(mixer.input)
        }
        allocator = VoiceAllocator(voices)

        lineOut.start()
        scope.start()
    }

    override fun getScopeProbe() = scope

    override fun start(): AudioResult {
        return ksynAudioBridge.start()
    }

    override fun stop() {
        ksynAudioBridge.stop()
    }

    fun indexToFrequency(index: Int): Double {
        val octave = index / scale.size
        val temp = index % scale.size
        val pitch = scale[temp] + (12 * octave)
        return AudioMath.pitchToFrequency(pitch + 16.0)
    }

    fun noteOff(time: Double, noteNumber: Int) {
        allocator.noteOff(noteNumber, TimeStamp(time))
    }

    fun noteOn(time: Double, noteNumber: Int) {
        val frequency = indexToFrequency(noteNumber)
        val amplitude = 0.1
        val timeStamp = TimeStamp(time)
        allocator.noteOn(noteNumber, frequency, amplitude, timeStamp)
        allocator.setPort(noteNumber, "Range", 0.7, synth.createTimeStamp())
    }

    suspend fun playSongCoroutine() {
        var savedSeed = kotlin.random.Random.nextInt()
        val duration = 0.2
        val advanceTime = 0.5
        var nextTime = synth.currentTime + advanceTime
        val onTime = duration / 2
        var beatIndex = 0

        while (currentCoroutineContext().isActive) {
            // on every measure, maybe repeat previous pattern
            if ((beatIndex and 7) == 0) {
                if (kotlin.random.Random.nextDouble() < 0.5) {
                    pseudo.setSeed(savedSeed)
                } else if (kotlin.random.Random.nextDouble() < 0.5) {
                    savedSeed = pseudo.getSeed()
                }
            }

            // Play a bunch of random notes in the scale.
            val numNotes = pseudo.choose(maxNotes)
            for (i in 0 until numNotes) {
                val noteNumber = pseudo.choose(30)
                noteOn(nextTime, noteNumber)
                noteOff(nextTime + onTime, noteNumber)
            }

            nextTime += duration
            beatIndex += 1

            // wake up before we need to play note to cover system latency
            try {
                synth.sleepUntil(nextTime - advanceTime)
            } catch (e: Exception) {
                break
            }
        }
    }
}

class ChebyshevSongScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val player = remember { ChebyshevSongPlayer() }

        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Chebyshev Song", style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Generative Pentatonic Melody using WaveShapingVoice")
                Spacer(modifier = Modifier.height(16.dp))
                Oscilloscope(
                    probe = player.scope,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { navigator.pop() }) {
                    Text("Go Back")
                }
            }
        }

        DisposableEffect(Unit) {
            val scope = CoroutineScope(Dispatchers.Default)
            val result = player.start()
            if (result == AudioResult.OK) {
                scope.launch {
                    player.playSongCoroutine()
                }
            } else {
                println("Failed to open audio bridge: $result")
            }
            onDispose {
                scope.cancel()
                player.stop()
            }
        }
    }
}
