/*
 * Copyright 2026 Phil Burk, Mobileer Inc
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

import com.softsynth.ksyn.toSample
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSchmittTrigger {

    @Test
    fun testSchmittTriggerHysteresis() {
        val trigger = SchmittTrigger()
        trigger.setLevel.set(0.5)
        trigger.resetLevel.set(-0.2)

        // Frame 0: input = 0.0 (below setLevel, initial state false)
        // Frame 1: input = 0.6 (exceeds setLevel -> transitions high, emits pulse)
        // Frame 2: input = 0.1 (drops below setLevel but above resetLevel -> remains high, pulse is low)
        // Frame 3: input = -0.3 (drops below resetLevel -> transitions low)
        val inValues = trigger.input.getValues()
        inValues[0] = 0.0f
        inValues[1] = 0.6f
        inValues[2] = 0.1f
        inValues[3] = -0.3f

        trigger.generate()

        val outValues = trigger.output.getValues()
        val pulseValues = trigger.outputPulse.getValues()

        assertEquals(UnitGenerator.FALSE.toSample(), outValues[0])
        assertEquals(UnitGenerator.FALSE.toSample(), pulseValues[0])

        assertEquals(UnitGenerator.TRUE.toSample(), outValues[1])
        assertEquals(UnitGenerator.TRUE.toSample(), pulseValues[1])

        assertEquals(UnitGenerator.TRUE.toSample(), outValues[2])
        assertEquals(UnitGenerator.FALSE.toSample(), pulseValues[2])

        assertEquals(UnitGenerator.FALSE.toSample(), outValues[3])
        assertEquals(UnitGenerator.FALSE.toSample(), pulseValues[3])
    }
}

