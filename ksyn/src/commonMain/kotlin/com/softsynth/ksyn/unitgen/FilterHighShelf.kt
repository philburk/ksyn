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
 * HighShelf Filter. This creates a flat response above the cutoff frequency. This filter is
 * sometimes used at the end of a bank of EQ filters.
 * 
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
class FilterHighShelf : FilterBiquadShelf() {
    /**
     * This method is called by by FilterBiquadShelf to update coefficients.
     */
    override fun updateCoefficients() {
        val scalar = 1.0 / (AP1 - AM1cs + beta_sn)
        a0 = (factorA * (AP1 + AM1cs + beta_sn) * scalar).toFloat()
        a1 = (-2.0 * factorA * (AM1 + AP1cs) * scalar).toFloat()
        a2 = (factorA * (AP1 + AM1cs - beta_sn) * scalar).toFloat()
        b1 = (2.0 * (AM1 - AP1cs) * scalar).toFloat()
        b2 = ((AP1 - AM1cs - beta_sn) * scalar).toFloat()
    }
}
