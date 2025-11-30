/*
 * Copyright 1997 Phil Burk, Mobileer Inc
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

package com.softsynth.ksyn.math

import kotlin.math.max
import kotlin.math.pow

/**
 * Polynomial<br>
 * Implement polynomial using List as coefficient holder. Element index is power of X, value at a
 * given index is coefficient.<br>
 * <br>
 *
 * @author Nick Didkovsky, (C) 1997 Phil Burk and Nick Didkovsky
 */

class Polynomial {

    private val terms: MutableList<Double>

    /** create a polynomial with no terms */
    constructor() {
        terms = ArrayList()
    }

    /** create a polynomial with one term of specified constant */
    constructor(c0: Double) : this() {
        appendTerm(c0)
    }

    /** create a polynomial with two terms with specified coefficients */
    constructor(c1: Double, c0: Double) : this(c0) {
        appendTerm(c1)
    }

    /** create a polynomial with specified coefficients */
    constructor(c2: Double, c1: Double, c0: Double) : this(c1, c0) {
        appendTerm(c2)
    }

    /** create a polynomial with specified coefficients */
    constructor(c3: Double, c2: Double, c1: Double, c0: Double) : this(c2, c1, c0) {
        appendTerm(c3)
    }

    /** create a polynomial with specified coefficients */
    constructor(c4: Double, c3: Double, c2: Double, c1: Double, c0: Double) : this(c3, c2, c1, c0) {
        appendTerm(c4)
    }

    /**
     * Append a term with specified coefficient. Power will be next available order (ie if the
     * polynomial is of order 2, appendTerm will supply the coefficient for x^3
     */
    fun appendTerm(coefficient: Double) {
        terms.add(coefficient)
    }

    /** Set the coefficient of given term */
    fun setTerm(coefficient: Double, power: Int) {
        // If setting a term greater than the current order of the polynomial, pad with zero terms
        val size = terms.size
        if (power >= size) {
            for (i in 0 until (power - size + 1)) {
                appendTerm(0.0)
            }
        }
        terms[power] = coefficient
    }

    /**
     * Add the coefficient of given term to the specified coefficient. ex. addTerm(3, 1) add 3x to a
     * polynomial, addTerm(4, 3) adds 4x^3
     */
    fun addTerm(coefficient: Double, power: Int) {
        setTerm(coefficient + get(power), power)
    }

    /** @return coefficient of nth term (first term=0) */
    fun get(power: Int): Double {
        return if (power >= terms.size)
            0.0
        else
            terms[power]
    }

    /** @return number of terms in this polynomial */
    fun size(): Int {
        return terms.size
    }

    /**
     * Add two polynomials together
     *
     * @return new Polynomial that is the sum of p1 and p2
     */
    operator fun plus(p2: Polynomial): Polynomial {
        val sum = Polynomial()
        for (i in 0 until max(this.size(), p2.size())) {
            sum.appendTerm(this.get(i) + p2.get(i))
        }
        return sum
    }

    /**
     * Subtract polynomial from another. (First arg - Second arg)
     *
     * @return new Polynomial p1 - p2
     */
    operator fun minus(p2: Polynomial): Polynomial {
        val sum = Polynomial()
        for (i in 0 until max(this.size(), p2.size())) {
            sum.appendTerm(this.get(i) - p2.get(i))
        }
        return sum
    }

    /**
     * Multiply two Polynomials
     *
     * @return new Polynomial that is the product p1 * p2
     */

    operator fun times(p2: Polynomial): Polynomial {
        val product = Polynomial()
        for (i in 0 until this.size()) {
            for (j in 0 until p2.size()) {
                product.addTerm(this.get(i) * p2.get(j), i + j)
            }
        }
        return product
    }

    /**
     * Multiply a Polynomial by a scaler
     *
     * @return new Polynomial that is the product p1 * p2
     */
    operator fun times(scaler: Double): Polynomial {
        val product = Polynomial()
        for (i in 0 until this.size()) {
            product.appendTerm(this.get(i) * scaler)
        }
        return product
    }

    /** Evaluate this polynomial for x */
    fun evaluate(x: Double): Double {
        var result = 0.0
        for (i in 0 until terms.size) {
            result += get(i) * x.pow(i)
        }
        return result
    }

    override fun toString(): String {
        var s = ""
        if (size() == 0)
            s = "empty polynomial"
        var somethingPrinted = false
        for (i in size() - 1 downTo 0) {
            if (get(i) != 0.0) {
                if (somethingPrinted)
                    s += " + "
                var coeff = ""
                if ((get(i) != 1.0) || (i == 0))
                    coeff += get(i)
                if (i == 0)
                    s += coeff
                else {
                    var power = ""
                    if (i != 1)
                        power = "^$i"
                    s += coeff + "x" + power
                }
                somethingPrinted = true;
            }
        }
        return s
    }
}
