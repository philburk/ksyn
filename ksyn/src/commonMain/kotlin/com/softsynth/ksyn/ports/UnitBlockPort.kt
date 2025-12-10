package com.softsynth.ksyn.ports

import com.softsynth.ksyn.AudioBuffer
import com.softsynth.ksyn.AudioSample
import com.softsynth.ksyn.toSample

/**
 * A port that contains multiple parts with blocks of data.
 */
open class UnitBlockPort(
    numParts: Int = 1,
    name: String,
    defaultValue: AudioSample = 0.0.toSample()
) : UnitPort(name) {

    // We use 'createPart' so subclasses (like UnitInputPort) can instantiate specialized parts.
    val parts: Array<PortBlockPart> = Array(numParts) { i ->
        createPart(i, defaultValue)
    }

    override val numParts: Int
        get() = parts.size

    /**
     * Factory method for creating parts.
     * Subclasses should override this to return their specific Part type.
     */
    protected open fun createPart(index: Int, defaultValue: AudioSample): PortBlockPart {
        return PortBlockPart(this, defaultValue)
    }

    override var value: AudioSample
        set(value: AudioSample)  { setValueInternal(0, value) }
        get() = get(0)

    fun getValue(partNum: Int): AudioSample = parts[partNum].getValue()

    fun getValues(partNum: Int): AudioBuffer = parts[partNum].getValues()

    fun getValues(): AudioBuffer = getValues(0)

    open fun get(partNum: Int): AudioSample = parts[partNum].get()

    open fun setValueInternal(partNum: Int, value: AudioSample) {
        parts[partNum].setValue(value)
    }

    fun setValueInternal(value: AudioSample) {
        setValueInternal(0, value)
    }

    fun isConnected(): Boolean = isConnected(0)

    fun isConnected(partNum: Int): Boolean = parts[partNum].isConnected

    fun disconnectAll(partNum: Int) {
        parts[partNum].disconnectAll()
    }

    fun disconnectAll() {
        disconnectAll(0)
    }
}
