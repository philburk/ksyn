/*
 * Copyright 2009 Phil Burk, Mobileer Inc
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

package com.softsynth.ksyn.shared.time

import kotlinx.coroutines.sync.Mutex

/**
 * Store objects in time sorted order.
 *
 * This is a multiplatform replacement for a `TreeMap`-based scheduled queue.
 * It uses a sorted list to maintain order.
 *
 * This implementation is now thread-safe via a lightweight spin-lock over a coroutine Mutex
 * which satisfies the concurrent constraints across Kotlin Common platforms (specifically
 * protecting the Desktop UI Thread from colliding with the Desktop Audio Synthesis Thread).
 */
class ScheduledQueue<T> {
    private val timeNodes = mutableListOf<Pair<TimeStamp, MutableList<T>>>()
    private val lock = Mutex()

    private inline fun <R> withSpinLock(block: () -> R): R {
        // Simple non-suspending spin lock for brief list modifications
        while (!lock.tryLock()) { }
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    val isEmpty: Boolean
        get() = withSpinLock { timeNodes.isEmpty() }

    /**
     * Add object in time sorted order.
     * This is an "insertion sort".
     */
    fun add(time: TimeStamp, obj: T) {
        withSpinLock {
            val index = timeNodes.indexOfFirst { it.first == time }
            if (index >= 0) {
                timeNodes[index].second.add(obj)
            } else {
                val insertIndex = timeNodes.indexOfFirst { it.first > time }
                if (insertIndex >= 0) {
                    timeNodes.add(insertIndex, time to mutableListOf(obj))
                } else {
                    timeNodes.add(time to mutableListOf(obj))
                }
            }
        }
    }

    /** Remove the earliest list of T objects that are ready to be processed. */
    fun removeNextList(time: TimeStamp): List<T>? {
        return withSpinLock {
            if (timeNodes.isNotEmpty() && timeNodes.first().first <= time) {
                timeNodes.removeAt(0).second
            } else {
                null
            }
        }
    }

    /** Remove the earliest T object that is ready to be processed. */
    fun removeNext(time: TimeStamp): T? {
        return withSpinLock {
            if (timeNodes.isNotEmpty()) {
                val (lowestTime, timeList) = timeNodes.first()
                if (lowestTime <= time) {
                    val next = timeList.removeAt(0)
                    if (timeList.isEmpty()) {
                        timeNodes.removeAt(0)
                    }
                    return@withSpinLock next
                }
            }
            null
        }
    }

    fun clear() {
        withSpinLock { timeNodes.clear() }
    }

    /**
     * @return The time of the next event, or null if the queue is empty.
     */
    val nextTime: TimeStamp?
        get() = withSpinLock { timeNodes.firstOrNull()?.first }
}
