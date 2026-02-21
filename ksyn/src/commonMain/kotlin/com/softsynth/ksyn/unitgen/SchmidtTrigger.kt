package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.toSample

/**
 * SchmidtTrigger unit.
 *
 * Output logic level value with hysteresis. Transition high when input exceeds setLevel. Only go
 * low when input is below resetLevel.
 */
class SchmidtTrigger : UnitFilter() {
    val setLevel = UnitInputPort("SetLevel")
    val resetLevel = UnitInputPort("ResetLevel")
    val outputPulse = UnitOutputPort("OutputPulse")

    init {
        addPort(setLevel)
        addPort(resetLevel)
        addPort(outputPulse)
    }

    override fun generate() {
        val inPtr = input.getValues()
        val pulsePtr = outputPulse.getValues()
        val outPtr = output.getValues()
        val setPtr = setLevel.getValues()
        val resetPtr = resetLevel.getValues()

        var outputValue = outPtr[0]
        var state = (outputValue > FALSE.toSample())

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            pulsePtr[i] = FALSE.toSample()
            if (state) {
                if (inPtr[i] <= resetPtr[i]) {
                    state = false
                    outputValue = FALSE.toSample()
                }
            } else {
                if (inPtr[i] > setPtr[i]) {
                    state = true
                    outputValue = TRUE.toSample()
                    pulsePtr[i] = TRUE.toSample()
                }
            }
            outPtr[i] = outputValue
        }
    }
}
