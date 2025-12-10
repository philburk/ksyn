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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class KSynAudioBridge(val synth: Synthesizer): AudioStreamManager() {
    val STEREO_CHANNELS = 2

    override suspend fun onAudioTask(scope: CoroutineScope) {
        val framesPerBuffer = 64 // TODO right size?
        val stereoBuffer = FloatArray(framesPerBuffer * STEREO_CHANNELS)

        // Set time to sleep based on the audio burst size.
        val framesPerBurst = audioBridge.getFramesPerBurst()
        val burstMillis = (1000 * framesPerBurst * 0.5 / synth.frameRate).toLong()
        synth.start()
        try {
            while (isActive()) { // Check isActive for cooperative cancellation
                val stereoBufferDouble = synth.renderBuffer()
                for (i in 0 until stereoBuffer.size) {
                    stereoBuffer[i] = stereoBufferDouble[i].toFloat()
                }
                var framesLeft = stereoBuffer.size / STEREO_CHANNELS

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
            synth.stop()
        }
    }

}