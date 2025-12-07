/*
 * Copyright 2009 Phil Burk, Mobileer Inc
 *
 * Licensed under the Apache License, Version2.0 (the "License");
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

package com.softsynth.ksyn.engine

import kotlin.time.*

/** Measure CPU load. */
@OptIn(ExperimentalTime::class)
class LoadAnalyzer internal constructor() {
    // Get the time source once.
    private val timeSource = TimeSource.Monotonic

    // CRITICAL FIX: Initialize each TimeMark directly from the TimeSource.
    private var stopTimeMark: TimeMark = timeSource.markNow()
    private var previousStopTimeMark: TimeMark = timeSource.markNow()
    private var startTimeMark: TimeMark = timeSource.markNow()

    private var averageTotalTime = 0.0
    private var averageOnTime = 0.0

    /**
     * Call this when you stop doing something. Ideally all of the time since start() was spent on
     * doing something without interruption.
     */
    fun stop() {
        previousStopTimeMark = stopTimeMark
        stopTimeMark = timeSource.markNow()

        // Alternative calculation without requiring the extension operator
        // (Start.elapsed - Stop.elapsed) == (Stop - Start)
        val onTime: Duration = startTimeMark.elapsedNow() - stopTimeMark.elapsedNow()
        val totalTime: Duration = previousStopTimeMark.elapsedNow() - stopTimeMark.elapsedNow()
        if (totalTime.isPositive()) {
            // Recursive averaging filter.
            val rate = 0.01
            averageOnTime = (averageOnTime * (1.0 - rate)) + (onTime.inWholeNanoseconds * rate)
            averageTotalTime =
                (averageTotalTime * (1.0 - rate)) + (totalTime.inWholeNanoseconds * rate)
        }
    }

    /** Call this when you start doing something. */
    fun start() {
        startTimeMark = timeSource.markNow()
    }

    /** Calculate, on average, how much of the time was spent doing something. */
    val averageLoad: Double
        get() = if (averageTotalTime > 0.0) {
            (averageOnTime / averageTotalTime).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
}
