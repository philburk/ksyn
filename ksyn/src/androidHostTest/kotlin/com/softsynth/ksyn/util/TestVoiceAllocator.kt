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

package com.softsynth.ksyn.util

import com.softsynth.ksyn.instruments.SubtractiveSynthVoice
import com.softsynth.ksyn.unitgen.UnitVoice
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestVoiceAllocator {
    private lateinit var allocator: VoiceAllocator
    private val max = 4
    private lateinit var voices: Array<UnitVoice>

    @BeforeTest
    fun beforeEach() {
        voices = Array(max) { SubtractiveSynthVoice() }
        allocator = VoiceAllocator(voices)
    }

    @Test
    fun testAllocation() {
        assertEquals(max, allocator.voiceCount, "get max")

        val tag1 = 61
        val tag2 = 62
        val tag3 = 63
        val tag4 = 64
        val tag5 = 65
        val tag6 = 66
        
        val voice1 = allocator.allocate(tag1)
        assertTrue(voice1 != null, "voice should be non-null")

        val voice2 = allocator.allocate(tag2)
        assertTrue(voice2 != null, "voice should be non-null")
        assertTrue(voice2 !== voice1, "new voice ")

        var voice = allocator.allocate(tag1)
        assertTrue(voice === voice1, "should be voice1 again ")

        voice = allocator.allocate(tag2)
        assertTrue(voice === voice2, "should be voice2 again ")

        val voice3 = allocator.allocate(tag3)
        val voice4 = allocator.allocate(tag4)

        val voice5 = allocator.allocate(tag5)
        assertTrue(voice5 === voice1, "ran out so get voice1 as oldest")

        voice = allocator.allocate(tag2)
        assertTrue(voice === voice2, "should be voice2 again ")

        // Now voice 3 should be the oldest cuz voice 2 was touched.
        val voice6 = allocator.allocate(tag6)
        assertTrue(voice6 === voice3, "ran out so get voice3 as oldest")
    }

    @Test
    fun testOff() {
        val tag1 = 61
        val tag2 = 62
        val tag3 = 63
        val tag4 = 64
        val tag5 = 65
        val tag6 = 66
        
        var voice1 = allocator.allocate(tag1)
        val voice2 = allocator.allocate(tag2)
        var voice3 = allocator.allocate(tag3)
        val voice4 = allocator.allocate(tag4)

        assertTrue(allocator.isOn(tag3), "voice 3 should start on")
        allocator.off(tag3)
        assertFalse(allocator.isOn(tag3), "voice 3 should now be off")

        allocator.off(tag2)

        val voice5 = allocator.allocate(tag5)
        assertTrue(voice5 === voice3, "should get voice3 cuz off first")
        val voice6 = allocator.allocate(tag6)
        assertTrue(voice6 === voice2, "should get voice2 cuz off second")
        voice3 = allocator.allocate(tag3)
        assertTrue(voice3 === voice1, "should get voice1 cuz on first")

        voice1 = allocator.allocate(tag1)
        assertTrue(voice1 === voice4, "should get voice4 cuz next up")
    }
}
