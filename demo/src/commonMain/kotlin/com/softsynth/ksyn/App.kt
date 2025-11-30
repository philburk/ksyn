package com.softsynth.ksyn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ksyn_project.demo.generated.resources.Res
import ksyn_project.demo.generated.resources.compose_multiplatform
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import com.softsynth.ksyn.Oscillator;
import com.softsynth.ksyn.math.PrimeFactors

@Composable
@Preview
fun App() {
    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. Create an instance of your library class
            // (If you haven't created Oscillator.kt yet, create it in ksyn/src/commonMain/kotlin/...)
            val osc = com.softsynth.ksyn.Oscillator()

            // 2. Call the math function
            val value = osc.getSine(0.37)

            // 3. Display it
            Text("Sine Output: $value")
            Text("8 db = ${com.softsynth.ksyn.math.decibelsToAmplitude(8.0)}")
            val factors = PrimeFactors(20, 24)
            Text("Prime factors of 20/24 are ${factors}")
        }
    }
}