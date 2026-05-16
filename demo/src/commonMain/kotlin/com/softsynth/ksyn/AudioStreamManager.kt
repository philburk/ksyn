/*
 * Copyright 2025 Phil Burk
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

import com.mobileer.audiobridge.AudioOutputBridge
import com.mobileer.audiobridge.AudioResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class AudioStreamManager {

    abstract suspend fun onAudioTask(scope: CoroutineScope)

    public fun isActive(): Boolean {
        return audioStreamJob?.isActive ?: false
    }

    // Keep a reference to the Job of the audio stream
    private var audioStreamJob: Job? = null

    private fun startAudioStreamJob(): Job { // Return the Job
        // Cancel any existing job before starting a new one
        audioStreamJob?.cancel() // This ensures only one stream runs if called multiple times

        val job = GlobalScope.launch(Dispatchers.Default) {
            onAudioTask(this)
        }
        audioStreamJob = job // Store the new job
        return job
    }

    // Optional: Add a function to explicitly stop the stream
    private fun stopAudioStreamJob() {
        audioStreamJob?.cancel()
        audioStreamJob = null
        println("Requested to stop audio stream job.")
    }

    fun start(): AudioResult {
        // Open and start the audio bridge
        // It's important that open() is called before start() and write()
        val openResult = audioBridge.open()
        if (openResult != AudioResult.OK) {
            println("Failed to open audio bridge: $openResult")
            // Handle error, maybe show a message to the user
            return openResult
        }
        val startResult = audioBridge.start()
        if (openResult != AudioResult.OK) {
            println("Failed to start audio bridge: $startResult")
            audioBridge.close() // Clean up if start fails
            return openResult
        }
        println("AudioBridge opened and started.")
        // Start the audio stream job
        startAudioStreamJob()
        println("Continuous tone started.")
        return AudioResult.OK
    }

    fun stop() {
        stopAudioStreamJob()
        // Stop and close the audio bridge
        audioBridge.stop()
        audioBridge.close()
        println("AudioBridge stopped and closed.")
    }

    companion object {
        val audioBridge = AudioOutputBridge.create()
    }
}
