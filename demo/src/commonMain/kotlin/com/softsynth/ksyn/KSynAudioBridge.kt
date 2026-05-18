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

import com.mobileer.audiobridge.writeSuspending
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive

class KSynAudioBridge(val synth: Synthesizer): AudioStreamManager() {
    val STEREO_CHANNELS = 2

    override suspend fun onAudioTask(scope: CoroutineScope) {
        synth.start()
        try {
            while (scope.isActive) { // Check isActive for cooperative cancellation
                val stereoBuffer = synth.renderBuffer()
                var framesToWrite = stereoBuffer.size / STEREO_CHANNELS

                // Write the synthesized buffer, waiting up to 1000ms if needed.
                val framesWritten = audioBridge.writeSuspending(
                    stereoBuffer,
                    0,
                    framesToWrite,
                    timeoutMillis = 1000L
                )
                if (framesWritten < 0) {
                    // Handle error from audioBridge.write, e.g., stream closed
                    println("AudioBridge write error: $framesWritten")
                    scope.cancel("AudioBridge write error") // Cancel the coroutine
                    break
                } else if (framesWritten < framesToWrite) {
                    println("AudioBridge write timeout")
                }
            }
        } catch (e: CancellationException) {
            println("Audio stream coroutine cancelled.")
            // Perform any cleanup specific to this coroutine if needed
        } finally {
            println("Audio stream coroutine finishing.")
            synth.stop()
        }
    }

}