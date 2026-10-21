/*
 * Copyright 2026 Phil Burk, Mobileer Inc
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.softsynth.audiobridge.AudioDeviceManager
import com.softsynth.audiobridge.AudioInputBridge
import com.softsynth.audiobridge.AudioPermissionState
import com.softsynth.audiobridge.AudioResult
import com.softsynth.audiobridge.writeSuspending
import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.util.SampleLoader
import ksyn_project.demo.generated.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/**
 * Normalize a [FloatArray] in-place so its peak absolute value is [targetPeak] (default 0.99f).
 */
fun normalizeAudioInPlace(data: FloatArray, targetPeak: Float = 0.99f) {
    var peak = 0.0f
    for (v in data) {
        val a = abs(v)
        if (a > peak) peak = a
    }
    if (peak > 1.0e-6f) {
        val scale = targetPeak / peak
        for (i in data.indices) {
            data[i] *= scale
        }
    }
}

/**
 * Generates a fallback mono [FloatSample] with a rich sweeping harmonic signal in case
 * resource loading is still in progress or unavailable.
 */
fun createDefaultTestSample(frameRate: Int = 44100, durationSeconds: Double = 2.0): FloatSample {
    val numFrames = (frameRate * durationSeconds).toInt()
    val data = FloatArray(numFrames) { i ->
        val t = i.toDouble() / frameRate
        val f1 = 220.0 + 110.0 * sin(2.0 * PI * 0.5 * t)
        val s1 = sin(2.0 * PI * f1 * t)
        val s2 = 0.5 * sin(2.0 * PI * f1 * 2.0 * t)
        val s3 = 0.25 * sin(2.0 * PI * f1 * 3.0 * t)
        ((s1 + s2 + s3) * 0.5).toFloat()
    }
    return FloatSample(data).apply {
        this.frameRate = frameRate.toDouble()
    }
}

@OptIn(ExperimentalResourceApi::class)
suspend fun loadClarinetSample(): FloatSample? {
    return try {
        val bytes = Res.readBytes("files/Clarinet.wav")
        SampleLoader.loadFloatSample(bytes).toMono()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Reusable Compose toolbar for loading `Clarinet.wav` or recording a new [FloatSample]
 * from the microphone using [AudioInputBridge].
 */
@Composable
fun SampleSourceBar(
    sampleName: String,
    sampleFrames: Int,
    sampleRate: Double,
    onSampleLoaded: (FloatSample, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = getPlatformContext()
    val inputSupported = remember { AudioInputBridge.isSupported() }

    var isRecording by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var recordJob by remember { mutableStateOf<Job?>(null) }
    val recordedChunks = remember { mutableListOf<FloatArray>() }
    var recordedFramesCount by remember { mutableStateOf(0) }
    var recordingSampleRate by remember { mutableStateOf(44100) }

    fun finishRecording() {
        val job = recordJob
        recordJob = null
        job?.cancel()
        if (isRecording) {
            isRecording = false
            val totalFrames = recordedFramesCount
            if (totalFrames > 0) {
                val combined = FloatArray(totalFrames)
                var offset = 0
                for (chunk in recordedChunks) {
                    val count = min(chunk.size, totalFrames - offset)
                    if (count > 0) {
                        chunk.copyInto(combined, offset, 0, count)
                        offset += count
                    }
                }
                normalizeAudioInPlace(combined)
                val sample = FloatSample(combined).apply {
                    frameRate = recordingSampleRate.toDouble()
                }
                val secs = round((totalFrames.toDouble() / recordingSampleRate) * 10.0) / 10.0
                onSampleLoaded(sample, "Mic Recording (${secs}s)")
                statusText = ""
            } else {
                statusText = "No audio captured"
            }
            recordedChunks.clear()
            recordedFramesCount = 0
        }
    }

    fun startRecording() {
        scope.launch {
            var state = AudioInputBridge.getPermissionState(context)
            if (state != AudioPermissionState.GRANTED) {
                statusText = "Requesting mic permission..."
                state = AudioInputBridge.requestPermission(context)
                if (state != AudioPermissionState.GRANTED) {
                    statusText = "Mic permission denied"
                    return@launch
                }
            }
            recordedChunks.clear()
            recordedFramesCount = 0
            val sr = AudioDeviceManager.getOptimalSampleRate().let { if (it > 0) it else 44100 }
            recordingSampleRate = sr
            val maxFrames = sr * 10 // up to 10 seconds
            val blockFrames = 256

            val inputBridge = AudioInputBridge.create {
                this.sampleRate = sr
                this.framesPerBuffer = blockFrames
                this.channels = 1
            }

            isRecording = true
            statusText = "Recording..."

            recordJob = scope.launch(Dispatchers.Default) {
                val openRes = inputBridge.open()
                if (openRes != AudioResult.OK) {
                    statusText = "Mic open failed: $openRes"
                    isRecording = false
                    return@launch
                }
                val startRes = inputBridge.start()
                if (startRes != AudioResult.OK) {
                    inputBridge.close()
                    statusText = "Mic start failed: $startRes"
                    isRecording = false
                    return@launch
                }

                val temp = FloatArray(blockFrames)
                try {
                    while (isActive && recordedFramesCount < maxFrames) {
                        val read = inputBridge.read(temp, 0, blockFrames)
                        if (read > 0) {
                            val slice = temp.copyOf(read)
                            recordedChunks.add(slice)
                            recordedFramesCount += read
                        } else {
                            kotlinx.coroutines.delay(5)
                        }
                    }
                } finally {
                    inputBridge.stop()
                    inputBridge.close()
                }
                if (recordedFramesCount >= maxFrames) {
                    scope.launch(Dispatchers.Main) {
                        finishRecording()
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recordJob?.cancel()
        }
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                scope.launch {
                    val s = loadClarinetSample()
                    if (s != null) {
                        onSampleLoaded(s, "Clarinet.wav")
                    }
                }
            },
            enabled = !isRecording
        ) {
            Text("Clarinet.wav")
        }

        Button(
            onClick = {
                scope.launch {
                    try {
                        val picked = pickAudioFileBytes(context)
                        if (picked != null) {
                            val (fileName, bytes) = picked
                            val loaded = SampleLoader.loadFloatSample(bytes).toMono()
                            onSampleLoaded(loaded, fileName)
                            statusText = ""
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        statusText = "Load error: ${e.message ?: "invalid file"}"
                    }
                }
            },
            enabled = !isRecording
        ) {
            Text("Load File...")
        }

        if (inputSupported) {
            Button(
                onClick = {
                    if (isRecording) {
                        finishRecording()
                    } else {
                        startRecording()
                    }
                },
                colors = if (isRecording) {
                    ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(if (isRecording) "Stop Recording" else "Record Mic")
            }
        }

        Spacer(modifier = Modifier.width(4.dp))
        val durationSec = if (sampleRate > 0) round((sampleFrames / sampleRate) * 100.0) / 100.0 else 0.0
        val label = if (statusText.isNotEmpty()) {
            statusText
        } else {
            "$sampleName (${durationSec}s)"
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Full-duplex audio bridge for KSyn that optionally opens an [AudioInputBridge] when
 * [enableInput] is true and feeds mono input to `synth.renderBuffer(inputBuffer)`,
 * falling back to output-only if input is disabled or unavailable.
 */
class KSynDuplexAudioBridge(
    val synth: Synthesizer
) : AudioStreamManager() {
    @kotlin.concurrent.Volatile
    var enableInput: Boolean = false

    override suspend fun onAudioTask(scope: CoroutineScope) {
        val sampleRate = 44100
        val framesPerBuffer = 64
        var inputBridge: AudioInputBridge? = null

        if (enableInput && AudioInputBridge.isSupported()) {
            val candidate = AudioInputBridge.create {
                this.sampleRate = sampleRate
                this.framesPerBuffer = framesPerBuffer
                this.channels = 1
            }
            if (candidate.open() == AudioResult.OK) {
                if (candidate.start() == AudioResult.OK) {
                    inputBridge = candidate
                } else {
                    candidate.close()
                }
            }
        }

        synth.start(sampleRate, if (inputBridge != null) 1 else 0, 2)
        val monoInputBuffer = FloatArray(framesPerBuffer)

        try {
            while (scope.isActive) {
                val activeIn = inputBridge
                val stereoBuffer = if (activeIn != null) {
                    val read = activeIn.read(monoInputBuffer, 0, framesPerBuffer)
                    if (read < framesPerBuffer) {
                        monoInputBuffer.fill(0.0f, maxOf(0, read), framesPerBuffer)
                    }
                    synth.renderBuffer(monoInputBuffer)
                } else {
                    synth.renderBuffer()
                }

                val framesToWrite = stereoBuffer.size / 2
                val framesWritten = audioBridge.writeSuspending(
                    stereoBuffer,
                    0,
                    framesToWrite,
                    timeoutMillis = 1000L
                )
                if (framesWritten < 0) {
                    scope.cancel("AudioBridge write error")
                    break
                }
            }
        } catch (_: CancellationException) {
        } finally {
            inputBridge?.stop()
            inputBridge?.close()
            synth.stop()
        }
    }
}
