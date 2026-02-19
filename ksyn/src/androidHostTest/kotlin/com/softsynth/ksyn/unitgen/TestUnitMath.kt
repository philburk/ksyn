package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.engine.SynthesisEngine
import com.softsynth.ksyn.toSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test basic binary math operators.
 */
class TestUnitMath {

    @Test
    fun testMultiplyUnit() = runBlocking {
        checkUnitBinaryOperator(Multiply(), 2.0f, 3.0f, 6.0f)
    }

    @Test
    fun testAddUnit() = runBlocking {
        checkUnitBinaryOperator(Add(), 2.1f, 3.6f, 5.7f)
    }

    @Test
    fun testSubtractUnit() = runBlocking {
        checkUnitBinaryOperator(Subtract(), 11.0f, 3.6f, 7.4f)
    }

    @Test
    fun testDivideUnit() = runBlocking {
        checkUnitBinaryOperator(Divide(), 7.0f, 2.0f, 3.5f)
    }

    @Test
    fun testMaximumUnit() = runBlocking {
        checkUnitBinaryOperator(Maximum(), 7.0f, 2.0f, 7.0f)
        checkUnitBinaryOperator(Maximum(), -3.2f, 5.7f, 5.7f)
    }

    @Test
    fun testMinimumUnit() = runBlocking {
        checkUnitBinaryOperator(Minimum(), 7.0f, 2.0f, 2.0f)
        checkUnitBinaryOperator(Minimum(), -3.2f, 5.7f, -3.2f)
    }

    @Test
    fun testCompareUnit() = runBlocking {
        checkUnitBinaryOperator(Compare(), 7.0f, 2.0f, 1.0f)
        checkUnitBinaryOperator(Compare(), 4.1f, 4.1f, 0.0f) // equal
        checkUnitBinaryOperator(Compare(), -3.2f, 5.7f, 0.0f)
    }

    private suspend fun CoroutineScope.checkUnitBinaryOperator(unit: UnitBinaryOperator,
                                                               a: Float,
                                                               b: Float,
                                                               expected: Float) {
        val synth = SynthesisEngine()
        synth.add(unit)
        synth.start()
        unit.start()
        unit.inputA.set(a)
        unit.inputB.set(b)
        assertEquals(0.0.toSample(), unit.output.get(0))
        launch {
            synth.generateNextBuffer()
        }.join()
        assertEquals(expected, unit.output.getValues()[0])
    }

    @Test
    fun testMultiplyUnitDynamic() = runBlocking {
        checkUnitBinaryOperatorDynamic(Multiply(), { a, b -> a * b } )
    }

    @Test
    fun testAddUnitDynamic() = runBlocking {
        checkUnitBinaryOperatorDynamic(Add()) { a, b -> a + b }
    }

    @Test
    fun testSubtractUnitDynamic() = runBlocking {
        checkUnitBinaryOperatorDynamic(Subtract()) { a, b -> a - b }
    }

    @Test
    fun testMaximumUnitDynamic() = runBlocking {
        checkUnitBinaryOperatorDynamic(Maximum()) { a, b -> kotlin.math.max(a, b) }
    }

    @Test
    fun testMinimumUnitDynamic() = runBlocking {
        checkUnitBinaryOperatorDynamic(Minimum()) { a, b -> kotlin.math.min(a, b) }
    }

    @Test
    fun testCompareUnitDynamic() = runBlocking {
        checkUnitBinaryOperatorDynamic(Compare()) { a, b ->
            if (a > b) 1.0f else 0.0f
        }
    }

    private suspend fun CoroutineScope.checkUnitBinaryOperatorDynamic(unit: UnitBinaryOperator,
                                                                      calculateExpected: (Float, Float) -> Float) {
        val synth = SynthesisEngine()
        val oscA = SawtoothOscillator()
        val oscB = SawtoothOscillator()
        synth.add(oscA)
        synth.add(oscB)
        synth.add(unit)
        oscA.output.connect(unit.inputA)
        oscB.output.connect(unit.inputB)
        oscA.frequency.set(12345.0)
        oscB.frequency.set(18765.0)
        synth.start()
        unit.start()
        launch {
            synth.generateNextBuffer()
        }.join()

        val outputs = unit.output.getValues()
        val inputsA = oscA.output.getValues()
        val inputsB = oscB.output.getValues()

        for (i in outputs.indices) {
            val a = inputsA[i]
            val b = inputsB[i]
            // Use the lambda to calculate the expected value
            val expected = calculateExpected(a, b)
            assertEquals(expected, outputs[i], "Failed at index $i with inputs $a and $b")
        }
    }

}