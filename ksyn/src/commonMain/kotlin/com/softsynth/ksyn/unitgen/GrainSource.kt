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

package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.data.AudioSample

/**
 * Defines classes that can provide the signal inside a Grain.
 * 
 * @author Phil Burk (C) 2011 Mobileer Inc
 */
interface GrainSource {
    var frameRate: AudioSample

    /** Generate one more value or the source signal. */
    fun next(): AudioSample

    /** Reset the internal phase of the grain. */
    fun reset()

    fun setRate(rate: Double)
}
