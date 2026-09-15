package com.softsynth.ksyn.instruments

import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.*
import com.softsynth.ksyn.voices.PitchedVoice
import com.softsynth.ksyn.voices.VoiceDescription

/**
 * Exported from Syntona patch: polyPatch
 *
 * This code was exported from Syntona. Rather then editing this file, you may want to
 * modify the Syntona patch and then re-export this source code.
 */
class PanningSaw : Circuit(), PitchedVoice {
    private val sawOscBL = SawtoothOscillatorBL()
    private val envDAHDSR = EnvelopeDAHDSR()
    private val pitchToHz = PitchToFrequency()
    private val pan = Pan()
    private val sineOsc = SineOscillator()
    private val twoInDualOut = TwoInDualOut()

    val pitch: UnitInputPort
    val amplitude: UnitInputPort

    init {
        add(sawOscBL)
        add(envDAHDSR)
        add(pitchToHz)
        add(pan)
        add(sineOsc)
        add(twoInDualOut)

        // Wire connections
        sawOscBL.output.connect(envDAHDSR.amplitude)
        pitchToHz.output.connect(sawOscBL.frequency)
        envDAHDSR.output.connect(pan.input)
        sineOsc.output.connect(pan.pan)
        pan.output.connect(twoInDualOut.inputA)
        pan.output.connect(1, twoInDualOut.inputB, 0)

        pitch = pitchToHz.input
        pitch.isValueAdded = true
        addPort(pitch, "Pitch")

        amplitude = sawOscBL.amplitude
        addPort(amplitude, "Amplitude")

        envDAHDSR.export(this, "Env")

        // Port limits and default values
        sawOscBL.frequency.setup(40.0, 440.0, 8000.0)
        sawOscBL.amplitude.setup(0.0, 1.0, 1.0)
        envDAHDSR.input.setup(0.0, 0.0, 1.0)
        envDAHDSR.delay.setup(0.0, 0.0, 2.0)
        envDAHDSR.attack.setup(0.0, 0.01, 1.0)
        envDAHDSR.hold.setup(0.0, 0.0, 1.0)
        envDAHDSR.decay.setup(0.0, 0.2, 1.0)
        envDAHDSR.sustain.setup(0.0, 0.5, 1.0)
        envDAHDSR.release.setup(0.0, 1.0, 15.0)
        envDAHDSR.amplitude.setup(0.0, 1.0, 1.0)
        pitchToHz.input.setup(0.0, 60.0, 127.0)
        pan.input.setup(0.0, 0.0, 1.0)
        pan.pan.setup(-1.0, 0.0, 1.0)
        sineOsc.frequency.setup(0.1, 0.694605, 8.0)
        sineOsc.amplitude.setup(-1.0, 1.0, 1.0)

        // Turn off circuit when envelope completes to save CPU
        envDAHDSR.setupAutoDisable(this)

        usePreset(0)
    }

    override fun noteOff(timeStamp: TimeStamp) {
        envDAHDSR.input.off(timeStamp)
    }

    override fun noteOn(pitch: Double, amplitude: Double, timeStamp: TimeStamp) {
        pitchToHz.input.set(pitch, timeStamp)
        this.amplitude.set(amplitude, timeStamp)
        envDAHDSR.input.on(timeStamp)
    }

    override fun getOutputPort(): UnitOutputPort {
        return twoInDualOut.output
    }

    override fun getPitchPort(): UnitInputPort? {
        return pitch
    }

    override fun usePreset(presetIndex: Int) {
    }

    companion object {
        val presetNames = arrayOf("Default")

        fun getVoiceDescription(): VoiceDescription {
            return MyVoiceDescription
        }
    }

    private object MyVoiceDescription : VoiceDescription("PanningSaw", presetNames) {
        override val voiceClassName: String
            get() = "com.softsynth.ksyn.instruments.PanningSaw"

        private val tags = arrayOf("electronic")

        override fun createPitchedVoice(): PitchedVoice {
            return PanningSaw()
        }

        override fun getTags(presetIndex: Int): Array<String> {
            return tags
        }
    }
}
