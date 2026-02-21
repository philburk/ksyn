package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.toSample

/**
 * Count zero crossings. Handy for unit tests.
 */
class ZeroCrossingCounter : UnitGenerator() {
    val input = UnitInputPort("Input")
    val output = UnitOutputPort("Output")

    var count: Long = 0
        private set
    private var armed: Boolean = false

    init {
        addPort(input)
        addPort(output)
    }

    override fun generate() {
        val inputs = input.getValues()
        val outputs = output.getValues()

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            val value = inputs[i]
            if (value < -THRESHOLD) {
                armed = true
            } else if (armed && (value > THRESHOLD)) {
                count++
                armed = false
            }
            outputs[i] = value
        }
    }

    companion object {
        private const val THRESHOLD = 0.0001f
    }
}
