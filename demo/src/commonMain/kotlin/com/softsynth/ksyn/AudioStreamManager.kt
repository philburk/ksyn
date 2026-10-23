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

import com.softsynth.audiobridge.AudioOutputBridge
import com.softsynth.audiobridge.AudioResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs an audio task that writes to the shared [audioBridge].
 *
 * Only one audio task runs at a time, across all managers, because they share one output bridge.
 * A new task waits for the previous task to finish before it runs, so two tasks never render
 * or write at the same time, and the previous task's cleanup cannot undo the new task's setup.
 *
 * Call [start] and [stop] from the main thread.
 */
abstract class AudioStreamManager {

    abstract suspend fun onAudioTask(scope: CoroutineScope)

    public fun isActive(): Boolean {
        return audioStreamJob?.isActive ?: false
    }

    // Keep a reference to the Job of the audio stream
    private var audioStreamJob: Job? = null

    private fun startAudioStreamJob() {
        // Replace whichever task is using the bridge, from this manager or another one.
        val previousJob = currentJob
        previousJob?.cancel()
        audioStreamJob?.cancel()

        val job = GlobalScope.launch(Dispatchers.Default) {
            // Cancelling is cooperative. Wait until the previous task has really stopped,
            // including its cleanup, for example synth.stop().
            previousJob?.join()
            onAudioTask(this)
        }
        audioStreamJob = job
        currentJob = job
        owner = this
    }

    fun start(): AudioResult {
        // Open and start the audio bridge
        // It's important that open() is called before start() and write()
        val openResult = audioBridge.open()
        if (openResult != AudioResult.OK) {
            println("Failed to open audio bridge: $openResult")
            return openResult
        }
        val startResult = audioBridge.start()
        if (startResult != AudioResult.OK) {
            println("Failed to start audio bridge: $startResult")
            audioBridge.close() // Clean up if start fails
            return startResult
        }
        println("AudioBridge opened and started.")
        startAudioStreamJob()
        return AudioResult.OK
    }

    fun stop() {
        val job = audioStreamJob ?: return
        audioStreamJob = null
        job.cancel()
        println("Requested to stop audio stream job.")
        // Another manager may have started since, for example the next screen.
        // Then the bridge is theirs, so leave it running.
        if (owner === this) {
            owner = null
            // Leave currentJob set, so that the next start() waits for this task to finish.
            audioBridge.stop()
            audioBridge.close()
            println("AudioBridge stopped and closed.")
        }
    }

    companion object {
        val audioBridge = AudioOutputBridge.create()

        /** The task that is using [audioBridge], and the manager that started it. */
        private var currentJob: Job? = null
        private var owner: AudioStreamManager? = null
    }
}
