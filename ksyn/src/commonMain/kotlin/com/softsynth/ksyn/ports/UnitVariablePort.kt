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

package com.softsynth.ksyn.ports

import com.softsynth.ksyn.AudioSample
import com.softsynth.ksyn.KSyn
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.toSample

open class UnitVariablePort(name: String, value: AudioSample = KSyn.ZERO) : UnitPort(name), SettablePort {

    init {
        this.value = value
    }

    fun set(partNum: Int, value: Float) {
        this.value = value.toSample()
    }
    fun set(partNum: Int, value: Double) {
        this.value = value.toSample()
    }

    fun get(partNum: Int): AudioSample = value

    override fun set(partNum: Int, value: AudioSample, timeStamp: TimeStamp) {
        scheduleCommand(timeStamp.time) { set(partNum, value) }
    }
    
   override fun getValue(partNum: Int): AudioSample = value

    override val numParts: Int = 1
}
