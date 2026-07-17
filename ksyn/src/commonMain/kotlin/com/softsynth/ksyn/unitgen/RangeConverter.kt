package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.toSample

/**
 * Convert an input signal between -1.0 and +1.0 to the range min to max. This is handy when using
 * an oscillator as a modulator.
 */
class RangeConverter : UnitFilter() {
    val min = UnitInputPort("Min")
    val max = UnitInputPort("Max")

    init {
        addPort(min)
        min.setup(0.0f, 40.0f, 100.0f)
        addPort(max)
        max.setup(101.0f, 2000.0f, 4000.0f)
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
