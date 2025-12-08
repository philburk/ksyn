package com.softsynth.ksyn.ports

/**
 * A port that contains multiple parts with blocks of data.
 */
open class UnitBlockPort(
    numParts: Int = 1,
    name: String,
    defaultValue: Double = 0.0
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
    protected open fun createPart(index: Int, defaultValue: Double): PortBlockPart {
        return PortBlockPart(this, defaultValue)
    }

    override var value: Double
        set(value: Double)  { setValueInternal(0, value) }
        get() = get(0)

    fun getValue(partNum: Int): Double = parts[partNum].getValue()

    fun getValues(partNum: Int): DoubleArray = parts[partNum].getValues()

    fun getValues(): DoubleArray = getValues(0)

    open fun get(partNum: Int): Double = parts[partNum].get()

    open fun setValueInternal(partNum: Int, value: Double) {
        parts[partNum].setValue(value)
    }

    fun setValueInternal(value: Double) {
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
