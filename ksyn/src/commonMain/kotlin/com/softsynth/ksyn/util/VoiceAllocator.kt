/*
 * Copyright 2011 Phil Burk, Mobileer Inc
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

package com.softsynth.ksyn.util

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.UnitVoice
import com.softsynth.ksyn.unitgen.UnitGenerator

/**
 * Allocate voices based on an integer tag. The tag could, for example, be a MIDI note number. Or a
 * tag could be an int that always increments. Use the same tag to refer to a voice for noteOn() and
 * noteOff(). If no new voices are available then a voice in use will be stolen.
 *
 * @author Phil Burk (C) 2011 Mobileer Inc
 */
open class VoiceAllocator(val voices: Array<UnitVoice>) : Instrument {
    val voiceCount: Int = voices.size
    private val trackers: Array<VoiceTracker> = Array(voices.size) { i ->
        VoiceTracker().apply { voice = voices[i] }
    }
    private var tick: Long = 0
    
    companion object {
        private const val UNASSIGNED_PRESET = -1
    }
    
    private var _presetIndex = UNASSIGNED_PRESET

    lateinit var synthesizer: Synthesizer
        private set

    init {
        val firstVoice = voices.firstOrNull()
        if (firstVoice != null) {
            val engine = firstVoice.getUnitGenerator().synthesisEngine
            if (engine is Synthesizer) {
                synthesizer = engine
            } else {
                throw IllegalStateException("SynthesisEngine is not a Synthesizer.")
            }
        }
    }

    private inner class VoiceTracker {
        var voice: UnitVoice? = null
        var tag: Int = -1
        var presetIndex: Int = UNASSIGNED_PRESET
        var whenTick: Long = 0
        var on: Boolean = false

        fun off() {
            on = false
            whenTick = tick++
        }
    }

    private fun findVoice(tag: Int): VoiceTracker? {
        for (tracker in trackers) {
            if (tracker.tag == tag) {
                return tracker
            }
        }
        return null
    }

    private fun stealVoice(): VoiceTracker {
        var bestOff: VoiceTracker? = null
        var bestOn: VoiceTracker? = null
        for (tracker in trackers) {
            if (tracker.voice == null) {
                return tracker
            }
            // If we have a bestOff voice then don't even bother with on voices.
            if (bestOff != null) {
                // Older off voice?
                if (!tracker.on && tracker.whenTick < bestOff.whenTick) {
                    bestOff = tracker
                }
            } else if (tracker.on) {
                if (bestOn == null) {
                    bestOn = tracker
                } else if (tracker.whenTick < bestOn.whenTick) {
                    bestOn = tracker
                }
            } else {
                bestOff = tracker
            }
        }
        return bestOff ?: bestOn!!
    }

    /**
     * Allocate a Voice associated with this tag. It will first pick a voice already assigned to
     * that tag. Next it will pick the oldest voice that is off. Next it will pick the oldest voice
     * that is on. If you are using timestamps to play the voice in the future then you should use
     * the noteOn() noteOff() and setPort() methods.
     *
     * @param tag
     * @return Voice that is most available.
     */
    fun allocate(tag: Int): UnitVoice? {
        val tracker = allocateTracker(tag)
        return tracker.voice
    }

    private fun allocateTracker(tag: Int): VoiceTracker {
        var tracker = findVoice(tag)
        if (tracker == null) {
            tracker = stealVoice()
        }
        tracker.tag = tag
        tracker.whenTick = tick++
        tracker.on = true
        return tracker
    }

    fun isOn(tag: Int): Boolean {
        return findVoice(tag)?.on ?: false
    }

    fun off(tag: Int): UnitVoice? {
        val tracker = findVoice(tag)
        if (tracker != null) {
            tracker.off()
            return tracker.voice
        }
        return null
    }

    /** Turn off all the note currently on. */
    override fun allNotesOff(timeStamp: TimeStamp?) {
        synthesizer.queueCommand {
            for (tracker in trackers) {
                if (tracker.on) {
                    val ts = timeStamp ?: synthesizer.createTimeStamp()
                    tracker.voice?.noteOff(ts)
                    tracker.off()
                }
            }
        }
    }

    /**
     * Play a note on the voice and associate it with the given tag. if needed a new voice will be
     * allocated and an old voice may be turned off.
     */
    override fun noteOn(tag: Int, frequency: Double, amplitude: Double, timeStamp: TimeStamp?) {
        synthesizer.queueCommand {
            val voiceTracker = allocateTracker(tag)
            if (voiceTracker.presetIndex != _presetIndex) {
                voiceTracker.voice?.usePreset(_presetIndex)
                voiceTracker.presetIndex = _presetIndex
            }
            val ts = timeStamp ?: synthesizer.createTimeStamp()
            voiceTracker.voice?.noteOn(frequency, amplitude, ts)
        }
    }

    /**
     * Play a note on the voice and associate it with the given tag. if needed a new voice will be
     * allocated and an old voice may be turned off.
     * Apply an operation to the voice.
     */
    fun noteOn(tag: Int, frequency: Double, amplitude: Double, operation: VoiceOperation, timeStamp: TimeStamp? = null) {
        synthesizer.queueCommand {
            val voiceTracker = allocateTracker(tag)
            voiceTracker.voice?.let { operation.operate(it) }
            val ts = timeStamp ?: synthesizer.createTimeStamp()
            voiceTracker.voice?.noteOn(frequency, amplitude, ts)
        }
    }

    /** Turn off the voice associated with the given tag if allocated. */
    override fun noteOff(tag: Int, timeStamp: TimeStamp?) {
        synthesizer.queueCommand {
            val voiceTracker = findVoice(tag)
            if (voiceTracker != null) {
                val ts = timeStamp ?: synthesizer.createTimeStamp()
                voiceTracker.voice?.noteOff(ts)
                off(tag)
            }
        }
    }

    /** Set a port on the voice associated with the given tag if allocated. */
    override fun setPort(tag: Int, portName: String, value: Double, timeStamp: TimeStamp?) {
        synthesizer.queueCommand {
            val voiceTracker = findVoice(tag)
            if (voiceTracker != null) {
                val ts = timeStamp ?: synthesizer.createTimeStamp()
                voiceTracker.voice?.setPort(portName, value.toFloat(), ts)
            }
        }
    }

    override fun usePreset(presetIndex: Int, timeStamp: TimeStamp?) {
        synthesizer.queueCommand {
            _presetIndex = presetIndex
        }
    }
}
