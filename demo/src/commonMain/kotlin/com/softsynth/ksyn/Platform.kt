package com.softsynth.ksyn

import androidx.compose.runtime.Composable

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

@Composable
expect fun getPlatformContext(): Any?