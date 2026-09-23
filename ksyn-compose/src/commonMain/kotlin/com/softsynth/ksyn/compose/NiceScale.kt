package com.softsynth.ksyn.compose

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

internal fun formatNumber(value: Float, decimals: Int): String {
    var factor = 1f
    repeat(decimals) { factor *= 10f }
    val rounded = round(value * factor) / factor
    val str = rounded.toString()
    if (decimals <= 0) {
        return str.substringBefore('.')
    }
    val parts = str.split('.')
    val intPart = parts[0]
    val fracPart = if (parts.size > 1) parts[1] else ""
    val paddedFrac = fracPart.padEnd(decimals, '0').take(decimals)
    return "$intPart.$paddedFrac"
}

/**
 * Paul Heckbert's "Nice Numbers" algorithm for finding clean, comfortable graph tick intervals.
 */
internal fun niceNum(range: Double, round: Boolean): Double {
    val exponent = floor(log10(range))
    val fraction = range / 10.0.pow(exponent)
    val niceFraction: Double = if (round) {
        if (fraction < 1.5) 1.0
        else if (fraction < 3.0) 2.0
        else if (fraction < 7.0) 5.0
        else 10.0
    } else {
        if (fraction <= 1.0) 1.0
        else if (fraction <= 2.0) 2.0
        else if (fraction <= 5.0) 5.0
        else 10.0
    }
    return niceFraction * 10.0.pow(exponent)
}

internal data class NiceScale(val min: Float, val max: Float, val step: Float, val ticks: List<Float>)

internal fun calculateNiceScale(min: Float, max: Float, targetTicks: Int = 4): NiceScale {
    val range = (max - min).toDouble()
    if (range <= 0.0) {
        return NiceScale(min, max, 1f, listOf(min))
    }
    val step = niceNum(range / targetTicks.toDouble(), round = true).toFloat()
    if (step <= 0f) {
        return NiceScale(min, max, 1f, listOf(min, max))
    }

    val eps = step * 0.001f
    val ticks = mutableListOf<Float>()
    var t = (floor(min / step) * step)
    while (t <= max + eps) {
        if (t >= min - eps) {
            ticks.add(t)
        }
        t += step
    }
    return NiceScale(min, max, step, ticks)
}

/**
 * Like [calculateNiceScale] but widens [min] and [max] outward to the nearest multiples
 * of the nice step, so the scale always starts and ends on a labelled tick.
 */
internal fun calculateExpandedNiceScale(min: Float, max: Float, targetTicks: Int = 4): NiceScale {
    val step = calculateNiceScale(min, max, targetTicks).step
    // Small tolerance so a value that is already on a tick does not add an extra step.
    val niceMin = floor(min / step + 1e-3f) * step
    val niceMax = ceil(max / step - 1e-3f) * step
    return NiceScale(niceMin, niceMax, step, ticksFor(niceMin, niceMax, step))
}

private fun ticksFor(min: Float, max: Float, step: Float): List<Float> {
    val eps = step * 0.001f
    val ticks = mutableListOf<Float>()
    var i = 0
    while (true) {
        // Multiply rather than accumulate to avoid float drift.
        val t = min + i * step
        if (t > max + eps) break
        ticks.add(t)
        i++
    }
    return ticks
}

internal fun formatNiceNumber(value: Float, step: Float): String {
    val roundedVal = round(value * 10000f) / 10000f
    val v = if (abs(roundedVal) < 1e-6f) 0f else roundedVal
    val decimals = when {
        step >= 1f -> 0
        step >= 0.1f -> 1
        step >= 0.01f -> 2
        else -> 3
    }
    return formatNumber(v, decimals)
}
