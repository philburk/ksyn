package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.toSample

/**
 * Convert an input signal between -1.0 and +1.0 to the range min to max. This is handy when using
 * an oscillator as a modulator.
 */
class RangeConverter : UnitFilter() {
    val min = UnitInputPort("Min", 40.0)
    val max = UnitInputPort("Max", 2000.0)

    init {
        addPort(min)
        addPort(max)
    }

    override fun generate() {
        val inputs = input.getValues()
        val mins = min.getValues()
        val maxs = max.getValues()
        val outputs = output.getValues()

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            val low = mins[i]
            val high = maxs[i]
            outputs[i] = (low + (high - low) * (inputs[i] + 1.0f) * 0.5f).toSample()
        }
    }
}
