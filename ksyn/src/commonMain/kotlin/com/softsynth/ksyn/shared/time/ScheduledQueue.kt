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

/**
 * Store objects in time sorted order.
 *
 * This is a multiplatform replacement for a `TreeMap`-based scheduled queue.
 * It uses a sorted list to maintain order.
 *
 * Note: The original Java class used `synchronized` methods for thread safety.
 * This implementation is not thread-safe. For multiplatform thread safety,
 * consider using a Mutex from `kotlinx.coroutines.sync`.
 */
class ScheduledQueue<T> {
    private val timeNodes = mutableListOf<Pair<TimeStamp, MutableList<T>>>()

    val isEmpty: Boolean
        get() = timeNodes.isEmpty()

    fun add(time: TimeStamp, obj: T) {
        val searchResult = timeNodes.binarySearch { it.first.compareTo(time) }
        if (searchResult >= 0) {
            // Timestamp already exists, add to the list.
            timeNodes[searchResult].second.add(obj)
        } else {
            // Timestamp not found, insert a new entry.
            val insertionPoint = -(searchResult + 1)
            timeNodes.add(insertionPoint, time to mutableListOf(obj))
        }
    }

    fun removeNextList(time: TimeStamp): List<T>? {
        if (timeNodes.isNotEmpty() && timeNodes.first().first <= time) {
            return timeNodes.removeAt(0).second
        }
        return null
    }

    fun removeNext(time: TimeStamp): T? {
        if (timeNodes.isNotEmpty()) {
            val (lowestTime, timeList) = timeNodes.first()
            if (lowestTime <= time) {
                val next = timeList.removeAt(0)
                if (timeList.isEmpty()) {
                    timeNodes.removeAt(0)
                }
                return next
            }
        }
        return null
    }

    fun clear() {
        timeNodes.clear()
    }

    /**
     * @return The time of the next event, or null if the queue is empty.
     */
    val nextTime: TimeStamp?
        get() = timeNodes.firstOrNull()?.first
}
