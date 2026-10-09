/*
 * Copyright 2026 Phil Burk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.softsynth.ksyn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.softsynth.audiobridge.AudioResult
import com.softsynth.ksyn.compose.Oscilloscope
import com.softsynth.ksyn.compose.PortFader
import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.dsp.OnePoleLowPass
import com.softsynth.ksyn.unitgen.FixedRateMonoReader
import com.softsynth.ksyn.unitgen.FixedStringResonator
import com.softsynth.ksyn.unitgen.LineOut
import com.softsynth.ksyn.unitgen.PassThrough
import com.softsynth.ksyn.unitgen.PitchDetector
import com.softsynth.ksyn.unitgen.ScopeProbe
import com.softsynth.ksyn.unitgen.StringResonator
import com.softsynth.ksyn.unitgen.VariableStringResonator
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.random.Random

class PluckedStringPlayer : KSynPlayable() {
    val synth = KSyn.createSynthesizer()
    private val ksynAudioBridge = KSynAudioBridge(synth)
    private val lineOut = LineOut()
    val scope = ScopeProbe(numChannels = 1)
    val pitchDetector = PitchDetector()

    // Each control fans out to both resonators.
    val frequencyControl = PassThrough()
    val decayControl = PassThrough()
    val brightnessControl = PassThrough()

    private val fixedResonator = FixedStringResonator()
    private val variableResonator = VariableStringResonator()
    private val fixedExciter = FixedRateMonoReader()
    private val variableExciter = FixedRateMonoReader()

    /** Pluck the VariableStringResonator if true, else the FixedStringResonator. */
    var useVariable by mutableStateOf(true)

    /** Where the string is plucked, as a fraction of its length from the bridge. 0.5 is the middle. */
    var pluckPosition by mutableStateOf(0.2f)

    /** 0.0 is a hard pick, 1.0 is a soft fingertip. */
    var pluckSoftness by mutableStateOf(0.0f)

    /** Length of the noise burst in milliseconds. It is never longer than one period. */
    var pluckLengthMillis by mutableStateOf(1.0f)

    init {
        for (unit in listOf(lineOut, scope, pitchDetector,
                frequencyControl, decayControl, brightnessControl,
                fixedResonator, variableResonator, fixedExciter, variableExciter)) {
            synth.add(unit)
        }

        frequencyControl.input.set(220.0)
        decayControl.input.set(3.0)
        brightnessControl.input.set(0.5)

        connectResonator(fixedResonator, fixedExciter)
        connectResonator(variableResonator, variableExciter)

        lineOut.start()
        scope.start()
        pitchDetector.start()
    }

    private fun connectResonator(resonator: StringResonator, exciter: FixedRateMonoReader) {
        frequencyControl.output.connect(resonator.frequency)
        decayControl.output.connect(resonator.decay)
        brightnessControl.output.connect(resonator.brightness)
        exciter.output.connect(resonator.input)
        resonator.output.connect(0, lineOut.input, 0)
        resonator.output.connect(0, lineOut.input, 1)
        resonator.output.connect(scope.input)
        resonator.output.connect(pitchDetector.input)
        // Turn off the resonator when the string is silent. pluck() turns it back on.
        resonator.setupAutoDisable()
    }

    /**
     * A short burst of noise, shaped by the pluck controls.
     * The classic Karplus-Strong excitation is a whole period of noise, which sounds long and scrapey
     * on low notes, so the burst is shorter and faded in and out with a Hann window.
     * Plucking at a fraction of the string length removes the harmonics that have a node there.
     * That is done with a comb filter, which subtracts a copy delayed by that fraction of a period.
     * Softness is a low pass filter, like the flesh of a fingertip compared with a pick.
     * Its cutoff goes from 64 times the frequency for a hard pick down to the frequency itself.
     */
    private fun makeBurst(frequency: Double): FloatSample {
        val frameRate = synth.frameRate.toDouble()
        val period = (frameRate / frequency).toInt().coerceAtLeast(1)
        val burstLength = (pluckLengthMillis * 0.001 * frameRate).toInt().coerceIn(1, period)
        val noise = FloatArray(burstLength) { n ->
            val window = 0.5 - (0.5 * cos(2.0 * PI * (n + 1) / (burstLength + 1)))
            ((Random.nextFloat() - 0.5f) * window).toFloat()
        }

        // The comb subtracts a copy of the burst from itself, so the result has no DC.
        val combDelay = (pluckPosition * period).toInt().coerceAtLeast(1)
        // Leave room for the comb copy and the tail of the softness filter.
        val data = FloatArray(burstLength + combDelay + (period / 2)) { n ->
            val direct = if (n < burstLength) noise[n] else 0.0f
            val delayedIndex = n - combDelay
            val delayed = if (delayedIndex in 0 until burstLength) noise[delayedIndex] else 0.0f
            direct - delayed
        }

        val softener = OnePoleLowPass()
        val cutoffRatio = MAX_SOFTNESS_CUTOFF_RATIO.pow(1.0 - pluckSoftness)
        softener.setCutoff(frequency * cutoffRatio, synth.frameRate.toDouble())
        var peak = 0.0f
        for (n in data.indices) {
            data[n] = softener.process(data[n])
            peak = maxOf(peak, abs(data[n]))
        }

        // The filters change the level a lot, so normalize it.
        if (peak > 0.0f) {
            val scale = BURST_PEAK / peak
            for (n in data.indices) data[n] *= scale
        }
        return FloatSample(data)
    }

    fun pluck() {
        val resonator = if (useVariable) variableResonator else fixedResonator
        val exciter = if (useVariable) variableExciter else fixedExciter
        val burst = makeBurst(frequencyControl.input.get(0).toDouble())
        synth.queueCommand {
            resonator.isEnabled = true
            exciter.dataQueue.queue(burst)
        }
    }

    override fun start(): AudioResult {
        return ksynAudioBridge.start()
    }

    override fun stop() {
        ksynAudioBridge.stop()
    }

    override fun getScopeProbe(): ScopeProbe = scope

    companion object {
        private const val BURST_PEAK = 0.3f

        /** Cutoff of the softness filter, relative to the frequency, for a hard pick. */
        private const val MAX_SOFTNESS_CUTOFF_RATIO = 64.0
    }
}

private fun roundTo(value: Double, places: Int): Double {
    var scale = 1.0
    repeat(places) { scale *= 10.0 }
    return round(value * scale) / scale
}

/**
 * A slider for a value that is not a port.
 * An exponential slider needs a range above zero.
 */
@Composable
private fun ValueSlider(name: String, value: Float, range: ClosedFloatingPointRange<Float>,
                        isExponential: Boolean = false,
                        onValueChange: (Float) -> Unit) {
    val min = range.start.toDouble()
    val max = range.endInclusive.toDouble()
    // Slider position from 0.0 to 1.0.
    val position = if (isExponential) {
        ln(value / min) / ln(max / min)
    } else {
        (value - min) / (max - min)
    }
    Row(
        modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$name:", modifier = Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = position.toFloat().coerceIn(0.0f, 1.0f),
            onValueChange = { newPosition ->
                val newValue = if (isExponential) {
                    min * exp(newPosition * ln(max / min))
                } else {
                    min + (newPosition * (max - min))
                }
                onValueChange(newValue.toFloat())
            },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text(
            roundTo(value.toDouble(), 3).toString(),
            modifier = Modifier.width(60.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

class PluckedStringScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val player = remember { PluckedStringPlayer() }
        var pitchText by remember { mutableStateOf("Detected: -") }
        var isConfident by remember { mutableStateOf(false) }

        // Poll the PitchDetector and compare it with the requested frequency.
        // Keep showing the last confident result, grayed out, when the confidence drops.
        LaunchedEffect(player) {
            while (true) {
                delay(100)
                val confidence = player.pitchDetector.confidence.value.toDouble()
                isConfident = confidence > 0.5
                if (isConfident) {
                    val detected = player.pitchDetector.frequency.value.toDouble()
                    val requested = player.frequencyControl.input.get(0).toDouble()
                    val cents = 1200.0 * log2(detected / requested)
                    pitchText = "Detected: ${roundTo(detected, 2)} Hz, error ${roundTo(cents, 1)} cents, " +
                            "confidence ${roundTo(confidence, 2)}"
                }
            }
        }

        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                Button(onClick = { navigator.pop() }) {
                    Text("Go Back")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Plucked String (Karplus-Strong)", style = MaterialTheme.typography.titleMedium)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = player.useVariable, onCheckedChange = { player.useVariable = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (player.useVariable) "VariableStringResonator (Lagrange, bends smoothly)"
                        else "FixedStringResonator (allpass, fixed pitch)"
                    )
                }

                PortFader(player.frequencyControl.input, 40.0f, 2000.0f, isExponential = true, name = "Frequency")
                PortFader(player.decayControl.input, 0.1f, 20.0f, isExponential = true, name = "Decay")
                PortFader(player.brightnessControl.input, 0.0f, 1.0f, name = "Brightness")
                ValueSlider("Pluck Position", player.pluckPosition, 0.02f..0.5f) {
                    player.pluckPosition = it
                }
                ValueSlider("Pluck Softness", player.pluckSoftness, 0.0f..1.0f) {
                    player.pluckSoftness = it
                }
                ValueSlider("Pluck Length ms", player.pluckLengthMillis, 0.05f..20.0f, isExponential = true) {
                    player.pluckLengthMillis = it
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { player.pluck() }) {
                    Text("Pluck")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    pitchText,
                    color = if (isConfident) MaterialTheme.colorScheme.primary else Color.Gray
                )

                Oscilloscope(
                    probe = player.scope,
                    modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = 8.dp),
                )
            }
        }

        DisposableEffect(Unit) {
            val result = player.start()
            if (result != AudioResult.OK) {
                println("Failed to open audio bridge: $result")
            }
            onDispose {
                player.stop()
            }
        }
    }
}
