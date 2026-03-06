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
import com.softsynth.ksyn.compose.UnitGeneratorFaders

open class AutoStartScreen(
    val playable: KSynPlayable,
    val title: String = "Use faders to adjust sound."
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
                    Text("Go Back")
                }

                Text(title)
                val unitGenerator = playable.getUnitGenerator()
                if (unitGenerator != null) {
                    UnitGeneratorFaders(unitGenerator = unitGenerator)
                }
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
