package com.softsynth.ksyn.voices

import com.softsynth.ksyn.voices.PitchedVoice

fun interface VoiceOperation {
    fun operate(voice: PitchedVoice)
}
