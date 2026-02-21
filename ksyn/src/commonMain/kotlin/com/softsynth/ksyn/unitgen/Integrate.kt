package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.toSample

/**
 * Integrate unit.
 *
 * Output accumulated sum of the input signal. This can be used to transform one signal into
 * another, or to generate ramps between the limits by setting the input signal positive or
 * negative. For a "leaky integrator" use a FilterOnePoleOneZero.
 */
class Integrate : UnitFilter() {
    val lowerLimit = UnitInputPort("LowerLimit", -1.0)
    val upperLimit = UnitInputPort("UpperLimit", 1.0)

    private var accum: Double = 0.0

    init {
        addPort(lowerLimit)
        addPort(upperLimit)
    }

    override fun generate() {
        val inputs = input.getValues()
        val lowerLimits = lowerLimit.getValues()
        val upperLimits = upperLimit.getValues()
        val outputs = output.getValues()

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            accum += inputs[i].toDouble()

            // clip to limits
            if (accum > upperLimits[i].toDouble()) {
                accum = upperLimits[i].toDouble()
            } else if (accum < lowerLimits[i].toDouble()) {
                accum = lowerLimits[i].toDouble()
            }

            outputs[i] = accum.toSample()
        }
    }
}
