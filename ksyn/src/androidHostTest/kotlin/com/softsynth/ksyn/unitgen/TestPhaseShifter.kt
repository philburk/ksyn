package com.softsynth.ksyn.unitgen

import kotlin.test.Test
import kotlin.test.assertTrue

class TestPhaseShifter : NonRealTimeTestCase() {

    @Test
    fun testPhaseShifter() {
        val osc = SineOscillator()
        val phaser = PhaseShifter()

        synthesisEngine.add(osc)
        synthesisEngine.add(phaser)

        osc.output.connect(phaser.input)
        phaser.offset.set(0.1)
        phaser.depth.set(1.0)
        phaser.feedback.set(0.7)

        synthesisEngine.start()
        osc.start()
        phaser.start()

        checkSleepUntil(0.5)

        // Ensure PhaseShifter is generating some output natively differing from 0.0
        val out = phaser.output.value
        assertTrue(out != 0.0f, "PhaseShifter should output non-zero values.")
    }
}
