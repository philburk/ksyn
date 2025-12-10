package com.softsynth.ksyn.ports

import com.softsynth.ksyn.AudioBuffer
import com.softsynth.ksyn.AudioSample
import com.softsynth.ksyn.KSyn
import com.softsynth.ksyn.ports.InputMixingBlockPart
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.toSample
import com.softsynth.ksyn.unitgen.UnitGenerator

/**
 * A port that is used to pass values into a UnitGenerator.
 *
 * Converted to Kotlin Multiplatform.
 */
class UnitInputPort(
    numParts: Int = 1,
    name: String = "Input",
    defaultValue: AudioSample = KSyn.ZERO
) : UnitBlockPort(numParts, name, defaultValue), ConnectableInput, SettablePort {

    var minimum: AudioSample = KSyn.ZERO
    var maximum: AudioSample = KSyn.ONE

    // In Kotlin, 'defaultValue' overrides the property if defined in base,
    // or is a new property here. Renaming to avoid confusion with constructor arg.
    private var _defaultValue: AudioSample = defaultValue

    // Stores the values set via set(), separate from the calculated/mixed audio values.
    private val setValues = AudioBuffer(numParts) { defaultValue }

    var isValueAdded: Boolean = false

    // Secondary constructors for convenience
    constructor(name: String) : this(1, name, KSyn.ZERO)
    constructor(name: String, defaultValue: Double) : this(1, name, defaultValue.toSample())
    constructor(name: String, defaultValue: AudioSample) : this(1, name, defaultValue)
    constructor(numParts: Int, name: String) : this(numParts, name, KSyn.ZERO)

    /**
     * Override the factory method from UnitBlockPort to create InputMixingBlockParts.
     * (Replaces Java's makeParts())
     */
    override fun createPart(index: Int, defaultValue: AudioSample): PortBlockPart {
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

    override fun setValueInternal(partNum: Int, value: AudioSample) {
        super.setValueInternal(partNum, value)
        setValues[partNum] = value
    }

    // ==========================================
    // Setters
    // ==========================================

    fun set(value: Float) {
        set(0, value.toSample())
    }

    fun set(value: Double) {
        set(0, value.toSample())
    }

    override fun set(partNum: Int, value: AudioSample, timeStamp: TimeStamp) {
        set(partNum, value, timeStamp.time)
    }

    fun set(partNum: Int, value: AudioSample) {
        // Immediate update of local storage
        setValues[partNum] = value

        // Queue the actual DSP update safely
        queueCommand {
            setValueInternal(partNum, value)
        }
    }

    fun set(value: AudioSample, time: Double) {
        set(0, value, time)
    }

    fun set(value: Float, timeStamp: TimeStamp) {
        set(0, value.toSample(), timeStamp)
    }

    fun set(value: Double, timeStamp: TimeStamp) {
        set(0, value.toSample(), timeStamp)
    }

    fun set(partNum: Int, value: AudioSample, time: Double) {
        // Check range or other logic here if needed (e.g. getValue(partNum))
        scheduleCommand(time) {
            setValueInternal(partNum, value)
        }
    }

    // ==========================================
    // Getters & Properties
    // ==========================================

    override fun get(partNum: Int): AudioSample {
        return setValues[partNum]
    }

    var defaultValue: AudioSample
        get() = _defaultValue
        set(value) {
            _defaultValue = value
        }

    /**
     * Convenience functions for setting limits on a port.
     */
    fun setup(minimum: Float, value: Float, maximum: Float) {
        this.minimum = minimum.toSample()
        this.maximum = maximum.toSample()
        this.defaultValue = value.toSample()
        set(value)
    }
    fun setup(minimum: Double, value: Double, maximum: Double) {
        this.minimum = minimum.toSample()
        this.maximum = maximum.toSample()
        this.defaultValue = value.toSample()
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
