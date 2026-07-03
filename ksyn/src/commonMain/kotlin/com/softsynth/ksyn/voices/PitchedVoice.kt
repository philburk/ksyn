package com.softsynth.ksyn.voices

import com.softsynth.ksyn.AudioSample
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.UnitSource

/**
 * A voice that can be allocated and played by a ??.
 */
interface PitchedVoice : UnitSource {
    /**
     * Play whatever you consider to be a note on this voice. Do not be constrained by traditional
     * definitions of notes or music.
     *
     * This call may trigger envelopes or samples.
     *
     * @param pitch in semitones where middle C is 60.0
     * @param amplitude generally between 0.0 and 1.0
     * @param timeStamp when to play the note
     */
    fun noteOn(pitch: Double, amplitude: Double, timeStamp: TimeStamp)

    /**
     * This is the equivalent of releasing a key on a piano.
     * The sound should continue and finish on its own.
     *
     * @param timeStamp when to finish the note
     */
    fun noteOff(timeStamp: TimeStamp)

    /**
     * Looks up a port using its name and sets the value.
     *
     * @param portName
     * @param value
     * @param timeStamp
     */
    fun setPort(portName: String, value: AudioSample, timeStamp: TimeStamp)

    fun usePreset(presetIndex: Int)
}