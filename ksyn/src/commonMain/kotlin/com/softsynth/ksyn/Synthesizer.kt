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
package com.softsynth.ksyn

import com.softsynth.ksyn.shared.time.ScheduledCommand
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.UnitGenerator

/**
 * A synthesizer used by KSyn to generate audio. The synthesizer executes a network of unit
 * generators to create an audio signal.
 *
 * @author Phil Burk (C) 2010 Mobileer Inc
 */
interface Synthesizer {
    /**
     * Starts a background thread that generates audio using the default frame rate of 44100 and two
     * stereo output channels.
     */
    fun start()

    /**
     * Starts a background thread that generates audio using the specified frame rate and two stereo
     * output channels.
     *
     * @param frameRate in Hertz
     */
    fun start(frameRate: Int)

    /**
     * Starts the synthesizer using specific audio devices.
     *
     *
     * Note that using more than 2 channels will probably require the use of JPortAudio because
     * JavaSound currently does not support more than two channels.
     * JPortAudio is available at
     * [http://www.softsynth.com/jsyn/developers/download.php](http://www.softsynth.com/jsyn/developers/download.php).
     *
     *
     * If you use more than 2 inputs or outputs then you will probably want to use [com.jsyn.unitgen.ChannelIn]
     * or [com.jsyn.unitgen.ChannelOut], which can be associated with any indexed channel.
     *
     * @param frameRate in Hertz
     * @param inputDeviceID obtained from an [AudioDeviceManager] or pass
     * AudioDeviceManager.USE_DEFAULT_DEVICE
     * @param numInputChannels 0 for no input, 1 for mono, 2 for stereo, etcetera
     * @param ouputDeviceID obtained from an AudioDeviceManager or pass
     * AudioDeviceManager.USE_DEFAULT_DEVICE
     * @param numOutputChannels 0 for no output, 1 for mono, 2 for stereo, etcetera
     */
//    fun start(
//        frameRate: Int, inputDeviceID: Int, numInputChannels: Int, ouputDeviceID: Int,
//        numOutputChannels: Int
//    )

    /** @return JSyn version as a string
     */
    val version: String

    /** @return version as an integer that always increases
     */
    val versionCode: Int

    /** Stops the background thread that generates the audio.  */
    fun stop()

    /** @return the frame rate in samples per second
     */
    val frameRate: Int

    /**
     * Add a unit generator to the synthesizer so it can be played. This is required before starting
     * or connecting a unit generator into a network.
     *
     * @param ugen a unit generator to be executed by the synthesizer
     */
    fun add(ugen: UnitGenerator)

    /** Removes a unit generator added using add().  */
    fun remove(ugen: UnitGenerator)

    /** @return the current audio time in seconds
     */
    val currentTime: Double

    /**
     * Start a unit generator at the specified time. This is not needed if a unit generator's output
     * is connected to other units. Typically you only need to start units that have no outputs, for
     * example LineOut or ChannelOut.
     */
    fun startUnit(unit: UnitGenerator, time: Double)

    fun startUnit(unit: UnitGenerator, timeStamp: TimeStamp)

    /**
     * The startUnit and stopUnit methods are mainly for internal use.
     * Please call unit.start() or unit.stop() instead.
     * @param unit
     */
    fun startUnit(unit: UnitGenerator)

    fun stopUnit(unit: UnitGenerator, time: Double)

    fun stopUnit(unit: UnitGenerator, timeStamp: TimeStamp)

    /**
     * The startUnit and stopUnit methods are mainly for internal use.
     * Please call unit.start() or unit.stop() instead.
     * @param unit
     */
    fun stopUnit(unit: UnitGenerator)

    /**
     * Sleep until the specified audio time is reached. In non-real-time mode, this will enable the
     * synthesizer to run.
     */
    @Throws(InterruptedException::class)
    suspend fun sleepUntil(time: Double)

    /**
     * Sleep for the specified audio time duration. In non-real-time mode, this will enable the
     * synthesizer to run.
     */
    @Throws(InterruptedException::class)
    suspend fun sleepFor(duration: Double)

    /** Is JSyn running in real-time mode?  */
    /**
     * If set true then the synthesizer will generate audio in real-time. Set it true for live
     * audio. If false then JSyn will run in non-real-time mode. This can be used to generate audio
     * to be written to a file. The default is true.
     *
     * @param realTime
     */
    var isRealTime: Boolean

    /** Create a TimeStamp using the current audio time.  */
    fun createTimeStamp(): TimeStamp

    /** @return the current CPU usage as a fraction between 0.0 and 1.0
     */
    val usage: Double

    /** @return inverse of frameRate, to avoid expensive divides
     */
    val framePeriod: Double

    /**
     * This count is not reset if you stop and restart.
     *
     * @return number of frames synthesized
     */
    val frameCount: Long

    /** Queue a command to be processed at a specific time in the background audio thread.  */
    fun scheduleCommand(timeStamp: TimeStamp, command: () -> Unit)

    /** Queue a command to be processed at a specific time in the background audio thread.  */
    fun scheduleCommand(time: Double, command: () -> Unit)

    /** Queue a command to be processed as soon as possible in the background audio thread.  */
    fun queueCommand(command: () -> Unit)

    /**
     * Clear all scheduled commands from the queue.
     * Commands will be discarded.
     */
    fun clearCommandQueue()

    /**
     * @return true if the Synthesizer has been started
     */
    val isRunning: Boolean

    /**
     * Add a task that will be run repeatedly on the Audio Thread before it generates every new block of Audio.
     * This task must be very quick and should not perform any blocking operations. If you are not
     * certain that you need an Audio rate task then don't use this.
     *
     * @param task
     */
//    fun addAudioTask(task: Runnable?)
//
//    fun removeAudioTask(task: Runnable?)

    companion object {
        const val FRAMES_PER_BLOCK: Int = 8
    }
}
