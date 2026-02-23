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
 * This envelope should start at 0.0, go up to 1.0 and then return to 0.0 in duration time.
 * 
 * @author Phil Burk (C) 2011 Mobileer Inc
 */
interface GrainEnvelope {

    var frameRate: AudioSample

    /**
     * @return next amplitude value of envelope
     */
    fun next(): AudioSample

    /**
     * Are there any more values to be generated in the envelope?
     * 
     * @return true if more
     */
    fun hasMoreValues(): Boolean

    /**
     * Prepare to start a new envelope.
     */
    fun reset()

    /**
     * @param duration in seconds.
     */
    fun setDuration(duration: Double)
}
