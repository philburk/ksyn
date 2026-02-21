package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.AudioSample
import com.softsynth.ksyn.engine.SynthesisEngine
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.ports.UnitPort
import com.softsynth.ksyn.shared.time.TimeStamp
import kotlin.math.pow

/**
 * Base class for all unit generators.
 * Converted to Kotlin Multiplatform.
 */
abstract class UnitGenerator {

    companion object {
        const val VERY_SMALL_FLOAT = 1.0e-26

        // Common port names
        const val PORT_NAME_INPUT = "Input"
        const val PORT_NAME_OUTPUT = "Output"
        const val PORT_NAME_PHASE = "Phase"
        const val PORT_NAME_FREQUENCY = "Frequency"
        const val PORT_NAME_FREQUENCY_SCALER = "FreqScaler"
        const val PORT_NAME_AMPLITUDE = "Amplitude"
        const val PORT_NAME_PAN = "Pan"
        const val PORT_NAME_TIME = "Time"
        const val PORT_NAME_CUTOFF = "Cutoff"
        const val PORT_NAME_PRESSURE = "Pressure"
        const val PORT_NAME_TIMBRE = "Timbre"

        const val FALSE = 0.0
        const val TRUE = 1.0

        // Global ID counter
        private var nextId = 0
    }

    val id: Int = nextId++

    // In Kotlin, MutableMap preserves insertion order (like LinkedHashMap)
    private val ports: MutableMap<String, UnitPort> = mutableMapOf()

    var synthesisEngine: SynthesisEngine? = null
        get() = field
        set(value) {
            if (field != null && field !== value) {
                throw RuntimeException("Unit synthesisEngine already set.")
            }
            field = value
        }

    // In KSyn, Circuit would likely be another UnitGenerator subclass container
    var circuit: UnitGenerator? = null
        private set

    private var lastFrameCount: Long = -1
    var isEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) flattenOutputs()
        }

    val frameRate: Double
        get() = synthesisEngine?.frameRate?.toDouble() ?: 44100.0

    val framePeriod: Double
        get() = 1.0 / frameRate

    /**
     * Perform essential synthesis function.
     * Optimized for SIMD: Always processes KSYN_BLOCK_SIZE samples.
     */
    abstract fun generate()

    fun addPort(port: UnitPort, name: String? = null) {
        port.unitGenerator = this
        val portName = name ?: port.name
        if (name != null) port.name = name

        // Store in map by lowercase name for case-insensitive lookup
        ports[portName.lowercase()] = port
    }

    fun getPortByName(portName: String): UnitPort? {
        return ports[portName.lowercase()]
    }

    fun getPorts(): Collection<UnitPort> {
        return ports.values
    }

    /**
     * Climb to top of circuit hierarchy.
     */
    val topUnit: UnitGenerator
        get() {
            var unit = this
            while (unit.circuit != null) {
                unit = unit.circuit!!
            }
            return unit
        }

    protected fun autoStop() {
        // TODO: Implement auto-stop in SynthesisEngine
        // synthesisEngine?.autoStopUnit(topUnit)
    }

    /** Calculate signal based on halflife of an exponential decay. */
    fun convertHalfLifeToMultiplier(halfLife: Double): Double {
        return if (halfLife < (2.0 * framePeriod)) {
            1.0
        } else {
            1.0 - 0.5.pow(1.0 / (halfLife * frameRate))
        }
    }

    protected fun incrementWrapPhase(phase: Double, increment: Double): Double {
        var currentPhase = phase + increment
        if (currentPhase >= 1.0) {
            currentPhase -= 2.0
        } else if (currentPhase < -1.0) {
            currentPhase += 2.0
        }
        return currentPhase
    }

    /** Calculate rate based on phase going from 0.0 to 1.0 in time. */
    protected fun convertTimeToRate(time: Double): Double {
        val inverseNyquist = 2.0 / frameRate
        return if (time < inverseNyquist) {
            1.0
        } else {
            framePeriod / time
        }
    }

    /** Flatten output ports so we don't output a changing signal when stopped. */
    fun flattenOutputs() {
        for (port in ports.values) {
            if (port is UnitOutputPort) {
                port.flatten()
            }
        }
    }

    fun setCircuit(circuit: UnitGenerator) {
        if (this.circuit != null && this.circuit !== circuit) {
            throw RuntimeException("Unit is already in a circuit.")
        }
        this.circuit = circuit
    }

    /**
     * The Pull Architecture Logic.
     * Recursively pulls data from upstream units.
     *
     * @param frameCount Current frame count, used to block infinite recursion in cyclic graphs.
     */
    fun pullData(frameCount: Long) {
        // Don't generate twice in case the paths merge (Diamond Graph problem)
        if (isEnabled && frameCount > lastFrameCount) {
            // Block recursion for feedback loops
            lastFrameCount = frameCount

            // 1. Pull from upstream
            for (port in ports.values) {
                if (port is UnitInputPort) {
                    // Assuming UnitInputPort has a pullData logic or connects to an Output that has a Unit
                    port.pullData(frameCount)
                }
            }

            // 2. Generate THIS unit's block
            generate()
        }
    }

    /**
     * Some units, for example LineOut, will only work if started explicitly.
     */
    open val isStartRequired: Boolean
        get() = false

    fun start() {
        val engine = synthesisEngine
            ?: throw RuntimeException("This ${this::class.simpleName} was not added to a SynthesisEngine.")

        engine.startUnit(this)
    }

    fun start(time: Double) {
        // Queue implementation would go here
        start(TimeStamp(time))
    }
    fun start(timeStamp: TimeStamp) {
        val engine = synthesisEngine
            ?: throw RuntimeException("This ${this::class.simpleName} was not added to a SynthesisEngine.")
        engine.startUnit(this, timeStamp)
    }

    fun stop() {
        val engine = synthesisEngine
            ?: throw RuntimeException("This ${this::class.simpleName} was not added to a SynthesisEngine.")
        engine.stopUnit(this)
    }

    fun stop(time: Double) {
        stop(TimeStamp(time))
    }

    fun stop(timeStamp: TimeStamp) {
        val engine = synthesisEngine
            ?: throw RuntimeException("This ${this::class.simpleName} was not added to a SynthesisEngine.")
        engine.stopUnit(this, timeStamp)
    }

    /** Needed by UnitSink */
    fun getUnitGenerator(): UnitGenerator = this

    /** Needed by UnitVoice */
    fun setPort(portName: String, value: AudioSample, timeStamp: TimeStamp) {
        (getPortByName(portName) as? UnitInputPort)?.let { port ->
            port.set(0, value, timeStamp.time)
        }
    }

    fun printConnections(level: Int = 0) {
        for (port in getPorts()) {
            if (port is UnitInputPort) {
                // In KMP we use standard println
                // Indent based on level
                val indent = "  ".repeat(level)
                println("$indent Port: ${port.name}")
                port.printConnections(level + 1)
            }
        }
    }

    override fun toString(): String {
        return "UnitGenerator(id=$id, type=${this::class.simpleName})"
    }
}
