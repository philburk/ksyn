package com.softsynth.ksyn.ports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestUnitInputPort {

    @Test
    fun testSet() {
        val port = UnitInputPort("input")
        assertEquals(0.0, port.get(0))
        port.set(0.2)
        assertEquals(0.2, port.get(0))
        port.set(-0.7)
        assertEquals(-0.7, port.get(0))
    }

    @Test
    fun testConnectInputToOutput() {
        val input = UnitInputPort("input")
        val output = UnitOutputPort("output")
        assertFalse(input.isConnected(), "should not be connected yet")
        input.connect(output)
        assertTrue(input.isConnected(), "should be connected")
    }

    @Test
    fun testConnectOutputToInput() {
        val input = UnitInputPort("input")
        val output = UnitOutputPort("output")
        assertFalse(input.isConnected(), "should not be connected yet")
        output.connect(input)
        assertTrue(input.isConnected(), "should be connected")
    }

    /** Internal value setting.  */
    @Test
    fun testSetValueInternal() {
        val numParts = 4
        val port = UnitInputPort(numParts, "Tester")
        port.setValueInternal(0, 100.0)
        port.setValueInternal(2, 120.0)
        port.setValueInternal(1, 110.0)
        port.setValueInternal(3, 130.0)
        assertEquals(100.0, port.getValue(0), "check port value")
        assertEquals(120.0, port.getValue(2), "check port value")
        assertEquals(110.0, port.getValue(1), "check port value")
        assertEquals(130.0, port.getValue(3), "check port value")
    }

    @Test
    fun testPullData() {
        val input = UnitInputPort("input")
        val output = UnitOutputPort("output")
        output.connect(input)
        val outputData = output.getValues()
        outputData.fill(23.45)
        input.pullData(0)
        val inputData = input.getValues()
        assertEquals(23.45, inputData[0])
    }

    @Test
    fun testMixingConnectedOutputs() {
        val input = UnitInputPort("input")
        val output1 = UnitOutputPort("output")
        val output2 = UnitOutputPort("output")
        output1.connect(input)
        output2.connect(input)
        val outputData1 = output1.getValues()
        val outputData2 = output2.getValues()
        outputData1.fill(20.0)
        outputData2.fill(5.67)
        input.pullData(0)
        val inputData = input.getValues()
        assertEquals(25.67, inputData[0])
    }
}