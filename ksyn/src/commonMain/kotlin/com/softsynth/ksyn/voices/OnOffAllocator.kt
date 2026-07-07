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

package com.softsynth.ksyn.voices

/**
 * Allocate voices based on an integer tag. The tag could, for example, be a MIDI note number. Or a
 * tag could be an int that always increments. Use the same tag to refer to a voice for noteOn() and
 * noteOff(). If no new voices are available then a voice in use will be stolen.
 *
 * This class is intended to be used only in the synthesizer coroutine scope.
 *
 * Also, it does not modify the voices. It only keeps track of them and allocates them.
 *
 * @author Phil Burk (C) 2011 Mobileer Inc
 */
open class OnOffAllocator<T>(val things: Array<T>) {
    private val trackers: List<Tracker<T>> = things.map { voice ->
        Tracker(voice)
    }

    // internal monotonic counter used to keep track of the ordering of on/off
    private var tick: Long = 0

    private inner class Tracker<T>(val thing: T) {
        var tag: Int = -1
        var whenTick: Long = 0
        var on: Boolean = false

        fun off() {
            on = false
            whenTick = tick++
        }
    }

    private fun findVoiceTracker(tag: Int): Tracker<T>? {
        for (tracker in trackers) {
            if (tracker.tag == tag) {
                return tracker
            }
        }
        return null
    }

    private fun stealVoice(): Tracker<T> {
        var bestOff: Tracker<T>? = null
        var bestOn: Tracker<T>? = null
        for (tracker in trackers) {
            // If we have a bestOff thing then don't even bother with on things.
            if (bestOff != null) {
                // Older off thing?
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
     * Allocate a thing associated with this tag. It will first pick a thing already assigned to
     * that tag. Next it will pick the oldest thing that is off. Next it will pick the oldest thing
     * that is on. If you are using timestamps to play the thing in the future then you should use
     * the noteOn() noteOff() and setPort() methods.
     *
     * @param tag
     * @return Voice that is most available.
     */
    fun allocate(tag: Int): T? {
        val tracker = allocateTracker(tag)
        return tracker.thing
    }

    private fun allocateTracker(tag: Int): Tracker<T> {
        var tracker = findVoiceTracker(tag)
        if (tracker == null) {
            tracker = stealVoice()
        }
        tracker.tag = tag
        tracker.whenTick = tick++
        tracker.on = true
        return tracker
    }

    // TODO do we need this?
    fun isOn(tag: Int): Boolean {
        return findVoiceTracker(tag)?.on ?: false
    }

    fun on(tag: Int): T {
        val tracker = allocateTracker(tag)
        return tracker.thing
    }

    fun off(tag: Int): T? {
        val tracker = findVoiceTracker(tag)
        if (tracker != null) {
            tracker.off()
            return tracker.thing
        }
        return null
    }

    fun findVoice(tag: Int): T? {
        val tracker = findVoiceTracker(tag)
        if (tracker != null) {
            return tracker.thing
        }
        return null
    }

    /** Mark all of the voices as off. */
    fun allOff() {
        for (tracker in trackers) {
            if (tracker.on) {
                tracker.off()
            }
        }
    }

}
