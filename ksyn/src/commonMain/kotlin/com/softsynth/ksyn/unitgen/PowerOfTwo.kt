package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.toSample
import kotlin.math.floor
import kotlin.math.pow

/**
 * output = (2.0^input) This is useful for converting a pitch modulation value into a frequency
 * scaler. An input value of +1.0 will output 2.0 for an octave increase. An input value of -1.0
 * will output 0.5 for an octave decrease.
 */
open class PowerOfTwo : UnitGenerator() {
    val input = UnitInputPort("Input")
    val output = UnitOutputPort("Output")

    private var lastInput: Double = 0.0
    private var lastOutput: Double = 1.0

    init {
        addPort(input)
        input.setup(-8.0, 0.0, 8.0)
        addPort(output)
    }

    override fun generate() {
        val inputs = input.getValues()
        val outputs = output.getValues()

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            val inVal = inputs[i].toDouble()
            if (inVal == lastInput) {
                outputs[i] = lastOutput.toSample()
            } else {
                lastInput = inVal
                val adjustedInput = adjustInput(inVal)
                var octave = floor(adjustedInput).toInt()
                val normal = adjustedInput - octave

                val findex = normal * NUM_VALUES
                val index = findex.toInt()
                val fraction = findex - index
                var value = table[index] + fraction * (table[index + 1] - table[index])

                while (octave > 0) {
                    octave -= 1
                    value *= 2.0
                }
                while (octave < 0) {
                    octave += 1
                    value *= 0.5
                }
                val adjustedOutput = adjustOutput(value)
                outputs[i] = adjustedOutput.toSample()
                lastOutput = adjustedOutput
            }
        }
    }

    open fun adjustInput(inVal: Double): Double {
        return inVal
    }

    open fun adjustOutput(outVal: Double): Double {
        return outVal
    }

    companion object {
        private const val NUM_VALUES = 2048
        private val table = DoubleArray(NUM_VALUES + 2)

        init {
            for (i in 0 until table.size) {
                table[i] = 2.0.pow(i.toDouble() / NUM_VALUES)
            }
        }
    }
}
