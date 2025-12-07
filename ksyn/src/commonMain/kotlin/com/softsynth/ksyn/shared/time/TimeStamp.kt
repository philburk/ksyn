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
 * @author Phil Burk, (C) 2009 Mobileer Inc
 */
data class TimeStamp(val time: Double) : Comparable<TimeStamp> {

    /**
     * @return -1 if (this < t2), 0 if equal, or +1
     */
    override fun compareTo(other: TimeStamp): Int {
        return time.compareTo(other.time)
    }

    /**
     * Create a new TimeStamp at a relative offset in seconds.
     *
     * @param delta
     * @return earlier or later TimeStamp
     */
    fun makeRelative(delta: Double): TimeStamp {
        return TimeStamp(time + delta)
    }

}
