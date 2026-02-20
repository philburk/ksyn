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

package com.softsynth.ksyn.unitgen

/**
 * Filter that allows frequencies near the center frequency to pass. This filter is based on the
 * BiQuad filter. Coefficients are updated whenever the frequency or Q changes.
 *
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
class FilterBandPass : FilterBiquadCommon() {

    override fun updateCoefficients() {
        val scalar = 1.0 / (1.0 + alpha)
        val halfAlpha = alpha * 0.5

        this.a0 = (halfAlpha * scalar).toFloat()
        this.a1 = 0.0f
        this.a2 = (-halfAlpha * scalar).toFloat()
        this.b1 = (-2.0 * cos_omega * scalar).toFloat()
        this.b2 = ((1.0 - alpha) * scalar).toFloat()
    }
}
