package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.toSample

/**
 * Latches when input crosses zero.
 * 
 * Pass a value unchanged if gate true, otherwise pass input unchanged until input crosses zero then
 * output zero.
 */
class LatchZeroCrossing : UnitGenerator() {
    val input = UnitInputPort("Input")
    val gate = UnitInputPort("Gate", 1.0)
    val output = UnitOutputPort("Output")
    
    private var held: Float = 0.0f
    private var crossed: Boolean = false

    init {
        addPort(input)
        addPort(gate)
        addPort(output)
    }

    override fun generate() {
        val inputs = input.getValues()
        val gates = gate.getValues()
        val outputs = output.getValues()

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            val current = inputs[i]
            if (gates[i] > 0.0f) {
                held = current
                crossed = false
            } else {
                if (!crossed) {
                    if ((held * current) <= 0.0f) {
                        held = 0.0f
                        crossed = true
                    } else {
                        held = current
                    }
                }
            }
            outputs[i] = held
        }
    }
}
