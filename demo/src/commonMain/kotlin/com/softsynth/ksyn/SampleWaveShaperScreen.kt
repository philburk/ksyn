/*
 * Copyright 2026 Phil Burk, Mobileer Inc
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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.softsynth.audiobridge.AudioResult
import com.softsynth.ksyn.compose.Oscilloscope
import com.softsynth.ksyn.compose.PortFader
import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.unitgen.EnvelopeDAHDSR
import com.softsynth.ksyn.unitgen.LineOut
import com.softsynth.ksyn.unitgen.PassThrough
import com.softsynth.ksyn.unitgen.RedNoise
import com.softsynth.ksyn.unitgen.SampleScrubber
import com.softsynth.ksyn.unitgen.ScopeProbe
import com.softsynth.ksyn.unitgen.SineOscillator
import com.softsynth.ksyn.unitgen.TriangleOscillator
import com.softsynth.ksyn.unitgen.UnitOscillator
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.round

enum class ScrubberDriveSignal(val label: String) {
    SINE("Sine"),
    TRIANGLE("Triangle"),
    RED_NOISE("RedNoise")
}

class SampleScrubberPlayer : KSynPlayable() {
    val synth = KSyn.createSynthesizer()
    val ksynAudioBridge = KSynAudioBridge(synth)

    val frequencyControl = PassThrough()
    val amplitudeControl = PassThrough()

    val sineOsc = SineOscillator()
    val triangleOsc = TriangleOscillator()
    val redNoise = RedNoise()

    val sampleScrubber = SampleScrubber()
    val envelope = EnvelopeDAHDSR()
    val lineOut = LineOut()
    val probe = ScopeProbe(numChannels = 1)

    var activeSignal by mutableStateOf(ScrubberDriveSignal.SINE)
        private set

    var sample: FloatSample = createDefaultTestSample()
        private set
    var sampleName by mutableStateOf("Default Harmonic Sweep")

    /** Peak-to-peak number of sample frames swept by the driving oscillator (`N = oscAmplitude * sampleSize`). */
    var sweptFrames by mutableFloatStateOf(400.0f)
        private set

    init {
        synth.add(frequencyControl)
        synth.add(amplitudeControl)
        synth.add(sineOsc)
        synth.add(triangleOsc)
        synth.add(redNoise)
        synth.add(sampleScrubber)
        synth.add(envelope)
        synth.add(lineOut)
        synth.add(probe)

        sampleScrubber.setSample(sample)
        sampleScrubber.position.set(0.0)
        sampleScrubber.time.set(0.03)
        sampleScrubber.amplitude.set(0.9)

        frequencyControl.input.set(110.0)
        updateOscillatorAmplitudeFromSweptFrames(sweptFrames)

        // Fan out frequency and amplitude controls to all three driving oscillators
        for (osc in listOf<UnitOscillator>(sineOsc, triangleOsc, redNoise)) {
            frequencyControl.output.connect(osc.frequency)
            amplitudeControl.output.connect(osc.amplitude)
        }

        // Configure EnvelopeDAHDSR
        envelope.attack.set(0.02)
        envelope.decay.set(0.1)
        envelope.sustain.set(0.9)
        envelope.release.set(0.25)

        // Connect SineOscillator (default) -> SampleScrubber -> EnvelopeDAHDSR.amplitude -> LineOut & Scope
        sineOsc.output.connect(sampleScrubber.input)
        sampleScrubber.output.connect(envelope.amplitude)
        envelope.output.connect(0, lineOut.input, 0)
        envelope.output.connect(0, lineOut.input, 1)
        envelope.output.connect(probe.input)

        lineOut.start()
        probe.start()
    }

    /**
     * Since the sample of size `numFrames` spans 2.0 units (-1.0 to +1.0) and an oscillator of
     * amplitude `A` spans `2 * A` peak-to-peak, the number of samples swept peak-to-peak is:
     * `sweptFrames = (2 * A / 2.0) * numFrames = A * numFrames` -> `A = sweptFrames / numFrames`.
     */
    fun updateOscillatorAmplitudeFromSweptFrames(framesToSweep: Float) {
        sweptFrames = framesToSweep
        val totalFrames = sample.numFrames.coerceAtLeast(1)
        val oscAmp = (framesToSweep.toDouble() / totalFrames.toDouble()).coerceIn(0.0, 1.0)
        amplitudeControl.input.set(oscAmp)
    }

    fun selectDriveSignal(signal: ScrubberDriveSignal) {
        activeSignal = signal
        synth.queueCommand {
            sineOsc.output.disconnectAll()
            triangleOsc.output.disconnectAll()
            redNoise.output.disconnectAll()

            val selectedOsc: UnitOscillator = when (signal) {
                ScrubberDriveSignal.SINE -> sineOsc
                ScrubberDriveSignal.TRIANGLE -> triangleOsc
                ScrubberDriveSignal.RED_NOISE -> redNoise
            }
            selectedOsc.output.connect(sampleScrubber.input)
        }
    }

    fun updateSample(newSample: FloatSample, name: String) {
        val mono = newSample.toMono()
        sample = mono
        sampleName = name
        val clampedSweep = sweptFrames.coerceIn(MIN_SWEPT_FRAMES, mono.numFrames.coerceAtLeast(MIN_SWEPT_FRAMES.toInt()).toFloat())
        updateOscillatorAmplitudeFromSweptFrames(clampedSweep)
        synth.queueCommand {
            sampleScrubber.setSample(mono)
        }
    }

    fun setThereminActive(active: Boolean) {
        synth.queueCommand {
            if (active) {
                envelope.input.on()
            } else {
                envelope.input.off()
            }
        }
    }

    fun setPositionAndRate(position: Double, frequencyHz: Double) {
        sampleScrubber.position.set(position)
        frequencyControl.input.set(frequencyHz)
    }

    override fun start(): AudioResult = ksynAudioBridge.start()

    override fun stop() {
        ksynAudioBridge.stop()
    }

    override fun getScopeProbe(): ScopeProbe = probe

    companion object {
        const val MIN_SWEPT_FRAMES = 32.0f
    }
}

/**
 * Exponential slider for controlling how many sample frames the oscillator sweeps peak-to-peak.
 */
@Composable
private fun SweptSamplesSlider(
    sweptFrames: Float,
    maxFrames: Int,
    onSweptFramesChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val minVal = SampleScrubberPlayer.MIN_SWEPT_FRAMES
    val maxVal = maxFrames.coerceAtLeast(minVal.toInt() + 1).toFloat()
    val clamped = sweptFrames.coerceIn(minVal, maxVal)
    val sliderPos = (ln(clamped / minVal) / ln(maxVal / minVal)).coerceIn(0.0f, 1.0f)

    Row(
        modifier = modifier.padding(vertical = 0.dp, horizontal = 8.dp).fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Sweep (samples):",
            modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.bodySmall
        )
        Slider(
            value = sliderPos,
            onValueChange = { pos ->
                val newFrames = (minVal * exp(pos * ln(maxVal / minVal)))
                onSweptFramesChange(newFrames)
            },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        val display = if (clamped >= 100f) {
            round(clamped).toInt().toString()
        } else {
            (round(clamped * 10f) / 10f).toString()
        }
        Text(
            text = display,
            modifier = Modifier.width(60.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Interactive X/Y Theremin Pad that draws the loaded sample's waveform in the background,
 * highlights the swept sample window around `position`, maps X to `position` (-1.0 to +1.0),
 * maps Y exponentially to oscillator rate/frequency (1 Hz to 1000 Hz),
 * and triggers [onActiveChange] when pressed and released.
 */
@Composable
private fun ThereminXYPad(
    sample: FloatSample,
    position: Float,
    frequency: Float,
    sweptFrames: Float,
    minFreq: Float = 1.0f,
    maxFreq: Float = 1000.0f,
    isActive: Boolean,
    onPositionAndFrequencyChange: (position: Float, frequency: Float) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Downsample waveform envelope for background visual reference
    val waveformEnvelope = remember(sample) {
        val bins = 256
        val mins = FloatArray(bins)
        val maxs = FloatArray(bins)
        val frames = sample.numFrames
        if (frames > 0) {
            for (b in 0 until bins) {
                val start = (b.toLong() * frames / bins).toInt()
                val end = ((b + 1).toLong() * frames / bins).toInt().coerceAtLeast(start + 1)
                var lo = 0f
                var hi = 0f
                for (i in start until minOf(end, frames)) {
                    val v = sample.readSample(i)
                    if (v < lo) lo = v
                    if (v > hi) hi = v
                }
                mins[b] = lo
                maxs[b] = hi
            }
        }
        Pair(mins, maxs)
    }

    fun xToPosition(px: Float, width: Float): Float {
        val norm = (px / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
        return (norm * 2f) - 1f
    }

    fun yToFrequency(py: Float, height: Float): Float {
        // Top of pad = maxFreq, bottom of pad = minFreq
        val norm = (1f - (py / height.coerceAtLeast(1f))).coerceIn(0f, 1f)
        return (minFreq * exp(norm * ln(maxFreq / minFreq))).toFloat()
    }

    fun positionToX(pos: Float, width: Float): Float {
        val norm = ((pos + 1f) * 0.5f).coerceIn(0f, 1f)
        return norm * width
    }

    fun frequencyToY(freq: Float, height: Float): Float {
        val clamped = freq.coerceIn(minFreq, maxFreq)
        val norm = (ln(clamped / minFreq) / ln(maxFreq / minFreq)).coerceIn(0f, 1f)
        return (1f - norm) * height
    }

    Canvas(
        modifier = modifier
            .background(Color(0xFF101424))
            .border(1.dp, if (isActive) Color(0xFF4FC3F7) else Color(0xFF445566))
            .pointerInput(minFreq, maxFreq) {
                awaitEachGesture {
                    var down: PointerInputChange? = null
                    while (down == null) {
                        val evt = awaitPointerEvent()
                        down = evt.changes.firstOrNull { !it.previousPressed && it.pressed }
                    }
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()

                    onPositionAndFrequencyChange(
                        xToPosition(down.position.x, w),
                        yToFrequency(down.position.y, h)
                    )
                    onActiveChange(true)
                    down.consume()

                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.find { it.id == down.id } ?: break
                            if (change.pressed) {
                                onPositionAndFrequencyChange(
                                    xToPosition(change.position.x, w),
                                    yToFrequency(change.position.y, h)
                                )
                                change.consume()
                            }
                        } while (event.changes.any { it.id == down.id && it.pressed })
                    } finally {
                        onActiveChange(false)
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val midY = h * 0.5f

        // Grid lines
        for (i in 1..3) {
            val gx = w * (i / 4f)
            val gy = h * (i / 4f)
            drawLine(Color(0x22FFFFFF), Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
            drawLine(Color(0x22FFFFFF), Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
        }
        drawLine(Color(0x44FFFFFF), Offset(0f, midY), Offset(w, midY), strokeWidth = 1f)

        // Draw sample waveform silhouette across X with high contrast
        val (mins, maxs) = waveformEnvelope
        val bins = mins.size
        if (bins > 1) {
            val path = Path()
            for (b in 0 until bins) {
                val x = (b.toFloat() / (bins - 1)) * w
                val yMin = (midY - maxs[b] * midY * 0.9f).coerceIn(0f, h)
                val yMax = (midY - mins[b] * midY * 0.9f).coerceIn(0f, h)
                path.moveTo(x, yMin)
                path.lineTo(x, yMax)
            }
            drawPath(path, color = Color(0xCC4FC3F7), style = Stroke(width = 2.5f))
        }

        // Highlight the swept window width around cx
        val cx = positionToX(position, w)
        val cy = frequencyToY(frequency, h)
        val totalFrames = sample.numFrames.coerceAtLeast(1).toFloat()
        val halfSweepPx = (sweptFrames / totalFrames) * 0.5f * w
        val sweepLeft = (cx - halfSweepPx).coerceIn(0f, w)
        val sweepRight = (cx + halfSweepPx).coerceIn(0f, w)
        if (sweepRight > sweepLeft) {
            drawRect(
                color = if (isActive) Color(0x33FFB74D) else Color(0x224FC3F7),
                topLeft = Offset(sweepLeft, 0f),
                size = Size(sweepRight - sweepLeft, h)
            )
        }

        // Draw current crosshair and cursor
        val crosshairColor = if (isActive) Color(0xFFFFB74D) else Color(0x884FC3F7)

        drawLine(crosshairColor, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1.5f)
        drawLine(crosshairColor, Offset(0f, cy), Offset(w, cy), strokeWidth = 1.5f)
        drawCircle(
            color = if (isActive) Color(0xFFFF7043) else Color(0xFF4FC3F7),
            radius = if (isActive) 10.dp.toPx() else 7.dp.toPx(),
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color.White,
            radius = if (isActive) 10.dp.toPx() else 7.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )
    }
}

typealias SampleWaveShaperScreen = SampleScrubberScreen

class SampleScrubberScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val player = remember { SampleScrubberPlayer() }
        val scrollState = rememberScrollState()

        var uiPosition by remember { mutableFloatStateOf(0.0f) }
        var uiFrequency by remember { mutableFloatStateOf(110.0f) }
        var isPadActive by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val clarinet = loadClarinetSample()
            if (clarinet != null) {
                player.updateSample(clarinet, "Clarinet.wav")
            }
            player.start()
        }

        DisposableEffect(Unit) {
            onDispose {
                player.stop()
            }
        }

        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(12.dp)
                    .verticalScroll(scrollState)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = { navigator.pop() }) {
                        Text("Go Back")
                    }
                    Text(
                        "Sample Scrubber Theremin",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                SampleSourceBar(
                    sampleName = player.sampleName,
                    sampleFrames = player.sample.numFrames,
                    sampleRate = player.sample.frameRate,
                    onSampleLoaded = { newSample, name ->
                        player.updateSample(newSample, name)
                    }
                )

                // Radio buttons for Sine, Triangle, and RedNoise
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Input Signal:", style = MaterialTheme.typography.bodySmall)
                    ScrubberDriveSignal.entries.forEach { sig ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { player.selectDriveSignal(sig) }
                        ) {
                            RadioButton(
                                selected = player.activeSignal == sig,
                                onClick = { player.selectDriveSignal(sig) }
                            )
                            Text(sig.label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                val posDisplay = round(uiPosition * 1000f) / 1000f
                val freqDisplay = round(uiFrequency * 10f) / 10f
                Text(
                    "Press & drag X/Y pad — X: Position ($posDisplay) | Y: Rate ($freqDisplay Hz)",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(4.dp))

                ThereminXYPad(
                    sample = player.sample,
                    position = uiPosition,
                    frequency = uiFrequency,
                    sweptFrames = player.sweptFrames,
                    isActive = isPadActive,
                    onPositionAndFrequencyChange = { pos, freq ->
                        uiPosition = pos
                        uiFrequency = freq
                        player.setPositionAndRate(pos.toDouble(), freq.toDouble())
                    },
                    onActiveChange = { active ->
                        isPadActive = active
                        player.setThereminActive(active)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Fader controlling the number of samples swept peak-to-peak
                SweptSamplesSlider(
                    sweptFrames = player.sweptFrames,
                    maxFrames = player.sample.numFrames,
                    onSweptFramesChange = { frames ->
                        player.updateOscillatorAmplitudeFromSweptFrames(frames)
                    }
                )
                PortFader(
                    port = player.sampleScrubber.time,
                    minValue = 0.001f,
                    maxValue = 0.5f,
                    isExponential = true,
                    name = "Smooth Time (s)"
                )
                PortFader(
                    port = player.sampleScrubber.amplitude,
                    minValue = 0.0f,
                    maxValue = 1.0f,
                    isExponential = false,
                    name = "Output Gain"
                )

                Oscilloscope(
                    probe = player.probe,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(top = 8.dp)
                )
            }
        }
    }
}
