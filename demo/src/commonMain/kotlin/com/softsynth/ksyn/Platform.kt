package com.softsynth.ksyn

import androidx.compose.runtime.Composable

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

@Composable
expect fun getPlatformContext(): Any?

/**
 * Open a platform file picker for WAV/AIFF audio files and return `(fileName, bytes)` or `null` if cancelled.
 */
expect suspend fun pickAudioFileBytes(context: Any?): Pair<String, ByteArray>?