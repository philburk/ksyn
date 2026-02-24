package com.softsynth.ksyn.util

import com.softsynth.ksyn.unitgen.UnitVoice

fun interface VoiceOperation {
    fun operate(voice: UnitVoice)
}
