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

package com.softsynth.ksyn.shared.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test command scheduling on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class TestScheduledQueue {

    @Test
    fun testTimeStamps() {
        val t10 = TimeStamp(10.0)
        val t20 = TimeStamp(20.0)
        assertTrue(t10 < t20)
        assertTrue(t20 > t10)
        assertEquals(0, t10.compareTo(t10))
        val t25 = t20.makeRelative(5.0)
        assertEquals(25.0, t25.time)
    }

    @Test
    fun clearQueue() {
        val queue = ScheduledQueue<Int>()
        val t = TimeStamp(10.0)
        assertTrue(queue.isEmpty)
        queue.add(t, 67)
        assertFalse(queue.isEmpty)
        queue.clear()
        assertTrue(queue.isEmpty)
    }

    @Test
    fun oneObjectInQueue() {
        val queue = ScheduledQueue<Int>()
        val t = TimeStamp(10.0)
        assertTrue(queue.isEmpty)
        queue.add(t, 1)
        assertFalse(queue.isEmpty)
        assertEquals(t, queue.nextTime)
        assertEquals(1, queue.removeNext(t))
        assertTrue(queue.isEmpty)
    }

    @Test
    fun twoObjectsInQueue() {
        val queue = ScheduledQueue<Int>()
        val t1 = TimeStamp(10.0)
        val t2 = TimeStamp(20.0)
        assertTrue(queue.isEmpty)
        queue.add(t1, 1)
        assertFalse(queue.isEmpty)
        queue.add(t2, 2)
        assertEquals(t1, queue.nextTime)
        assertEquals(1, queue.removeNext(t1))
        assertEquals(t2, queue.nextTime)
        assertEquals(2, queue.removeNext(t2))
        assertTrue(queue.isEmpty)
    }

    @Test
    fun twoObjectsAddedInReverseOrder() {
        val queue = ScheduledQueue<Int>()
        val t1 = TimeStamp(10.0)
        val t2 = TimeStamp(20.0)
        queue.add(t2, 2) // reverse order
        queue.add(t1, 1)
        assertEquals(t1, queue.nextTime)
        assertEquals(1, queue.removeNext(t1))
        assertEquals(t2, queue.nextTime)
        assertEquals(2, queue.removeNext(t2))
    }

    @Test
    fun beforeAndAfter() {
        val queue = ScheduledQueue<Int>()
        val t10 = TimeStamp(10.0)
        val t15 = TimeStamp(15.0)
        val t20 = TimeStamp(20.0)
        queue.add(t15, 123)
        assertEquals(t15, queue.nextTime)
        assertEquals(null, queue.removeNext(t10))
        assertEquals(123, queue.removeNext(t20))
    }

    @Test
    fun testRemoveNextList() {
        val queue = ScheduledQueue<Int>()
        val t10 = TimeStamp(10.0)
        val t12 = TimeStamp(12.0)
        val t15 = TimeStamp(15.0)
        val t20 = TimeStamp(20.0)
        val t22 = TimeStamp(22.0)
        queue.add(t15, 150)
        queue.add(t12, 120)
        queue.add(t20, 200)
        queue.add(t15, 151)

        // The queue has events at 12, 15, 20
        // Get events up to time 10. Should be none.
        var list = queue.removeNextList(t10)
        assertEquals(null, list)
        assertEquals(t12, queue.nextTime)
        list = queue.removeNextList(t20)
        assertEquals(listOf(120), list)
        list = queue.removeNextList(t20)
        assertEquals(listOf(150, 151), list)
        assertEquals(t20, queue.nextTime) // next is 20
    }

}
