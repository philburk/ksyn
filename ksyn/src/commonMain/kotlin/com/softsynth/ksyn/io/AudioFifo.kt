/*
 * Copyright 2010 Phil Burk, Mobileer Inc
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

package com.softsynth.ksyn.io

import com.softsynth.ksyn.AudioBuffer
import com.softsynth.ksyn.AudioSample
import kotlin.concurrent.Volatile

/**
 * FIFO that implements AudioInputStream, AudioOutputStream interfaces. This can be used to send
 * audio data between different threads. The reads or writes may or may not wait based on flags.
 *
 * In Kotlin Multiplatform (especially on WASM), thread-blocking primitives like ReentrantLock's
 * Condition.await() are completely unsupported. Therefore, when wait is enabled, this implementation
 * falls back to a non-suspending spin-loop which checks underlying naturally lock-free @Volatile 
 * array pointers (readIndex/writeIndex) to safely synchronize single-producer single-consumer flow
 * across platforms.
 *
 * @author Phil Burk (C) 2010 Mobileer Inc
 */
class AudioFifo : AudioInputStream, AudioOutputStream {
    // These indices run double the FIFO size so that we can tell empty from full.
    @Volatile
    private var readIndex = 0
    @Volatile
    private var writeIndex = 0
    private var buffer: AudioBuffer = AudioBuffer(0)
    
    // Used to mask the index into range when accessing the buffer array.
    private var accessMask: Int = 0
    // Used to mask the index so it wraps around.
    private var sizeMask: Int = 0
    
    var isWriteWaitEnabled: Boolean = true
    var isReadWaitEnabled: Boolean = true
    
    @Volatile
    private var mOpen: Boolean = true

    /**
     * @param size Number of doubles/samples in the FIFO. Must be a power of 2. Eg. 1024.
     */
    fun allocate(size: Int) {
        if (!isPowerOfTwo(size)) {
            throw IllegalArgumentException("Size must be a power of two.")
        }
        buffer = AudioBuffer(size)
        accessMask = size - 1
        sizeMask = (size * 2) - 1
    }

    fun size(): Int {
        return buffer.size
    }

    companion object {
        fun isPowerOfTwo(size: Int): Boolean {
            return (size and (size - 1)) == 0 && size > 0
        }
    }

    /** How many samples are available for reading without blocking? */
    override fun available(): Int {
        return (writeIndex - readIndex) and sizeMask
    }

    override fun close() {
        // Break any waiting spin loops.
        mOpen = false
    }

    override fun read(): AudioSample {
        var value: AudioSample = Float.NaN
        if (isReadWaitEnabled) {
            // Spin loop instead of Condition.await() for multiplatform compatibility
            while (mOpen && available() < 1) {
                // Wait for the writer to push data
            }
            if (mOpen) {
                value = readOneInternal()
            }
        } else {
            if (mOpen && readIndex != writeIndex) {
                value = readOneInternal()
            }
        }

        // JSyn used to call `notFull.signal()` here if `writeWaitEnabled == true`
        // In this implementation, the writer's spin-loop catches up gracefully.
        return value
    }

    private fun readOneInternal(): AudioSample {
        val value = buffer[readIndex and accessMask]
        readIndex = (readIndex + 1) and sizeMask
        return value
    }

    override fun write(value: AudioSample) {
        if (isWriteWaitEnabled) {
            // Spin loop instead of Condition.await() for multiplatform compatibility
            while (mOpen && available() == buffer.size) {
                // Wait for the reader to pull data
            }
            if (mOpen) {
                writeOneInternal(value)
            }
        } else {
            if (available() != buffer.size) {
                writeOneInternal(value)
            }
        }

        // JSyn used to call `notEmpty.signal()` here.
        // In KMP, reader unblocks immediately as `writeIndex` propagates downwards via volatile cache synchronisation.
    }

    private fun writeOneInternal(value: AudioSample) {
        buffer[writeIndex and accessMask] = value
        writeIndex = (writeIndex + 1) and sizeMask
    }

    override fun read(buffer: AudioBuffer): Int {
        return read(buffer, 0, buffer.size)
    }

    override fun read(buffer: AudioBuffer, start: Int, count: Int): Int {
        if (!mOpen) {
            return 0
        }
        var actualCount = count
        if (!isReadWaitEnabled) {
            actualCount = minOf(available(), count)
        }
        var numRead = 0
        for (i in 0 until actualCount) {
            if (!mOpen) break
            val value = read()
            if (value.isNaN()) break
            buffer[i + start] = value
            numRead++
        }
        return numRead
    }

    override fun write(buffer: AudioBuffer) {
        write(buffer, 0, buffer.size)
    }

    override fun write(buffer: AudioBuffer, start: Int, count: Int) {
        for (i in 0 until count) {
            write(buffer[i + start])
        }
    }
}
