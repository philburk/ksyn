package com.softsynth.ksyn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

open class AutoStartScreen(
    val playable: KSynPlayable,
    val title: String = "Go Back",
    val customContent: @Composable () -> Unit = {}
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Button(
                    onClick = {
                        navigator.pop()
                    }
                ) {
                    Text(title)
                }

                customContent()
            }

            DisposableEffect(Unit) {
                // Auto-start when the screen is composed
                val result = playable.start()
                if (result != com.mobileer.audiobridge.AudioResult.OK) {
                    println("Failed to open audio bridge: $result")
                }

                onDispose {
                    // Cleanup when the Composable leaves the composition (e.g., navigating back)
                    playable.stop()
                }
            }
        }
    }
}
