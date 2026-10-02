package com.softsynth.ksyn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.softsynth.audiobridge.AudioResult
import com.softsynth.ksyn.compose.BlackWhiteKeyboard
import com.softsynth.ksyn.compose.Oscilloscope
import com.softsynth.ksyn.data.FloatSample
import com.softsynth.math.AudioMath
import com.softsynth.ksyn.unitgen.Circuit
import com.softsynth.ksyn.unitgen.LineOut
import com.softsynth.ksyn.unitgen.ScopeProbe
import com.softsynth.ksyn.unitgen.VariableRateMonoReader
import com.softsynth.ksyn.util.SampleLoader
import ksyn_project.demo.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** A sample reader wrapped in a Circuit, like a sample-playing voice. */
class SamplePlayerCircuit : Circuit() {
    val reader = VariableRateMonoReader()

    init {
        add(reader)
    }
}

class PlaySamplePlayer : KSynPlayable() {
    val ksynAudioBridge: KSynAudioBridge
    val synth = KSyn.createSynthesizer()
    val lineOut = LineOut()
    val playerCircuit = SamplePlayerCircuit()
    val sampleReader = playerCircuit.reader
    val scope = ScopeProbe(numChannels = 1)

    var sample: FloatSample? = null
    var isLoaded by mutableStateOf(false)
    var currentRate by mutableStateOf(1.0)

    init {
        ksynAudioBridge = KSynAudioBridge(synth)
        synth.add(lineOut)
        synth.add(playerCircuit)
        synth.add(scope)

        // Connect the mono reader to both left and right outputs
        sampleReader.output.connect(0, lineOut.input, 0)
        sampleReader.output.connect(0, lineOut.input, 1)
        sampleReader.output.connect(0, scope.input, 0)
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

    fun playNote(pitch: Double) {
        val loadedSample = sample ?: return
        val sampleFreq = AudioMath.pitchToFrequency(60.0) // Middle C assumption
        val rateScaler = AudioMath.pitchToFrequency(pitch) / sampleFreq
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
        lineOut.start()
        playerCircuit.start()
        scope.start()
        return ksynAudioBridge.start()
    }

    override fun stop() {
        ksynAudioBridge.stop()
        lineOut.stop()
        playerCircuit.stop()
        scope.stop()
    }

    override fun getScopeProbe(): ScopeProbe = scope
}

class PlaySampleScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val player = remember { PlaySamplePlayer() }
        var isPlaying by remember { mutableStateOf(false) }

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

                Text("Clarinet.wav in a Circuit", style = MaterialTheme.typography.titleMedium)
                Text("Playback Rate Scaler: ${kotlin.math.round(player.currentRate * 1000) / 1000.0}x", color = MaterialTheme.colorScheme.primary)

                Spacer(modifier = Modifier.height(16.dp))

                BlackWhiteKeyboard(
                    onKeyDown = { noteNumber ->
                        if (isPlaying) player.playNote(noteNumber.toDouble())
                    },
                    onKeyUp = { noteNumber ->
                        if (isPlaying) player.stopNote()
                    },
                    numNotes = 25
                )

                Oscilloscope(
                    probe = player.scope,
                    modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = 16.dp),
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
