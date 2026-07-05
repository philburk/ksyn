package com.softsynth.ksyn.util

import com.softsynth.ksyn.unitgen.UnitVoice

/**
* @deprecated Use classes in com.softsynth.ksyn.voices instead
*/
fun interface VoiceOperation {
    fun operate(voice: UnitVoice)
}
