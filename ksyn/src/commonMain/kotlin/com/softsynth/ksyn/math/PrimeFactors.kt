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

package com.softsynth.ksyn.math

/**
 * Tool for factoring primes and prime ratios. This class contains a static array of primes
 * generated using the Sieve of Eratosthenes.
 *
 * @author Phil Burk (C) 2011 Mobileer Inc
 */

private const val SIEVE_SIZE = 1000

private val primes: IntArray = run {
    // Use Sieve of Eratosthenes to fill Prime table
    val sieve = BooleanArray(SIEVE_SIZE)
    val primeList = ArrayList<Int>()
    var i = 2
    while (i < (SIEVE_SIZE / 2)) {
        if (!sieve[i]) {
            primeList.add(i)
            var multiple = 2 * i
            while (multiple < SIEVE_SIZE) {
                sieve[multiple] = true
                multiple += i
            }
        }
        i += 1
    }
    primeList.toIntArray()
}

class PrimeFactors {
    private val factors: IntArray

    constructor(factors: IntArray) {
        this.factors = factors
    }

    constructor(numerator: Int, denominator: Int) {
        val topFactors = factor(numerator)
        val bottomFactors = factor(denominator)
        factors = subtract(topFactors, bottomFactors)
    }

    fun subtract(pf: PrimeFactors): PrimeFactors {
        return PrimeFactors(subtract(factors, pf.factors))
    }

    fun add(pf: PrimeFactors): PrimeFactors {
        return PrimeFactors(add(factors, pf.factors))
    }

    val justRatio: JustRatio
        get() {
            var n: Long = 1
            var d: Long = 1
            for (i in factors.indices) {
                val exponent = factors[i]
                val p = primes[i]
                if (exponent > 0) {
                    for (k in 0 until exponent) {
                        n *= p
                    }
                } else if (exponent < 0) {
                    val posExponent = -exponent
                    for (k in 0 until posExponent) {
                        d *= p
                    }
                }
            }
            return JustRatio(n, d)
        }

    fun getFactors(): IntArray {
        return factors.clone()
    }

    override fun toString(): String {
        val buffer = StringBuilder()
        printFactors(buffer, 1)
        buffer.append("/")
        printFactors(buffer, -1)
        return buffer.toString()
    }

    private fun printFactors(buffer: StringBuilder, sign: Int) {
        var gotSome = false
        for (i in factors.indices) {
            val pf = factors[i] * sign
            if (pf > 0) {
                if (gotSome)
                    buffer.append('*')
                val prime = primes[i]
                if (pf == 1) {
                    buffer.append("" + prime)
                } else if (pf == 2) {
                    buffer.append("$prime*$prime")
                } else if (pf > 2) {
                    buffer.append("($prime^$pf)")
                }
                gotSome = true
            }
        }
        if (!gotSome) {
            buffer.append("1")
        }
    }
}

// Static utilities moved to top level

fun subtract(factorsA: IntArray, factorsB: IntArray): IntArray {
    val max: Int
    val min: Int
    if (factorsA.size > factorsB.size) {
        max = factorsA.size
        min = factorsB.size
    } else {
        min = factorsA.size
        max = factorsB.size
    }
    val primeList = ArrayList<Int>()
    var i = 0
    while (i < min) {
        primeList.add(factorsA[i] - factorsB[i])
        i++
    }
    if (factorsA.size > factorsB.size) {
        while (i < max) {
            primeList.add(factorsA[i])
            i++
        }
    } else {
        while (i < max) {
            primeList.add(0 - factorsB[i])
            i++
        }
    }
    trimPrimeList(primeList)
    return primeList.toIntArray()
}

fun add(factorsA: IntArray, factorsB: IntArray): IntArray {
    val max: Int
    val min: Int
    if (factorsA.size > factorsB.size) {
        max = factorsA.size
        min = factorsB.size
    } else {
        min = factorsA.size
        max = factorsB.size
    }
    val primeList = ArrayList<Int>()
    var i = 0
    while (i < min) {
        primeList.add(factorsA[i] + factorsB[i])
        i++
    }
    if (factorsA.size > factorsB.size) {
        while (i < max) {
            primeList.add(factorsA[i])
            i++
        }
    } else if (factorsB.size > factorsA.size) {
        while (i < max) {
            primeList.add(factorsB[i])
            i++
        }
    }
    trimPrimeList(primeList)
    return primeList.toIntArray()
}

private fun trimPrimeList(primeList: ArrayList<Int>) {
    var i = primeList.size - 1
    // trim zero factors off end.
    while (i >= 0) {
        if (primeList[i] == 0) {
            primeList.removeAt(i)
        } else {
            break
        }
        i--
    }
}

fun factor(n: Int): IntArray {
    var num = n
    val primeList = ArrayList<Int>()
    var i = 0
    var p = primes[i]
    var exponent = 0
    while (num > 1) {
        // does the prime number divide evenly into n?
        val d = num / p
        val m = d * p;
        if (m == num) {
            num = d
            exponent += 1
        } else {
            primeList.add(exponent)
            exponent = 0
            i += 1
            p = primes[i]
        }
    }
    if (exponent > 0) {
        primeList.add(exponent)
    }
    return primeList.toIntArray()
}

/**
 * Get prime from table.
 *
 *
 * @param n Warning: Do not exceed getPrimeCount()-1.
 * @return Nth prime number, the 0th prime is 2
 */
fun getPrime(n: Int): Int {
    return primes[n]
}

/**
 * @return the number of primes stored in the table
 */
fun getPrimeCount(): Int {
    return primes.size
}
