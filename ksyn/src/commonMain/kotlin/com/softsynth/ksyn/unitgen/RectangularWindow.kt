/*
 * Copyright 2013 Phil Burk, Mobileer Inc
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

import com.softsynth.ksyn.data.SpectralWindow

/**
 * Window that is just 1.0. Flat like a rectangle.
 *
 * @author Phil Burk (C) 2013 Mobileer Inc
 */
class RectangularWindow private constructor() : SpectralWindow {

    /** This always returns 1.0. Do not pass indices outside the window range. */
    override fun get(index: Int): Float = 1.0f

    override fun size(): Int = Int.MAX_VALUE

    companion object {
        private val instance = RectangularWindow()
        fun getInstance(): RectangularWindow = instance
    }
}
