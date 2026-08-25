package com.softsynth.ksyn

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val rootElement = document.getElementById("composeRoot") ?: document.body!!
    ComposeViewport(rootElement) {
        App()
    }
}