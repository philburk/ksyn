/*
 * Copyright 2024 Phil Burk, Mobileer Inc
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

import kotlin.test.*

class TestOnOffAllocator {
    @Test
    fun testAllocation() {
        val things = arrayOf("A", "B", "C")
        val allocator = OnOffAllocator(things)

        val v1 = allocator.on(10)
        assertTrue(allocator.isOn(10))

        val v2 = allocator.on(20)
        assertNotEquals(v1, v2)

        val v3 = allocator.on(30)
        assertNotEquals(v1, v3)
        assertNotEquals(v2, v3)

        // Steal oldest (A is oldest)
        val v4 = allocator.on(40)
        assertEquals(v1, v4)
        assertFalse(allocator.isOn(10))
        assertTrue(allocator.isOn(40))
    }

    @Test
    fun testReallocation() {
        val things = arrayOf("A", "B")
        val allocator = OnOffAllocator(things)

        val v1 = allocator.on(10) // A
        val v2 = allocator.on(20) // B

        val v3 = allocator.on(10)
        assertEquals(v1, v3)

        // B is now the oldest because A was re-allocated (tick increased)
        val v4 = allocator.on(30)
        assertEquals(v2, v4)
    }

    @Test
    fun testOffAndSteal() {
        val things = arrayOf("A", "B", "C")
        val allocator = OnOffAllocator(things)

        val v1 = allocator.on(10) // A, tick 0
        val v2 = allocator.on(20) // B, tick 1
        val v3 = allocator.on(30) // C, tick 2

        allocator.off(20) // B, tick 3, on=false
        allocator.off(10) // A, tick 4, on=false

        // Now B has tick 3, A has tick 4, C is ON with tick 2.
        // stealVoice should pick B because it's OFF and has the lowest tick among OFF things.
        val v4 = allocator.on(40)
        assertEquals(v2, v4, "Should steal v2 because it is OFF and was turned OFF before A.")

        val v5 = allocator.on(50)
        assertEquals(v1, v5, "Should steal v1 because it is the next oldest OFF thing.")

        val v6 = allocator.on(60)
        assertEquals(v3, v6, "Should steal v3 because it is the oldest (and only) ON thing.")
    }

    @Test
    fun testFindVoice() {
        val things = arrayOf("A", "B")
        val allocator = OnOffAllocator(things)

        allocator.on(10)
        assertEquals("A", allocator.findVoice(10))
        assertNull(allocator.findVoice(20))

        allocator.off(10)
        assertEquals("A", allocator.findVoice(10), "Should still find the voice even if it is OFF.")
    }

    @Test
    fun testAllOff() {
        val things = arrayOf("A", "B")
        val allocator = OnOffAllocator(things)

        allocator.on(10)
        allocator.on(20)
        assertTrue(allocator.isOn(10))
        assertTrue(allocator.isOn(20))

        allocator.allOff()
        assertFalse(allocator.isOn(10))
        assertFalse(allocator.isOn(20))
    }
}
