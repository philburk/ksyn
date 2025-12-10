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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mobileer.audiobridge.AudioResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.collections.minusAssign
import kotlin.collections.plusAssign
import kotlin.compareTo
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin


class SineWaveGenerator(private var frequency: Float,
                        private val amplitude: Float = 1.0f) {

    private var phase = 0.0 // Current phase, maintained between calls
    private var currentSampleRate = 44100
    private var phaseIncrement = 2 * PI * frequency / currentSampleRate

    fun generateBuffer(buffer: FloatArray, numFrames: Int) {
        for (i in 0 until numFrames) {
            val sampleValue = amplitude * sin(phase).toFloat()
            buffer[i] = sampleValue
            phase += phaseIncrement
            // Wrap phase to keep it within a manageable range
            if (phase >= 2 * PI) {
                phase -= 2 * PI
            }
        }
    }

    fun setFrequency(newFrequency: Float) {
        frequency = newFrequency
        updatePhaseIncrement()
    }

    fun setSampleRate(newSampleRate: Int) {
        currentSampleRate = newSampleRate
        updatePhaseIncrement()
    }

    private fun updatePhaseIncrement() {
        phaseIncrement = 2 * PI * frequency / currentSampleRate
    }
}

private class SineWavePlayer(private val frequency: Float): AudioStreamManager() {

    val MAX_FRAMES_PER_BUFFER = 256
    val STEREO_CHANNELS = 2
    val BASE_FREQUENCY = 440.0 // Concert A for the first sine tone

    override suspend fun onAudioTask(scope: CoroutineScope) {
        val leftSine = SineWaveGenerator(BASE_FREQUENCY.toFloat())
        val rightSine = SineWaveGenerator((BASE_FREQUENCY * 5.0 / 4.0).toFloat())

        val framesPerBurst = audioBridge.getFramesPerBurst()
        println("AudioBridge framesPerBurst: $framesPerBurst")
        // Don't make the buffer too large because the note timing will be too grainy.
        val bufferSizeFrames = min(framesPerBurst, MAX_FRAMES_PER_BUFFER)
        val leftBuffer = FloatArray(bufferSizeFrames)
        val rightBuffer = FloatArray(bufferSizeFrames)
        val stereoBuffer = FloatArray(bufferSizeFrames * STEREO_CHANNELS)

        val sampleRate = audioBridge.getSampleRate()
        println("AudioBridge sample rate: $sampleRate")
        leftSine.setSampleRate(sampleRate)
        rightSine.setSampleRate(sampleRate)

        // Set time to sleep based on the audio burst size.
        val burstMillis = 1000L * bufferSizeFrames / sampleRate

        try {
            while (isActive()) { // Check isActive for cooperative cancellation
                leftSine.generateBuffer(leftBuffer, bufferSizeFrames)
                rightSine.generateBuffer(rightBuffer, bufferSizeFrames)

                // Interleave left and right buffers into stereoBuffer
                for (i in 0 until bufferSizeFrames) {
                    stereoBuffer[i * 2] = leftBuffer[i]      // Left channel
                    stereoBuffer[i * 2 + 1] = rightBuffer[i] // Right channel
                }

                var framesLeft = bufferSizeFrames
                var offset = 0
                while (framesLeft > 0 && isActive()) {
                    val frameCount = audioBridge.write(stereoBuffer, offset, framesLeft)
                    if (frameCount < 0) {
                        // Handle error from audioBridge.write, e.g., stream closed
                        println("AudioBridge write error: $frameCount")
                        scope.cancel("AudioBridge write error") // Cancel the coroutine
                        break
                    }
                    offset += frameCount
                    framesLeft -= frameCount
                    if (framesLeft > 0 && scope.isActive) {
                        // Wait long enough for one burst of room to be available.
                        delay(burstMillis)
                    }
                }
            }
        } catch (e: CancellationException) {
            println("Audio stream coroutine cancelled.")
            // Perform any cleanup specific to this coroutine if needed
        } finally {
            println("Audio stream coroutine finishing.")
            // Ensure resources are released if this coroutine was solely responsible
            // However, audioBridge.stop/close is handled by the button in App
        }
    }

}

private val sineWavePlayer = SineWavePlayer(440.0f)


// 2. The Destination Screen
class TestAudioBridge : Screen {

    @Composable
    override fun Content() {
        var isPlaying by remember { mutableStateOf(false) }
        val navigator = LocalNavigator.currentOrThrow
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Button(
                    onClick = {
                        // Pop this screen off the stack (Go Back)
                        navigator.pop()
                    }
                ) {
                    Text("Audio Bridge Active (Go Back)")
                }

                Text("Test audio on ${getPlatform().name}")

                Row {
                    Button(
                        onClick = {
                            val result = sineWavePlayer.start()
                            if (result != AudioResult.OK) {
                                println("Failed to open audio bridge: $result")
                                // Handle error, maybe show a message to the user
                                return@Button
                            }
                            isPlaying = true
                        },
                        enabled = !isPlaying
                    ) {
                        Text("START")
                    }

                    Button(
                        onClick = {
                            sineWavePlayer.stop()
                            isPlaying = false
                        },
                        enabled = isPlaying
                    ) {
                        Text("STOP")
                    }
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    // Cleanup when the Composable leaves the composition
                    sineWavePlayer.stop()
                }
            }
        }
    }
}

