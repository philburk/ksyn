package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.shared.time.TimeStamp


/**
 * A voice that can be allocated and played by the VoiceAllocator.
 *
 * @author Phil Burk (C) 2011 Mobileer Inc
 * @see VoiceDescription
 *
 * @see Instrument
 */
interface UnitVoice : UnitSource {
    /**
     * Play whatever you consider to be a note on this voice. Do not be constrained by traditional
     * definitions of notes or music.
     *
     * @param frequency in Hz related to the perceived pitch of the note.
     * @param amplitude generally between 0.0 and 1.0
     * @param timeStamp when to play the note
     */
    fun noteOn(frequency: Double, amplitude: Double, timeStamp: TimeStamp)

    fun noteOff(timeStamp: TimeStamp)

    /**
     * Looks up a port using its name and sets the value.
     *
     * @param portName
     * @param value
     * @param timeStamp
     */
    fun setPort(portName: String, value: Double, timeStamp: TimeStamp)

    fun usePreset(presetIndex: Int)
}