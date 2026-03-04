package com.softsynth.ksyn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mobileer.audiobridge.AudioResult
import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.math.AudioMath
import com.softsynth.ksyn.unitgen.LineOut
import com.softsynth.ksyn.unitgen.VariableRateMonoReader
import com.softsynth.ksyn.util.SampleLoader
import ksyn_project.demo.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

class PlaySamplePlayer : KSynPlayable() {
    val ksynAudioBridge: KSynAudioBridge
    val synth = KSyn.createSynthesizer()
    val lineOut = LineOut()
    val sampleReader = VariableRateMonoReader()

    var sample: FloatSample? = null
    var isLoaded by mutableStateOf(false)
    var currentRate by mutableStateOf(1.0)

    init {
        ksynAudioBridge = KSynAudioBridge(synth)
        synth.add(lineOut)
        synth.add(sampleReader)

        // Connect the mono reader to both left and right outputs
        sampleReader.output.connect(0, lineOut.input, 0)
        sampleReader.output.connect(0, lineOut.input, 1)

        lineOut.start()
    }

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadSample() {
        try {
            val bytes = Res.readBytes("files/Clarinet.wav")
            sample = SampleLoader.loadFloatSample(bytes)
            isLoaded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playNote(frequency: Double) {
        val loadedSample = sample ?: return
        val baseFreq = AudioMath.pitchToFrequency(60.0) // Middle C assumption
        val rateScaler = frequency / baseFreq
        val targetRate = loadedSample.frameRate * rateScaler
        currentRate = rateScaler

        synth.queueCommand {
            sampleReader.rate.set(targetRate)
            sampleReader.dataQueue.queueOn(loadedSample)
        }
    }

    fun stopNote() {
        val loadedSample = sample ?: return
        synth.queueCommand {
            sampleReader.dataQueue.queueOff(loadedSample)
        }
    }

    override fun start(): AudioResult {
        return ksynAudioBridge.start()
    }

    override fun stop() {
        ksynAudioBridge.stop()
    }
}

class PlaySampleScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val player = remember { PlaySamplePlayer() }
        var isPlaying by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            player.loadSample()
            if (player.start() == AudioResult.OK) isPlaying = true
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

                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Clarinet.wav (Native Compose Resources Decoder)", style = MaterialTheme.typography.titleMedium)
                Text("Playback Rate Scaler: ${kotlin.math.round(player.currentRate * 1000) / 1000.0}x", color = MaterialTheme.colorScheme.primary)

                Spacer(modifier = Modifier.height(16.dp))

                BlackWhiteKeyboard(
                    onNoteOn = { frequency ->
                        if (isPlaying) player.playNote(frequency)
                    },
                    onNoteOff = {
                        if (isPlaying) player.stopNote()
                    }
                )
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                player.stop()
            }
        }
    }
}
