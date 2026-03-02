package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.engine.NonRealTimeTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRouting : NonRealTimeTestCase() {

    @Test
    fun testDualInTwoOut() {
        val router = DualInTwoOut()
        val osc1 = SineOscillator()
        val osc2 = SineOscillator()

        synthesisEngine.add(router)
        synthesisEngine.add(osc1)
        synthesisEngine.add(osc2)

        osc1.output.connect(0, router.input, 0)
        osc2.output.connect(0, router.input, 1)

        synthesisEngine.start()
        osc1.start()
        osc2.start()
        router.start()

        checkSleepUntil(0.1)

        val outA = router.outputA.value
        val outB = router.outputB.value

        assertEquals(osc1.output.value, outA, 0.001)
        assertEquals(osc2.output.value, outB, 0.001)
    }

    @Test
    fun testTwoInDualOut() {
        val router = TwoInDualOut()
        val osc1 = SineOscillator()
        val osc2 = SineOscillator()

        synthesisEngine.add(router)
        synthesisEngine.add(osc1)
        synthesisEngine.add(osc2)

        osc1.output.connect(0, router.inputA, 0)
        osc2.output.connect(0, router.inputB, 0)

        synthesisEngine.start()
        osc1.start()
        osc2.start()
        router.start()

        checkSleepUntil(0.1)

        val out0 = router.output.getValue(0)
        val out1 = router.output.getValue(1)

        assertEquals(osc1.output.value, out0, 0.001)
        assertEquals(osc2.output.value, out1, 0.001)
    }

    @Test
    fun testMultiPassThrough() {
        val router = MultiPassThrough(3)
        val osc1 = SineOscillator()
        val osc2 = SineOscillator()
        val osc3 = SineOscillator()

        synthesisEngine.add(router)
        synthesisEngine.add(osc1)
        synthesisEngine.add(osc2)
        synthesisEngine.add(osc3)

        osc1.output.connect(0, router.input, 0)
        osc2.output.connect(0, router.input, 1)
        osc3.output.connect(0, router.input, 2)

        synthesisEngine.start()
        osc1.start()
        osc2.start()
        osc3.start()
        router.start()

        checkSleepUntil(0.1)

        val out0 = router.output.getValue(0)
        val out1 = router.output.getValue(1)
        val out2 = router.output.getValue(2)

        assertEquals(osc1.output.value, out0, 0.001)
        assertEquals(osc2.output.value, out1, 0.001)
        assertEquals(osc3.output.value, out2, 0.001)
    }
}
