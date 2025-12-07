package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort


/**
 * Base class for binary arithmetic operators like Add and Compare.
 *
 * @author Phil Burk (C) 2010 Mobileer Inc
 */
abstract class UnitBinaryOperator : UnitGenerator() {
    var inputA: UnitInputPort
    var inputB: UnitInputPort
    var output: UnitOutputPort

    /* Define Unit Ports used by connect() and set(). */
    init {
        addPort(UnitInputPort("InputA").also { inputA = it })
        addPort(UnitInputPort("InputB").also { inputB = it })
        addPort(UnitOutputPort("Output").also { output = it })
    }

    public abstract override fun generate()
}