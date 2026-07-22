package com.softsynth.ksyn

import androidx.compose.runtime.Composable

class JsPlatform: Platform {
    override val name: String = "Web with Kotlin/JS"
}

actual fun getPlatform(): Platform = JsPlatform()

@Composable
actual fun getPlatformContext(): Any? = null