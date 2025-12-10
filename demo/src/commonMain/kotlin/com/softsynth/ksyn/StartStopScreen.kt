package com.softsynth.ksyn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mobileer.audiobridge.AudioResult

interface KSynPlayable {
    fun start(): AudioResult
    fun stop()
}

open class StartStopScreen(val playable: KSynPlayable) : Screen {

    @Composable
    override fun Content() {
        var isPlaying by remember { mutableStateOf(false) }
        val navigator = LocalNavigator.currentOrThrow
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Button(
                    onClick = {
                        // Pop this screen off the stack (Go Back)
                        navigator.pop()
                    }
                ) {
                    Text("Audio Bridge Active (Go Back)")
                }

                Text("Play KSyn SawtoothOscillator")

                Row {
                    Button(
                        onClick = {
                            val result = playable.start()
                            if (result != AudioResult.OK) {
                                println("Failed to open audio bridge: $result")
                                // Handle error, maybe show a message to the user
                                return@Button
                            }
                            isPlaying = true
                        },
                        enabled = !isPlaying
                    ) {
                        Text("START")
                    }

                    Button(
                        onClick = {
                            playable.stop()
                            isPlaying = false
                        },
                        enabled = isPlaying
                    ) {
                        Text("STOP")
                    }
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    // Cleanup when the Composable leaves the composition
                    playable.stop()
                }
            }
        }
    }
}
