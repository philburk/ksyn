/*
 * Copyright 2023 Phil Burk, Mobileer Inc
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

package com.softsynth.ksyn.unitgen

import kotlin.test.Test
import kotlin.test.assertEquals

class TestPanners : NonRealTimeTestCase() {

    @Test
    fun testPan() {
        val pan = Pan()
        synthesisEngine.add(pan)
        
        pan.input.set(1.0)
        pan.pan.set(0.0) // center
        
        synthesisEngine.start()
        pan.start()
        checkSleepUntil(synthesisEngine.currentTime + 0.01)
        
        // pan center should be 0.5 and 0.5 because it's (pan*0.5)+0.5 -> 0.5
        assertEquals(0.5f, pan.output.getValue(0), 0.001f, "pan left center")
        assertEquals(0.5f, pan.output.getValue(1), 0.001f, "pan right center")
        
        pan.pan.set(-1.0) // hard left
        checkSleepUntil(synthesisEngine.currentTime + 0.02)
        
        // hard left (pan=-1) -> right = 0.0, left = 1.0
        assertEquals(1.0f, pan.output.getValue(0), 0.001f, "pan left")
        assertEquals(0.0f, pan.output.getValue(1), 0.001f, "pan right")
    }

    @Test
    fun testFourWayFade() {
        val fade = FourWayFade()
        synthesisEngine.add(fade)
        
        fade.input.set(0, 1.0f)
        fade.input.set(1, 2.0f)
        fade.input.set(2, 3.0f)
        fade.input.set(3, 4.0f)
        
        synthesisEngine.start()
        fade.start()
        
        fade.fade.set(0, -1.0f) // left
        fade.fade.set(1, 1.0f) // top 
        checkSleepUntil(synthesisEngine.currentTime + 0.01)
        
        // expected to pick input 0 (bottom-left from fade mappings) = 1.0
        assertEquals(1.0f, fade.output.value, 0.001f, "fade top left (1)")
        
        fade.fade.set(0, 1.0f) // right
        fade.fade.set(1, 1.0f) // top 
        checkSleepUntil(synthesisEngine.currentTime + 0.02)
        
        // expected to pick input 1 (bottom-right from fade mappings) = 2.0
        assertEquals(2.0f, fade.output.value, 0.001f, "fade top right (2)")


        fade.fade.set(0, -1.0f) // left
        fade.fade.set(1, -1.0f) // bottom 
        checkSleepUntil(synthesisEngine.currentTime + 0.01)
        
        // expected to pick input 3 (bottom-left from fade mappings) = 4.0
        assertEquals(4.0f, fade.output.value, 0.001f, "fade bottom left (3)")
        
        fade.fade.set(0, 1.0f) // right
        fade.fade.set(1, -1.0f) // bottom 
        checkSleepUntil(synthesisEngine.currentTime + 0.02)
        
        // expected to pick input 2 (bottom-right from fade mappings) = 3.0
        assertEquals(3.0f, fade.output.value, 0.001f, "fade bottom right (2)")
    }
}
