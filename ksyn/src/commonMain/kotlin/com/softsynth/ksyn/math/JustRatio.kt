package com.softsynth.ksyn.math

class JustRatio(var numerator: Long, var denominator: Long) {

    constructor(numerator: Int, denominator: Int) : this(numerator.toLong(), denominator.toLong())

    val value: Double
        get() = numerator.toDouble() / denominator

    fun invert() {
        val temp = denominator
        denominator = numerator
        numerator = temp
    }

    override fun toString(): String {
        return "$numerator/$denominator"
    }
}
