/*
 * Copyright 2010 Phil Burk, Mobileer Inc
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

import com.softsynth.ksyn.ports.UnitDataQueuePort
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort

/**
 * Base class for reading a sample or envelope.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
abstract class SequentialDataReader : UnitGenerator() {
    var dataQueue: UnitDataQueuePort
    var amplitude: UnitInputPort
    lateinit var output: UnitOutputPort

    init {
        dataQueue = UnitDataQueuePort("Data")
        addPort(dataQueue)
        
        amplitude = UnitInputPort("Amplitude", UnitOscillator.DEFAULT_AMPLITUDE)
        addPort(amplitude)
    }
}
