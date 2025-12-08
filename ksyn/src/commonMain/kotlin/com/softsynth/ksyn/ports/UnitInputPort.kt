package com.softsynth.ksyn.ports

import com.softsynth.ksyn.ports.InputMixingBlockPart
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.UnitGenerator

/**
 * A port that is used to pass values into a UnitGenerator.
 *
 * Converted to Kotlin Multiplatform.
 */
class UnitInputPort(
    numParts: Int = 1,
    name: String = "Input",
    defaultValue: Double = 0.0
) : UnitBlockPort(numParts, name, defaultValue), ConnectableInput, SettablePort {

    var minimum: Double = 0.0
    var maximum: Double = 1.0

    // In Kotlin, 'defaultValue' overrides the property if defined in base,
    // or is a new property here. Renaming to avoid confusion with constructor arg.
    private var _defaultValue: Double = defaultValue

    // Stores the values set via set(), separate from the calculated/mixed audio values.
    private val setValues: DoubleArray = DoubleArray(numParts) { defaultValue }

    var isValueAdded: Boolean = false

    // Secondary constructors for convenience
    constructor(name: String, defaultValue: Double) : this(1, name, defaultValue)
    constructor(name: String) : this(1, name, 0.0)
    constructor(numParts: Int, name: String) : this(numParts, name, 0.0)


    /**
     * Override the factory method from UnitBlockPort to create InputMixingBlockParts.
     * (Replaces Java's makeParts())
     */
    override fun createPart(index: Int, defaultValue: Double): PortBlockPart {
        return InputMixingBlockPart(this, defaultValue)
    }

    /**
     * This is used internally by the SynthesisEngine to execute units based on their connections.
     * KSyn Update: No start/limit arguments (SIMD architecture).
     *
     * @param frameCount Current frame count. Only used to block recursion in cyclic graphs.
     */
    override fun pullData(frameCount: Long) {
        for (part in parts) {
            // Safe cast because we know createPart returns InputMixingBlockPart
            (part as InputMixingBlockPart).pullData(frameCount)
        }
    }

    override fun setValueInternal(partNum: Int, value: Double) {
        super.setValueInternal(partNum, value)
        setValues[partNum] = value
    }

    // ==========================================
    // Setters
    // ==========================================

    fun set(value: Double) {
        set(0, value)
    }

    override fun set(partNum: Int, value: Double, timeStamp: TimeStamp) {
        set(partNum, value, timeStamp.time)
    }

    fun set(partNum: Int, value: Double) {
        // Immediate update of local storage
        setValues[partNum] = value

        // Queue the actual DSP update safely
        queueCommand {
            setValueInternal(partNum, value)
        }
    }

    fun set(value: Double, time: Double) {
        set(0, value, time)
    }
    fun set(value: Double, timeStamp: TimeStamp) {
        set(0, value, timeStamp)
    }

    fun set(partNum: Int, value: Double, time: Double) {
        // Check range or other logic here if needed (e.g. getValue(partNum))
        scheduleCommand(time) {
            setValueInternal(partNum, value)
        }
    }

    // ==========================================
    // Getters & Properties
    // ==========================================

    override fun get(partNum: Int): Double {
        return setValues[partNum]
    }

    var defaultValue: Double
        get() = _defaultValue
        set(value) {
            _defaultValue = value
        }

    /**
     * Convenience function for setting limits on a port.
     */
    fun setup(minimum: Double, value: Double, maximum: Double) {
        this.minimum = minimum
        this.maximum = maximum
        this.defaultValue = value
        set(value)
    }

    fun setup(other: UnitInputPort) {
        setup(other.minimum, other.defaultValue, other.maximum)
    }

    // ==========================================
    // Connections
    // ==========================================

    fun connect(thisPartNum: Int, otherPort: UnitOutputPort, otherPartNum: Int, timeStamp: Double) {
        otherPort.connect(otherPartNum, this, thisPartNum, timeStamp)
    }

    fun connect(thisPartNum: Int, otherPort: UnitOutputPort, otherPartNum: Int) {
        otherPort.connect(otherPartNum, this, thisPartNum)
    }

    fun connect(otherPort: UnitOutputPort) {
        connect(0, otherPort, 0)
    }

    override fun connect(other: ConnectableOutput) {
        other.connect(this)
    }

    fun disconnect(thisPartNum: Int, otherPort: UnitOutputPort, otherPartNum: Int) {
        otherPort.disconnect(otherPartNum, this, thisPartNum)
    }

    override fun disconnect(other: ConnectableOutput) {
        other.disconnect(this)
    }

    // ==========================================
    // Interface Implementations
    // ==========================================

    override val portBlockPart: PortBlockPart
        get() = parts[0]

    fun getConnectablePart(i: Int): ConnectableInput {
        return parts[i] as ConnectableInput
    }

    fun printConnections(level: Int = 0) {
        // We generally use standard IO, but could pass a printer if needed
        for (part in parts) {
            (part as InputMixingBlockPart).printConnections(level)
        }
    }
}
