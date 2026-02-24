/*
 * Copyright 2011 Phil Burk, Mobileer Inc
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

import com.softsynth.ksyn.unitgen.UnitVoice

/**
 * Describe a voice so that a user can pick it out of an InstrumentLibrary.
 * 
 * @author Phil Burk (C) 2011 Mobileer Inc
 */
abstract class VoiceDescription(var name: String, val presetNames: Array<String>) {

    val presetCount: Int
        get() = presetNames.size

    abstract fun getTags(presetIndex: Int): Array<String>

    /**
     * Instantiate one of these voices. You may want to call usePreset(n) on the voice after
     * instantiating it.
     * 
     * @return a voice
     */
    abstract fun createUnitVoice(): UnitVoice

    abstract val voiceClassName: String

    override fun toString(): String {
        return "$name[$presetCount]"
    }
}
