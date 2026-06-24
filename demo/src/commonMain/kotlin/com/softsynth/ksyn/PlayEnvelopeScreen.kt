package com.softsynth.ksyn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.softsynth.ksyn.compose.SegmentedEnvelopeEditor
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mobileer.audiobridge.AudioResult
import com.softsynth.ksyn.compose.BlackWhiteKeyboard
import com.softsynth.ksyn.data.SegmentedEnvelope
import com.softsynth.ksyn.unitgen.LineOut
import com.softsynth.ksyn.unitgen.SawtoothOscillatorDPW
import com.softsynth.ksyn.unitgen.VariableRateMonoReader
import com.softsynth.ksyn.util.SampleLoader
import com.softsynth.math.AudioMath
import ksyn_project.demo.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.math.round

class PlayEnvelopePlayer : KSynPlayable() {
    val ksynAudioBridge: KSynAudioBridge
    val synth = KSyn.createSynthesizer()
    val lineOut = LineOut()
    val sawOsc = SawtoothOscillatorDPW()
    val envelopeReader = VariableRateMonoReader()

    lateinit var ampEnvelope: SegmentedEnvelope
    var isLoaded by mutableStateOf(false)
    var currentRate by mutableStateOf(1.0)

    init {
        ksynAudioBridge = KSynAudioBridge(synth)
        synth.add(lineOut)
        synth.add(sawOsc)
        synth.add(envelopeReader)

        envelopeReader.output.connect(sawOsc.amplitude,)
        sawOsc.output.connect(0, lineOut.input, 0)
        sawOsc.output.connect(0, lineOut.input, 1)

        val ampData = doubleArrayOf(
            0.02, 0.9, // duration,value pair 0, "attack"
            0.10, 0.5, // pair 1, "decay"
            0.50, 0.0  // pair 2, "release"
            )
        ampEnvelope = SegmentedEnvelope( ampData )

        // Hang at end of decay segment to provide a "sustain" segment.
        // Note End point is one past the last index.
        ampEnvelope.sustainBegin = 2
        ampEnvelope.sustainEnd = 2
    }

    fun playNote(frequency: Double) {
        synth.queueCommand {
            sawOsc.frequency.set(frequency)
            envelopeReader.dataQueue.queueOn(ampEnvelope)
        }
    }

    fun stopNote() {
        synth.queueCommand {
            envelopeReader.dataQueue.queueOff(ampEnvelope)
        }
    }

    override fun start(): AudioResult {
        lineOut.start()
        return ksynAudioBridge.start()
    }

    override fun stop() {
        lineOut.stop()
        ksynAudioBridge.stop()
    }
}

class PlayEnvelopeScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val player = remember { PlayEnvelopePlayer() }
        var isPlaying by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (player.start() == AudioResult.OK) isPlaying = true
        }

        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Button(onClick = { navigator.pop() }) {
                    Text("Go Back")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "SawtoothOscillatorDPW with SegmentedEnvelope amplitude control.",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Envelope Editor", style = MaterialTheme.typography.titleSmall)

                Spacer(modifier = Modifier.height(8.dp))

                SegmentedEnvelopeEditor(
                    envelope = player.ampEnvelope,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    minValue = 0f,
                    maxValue = 1f,
                    maxTime = 2f,
                )

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
