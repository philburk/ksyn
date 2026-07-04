package com.softsynth.ksyn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mobileer.audiobridge.AudioResult
import com.softsynth.ksyn.compose.BlackWhiteKeyboard
import com.softsynth.ksyn.compose.UnitGeneratorFaders
import com.softsynth.ksyn.instruments.KSynInstrumentLibrary
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.LineOut
import com.softsynth.ksyn.voices.PitchedVoice
import com.softsynth.ksyn.voices.VoiceDescription
import com.softsynth.ksyn.voices.PolyphonicInstrument
import com.softsynth.math.AudioMath

class KSynInstrumentsPlayer : KSynPlayable() {
    val ksynAudioBridge: KSynAudioBridge
    val synth = KSyn.createSynthesizer()
    val lineOut = LineOut()
    val numVoices = 4

    var polyphonicInstrument: PolyphonicInstrument? = null
    var voices: Array<PitchedVoice>? = null

    var activeInstrumentIndex by mutableStateOf(0)
    
    // Store the selected preset index for each voice so it isn't lost when switching instruments
    val activePresetIndices = mutableStateListOf<Int>().apply {
        repeat(KSynInstrumentLibrary.voiceDescriptions.size) { add(0) }
    }

    var activePresetIndex: Int
        get() = activePresetIndices[activeInstrumentIndex]
        set(value) {
            activePresetIndices[activeInstrumentIndex] = value
        }

    val activeVoiceDesc: VoiceDescription
        get() = KSynInstrumentLibrary.voiceDescriptions[activeInstrumentIndex]

//    val activeVoice: PitchedVoice
//        get() = voices?.get(0) ?: throw IllegalStateException("Voices not initialized")

    init {
        ksynAudioBridge = KSynAudioBridge(synth)
        synth.add(lineOut)
        updateRouting()
        lineOut.start()
    }

    fun updateRouting() {
        // Disconnect all previous voice mappings to clear the lineOut
        val currentVoices = voices
        if (currentVoices != null) {
            for (voice in currentVoices) {
                val output = voice.getOutputPort()
                output.disconnectAll()
                synth.remove(voice.getUnitGenerator())
            }
        }

        // Create a new PolyphonicInstrument
        val newVoices = Array(numVoices) {
            val voice = activeVoiceDesc.createPitchedVoice()
            val voiceGen = voice.getUnitGenerator()
            synth.add(voiceGen)
            val ampPort = voiceGen.getPortByName("Amplitude") as? com.softsynth.ksyn.ports.UnitInputPort
            ampPort?.set(0.1)
            voice
        }
        voices = newVoices
        polyphonicInstrument = PolyphonicInstrument(synth, newVoices)

        val outputPort = polyphonicInstrument!!.getOutputPort()
        val numParts = outputPort.numParts
        if (numParts == 1) {
            // Mono: Connect output 0 to both left and right
            outputPort.connect(0, lineOut.input, 0)
            outputPort.connect(0, lineOut.input, 1)
        } else if (numParts >= 2) {
            // Stereo (or more): Connect output 0 to left, output 1 to right
            outputPort.connect(0, lineOut.input, 0)
            outputPort.connect(1, lineOut.input, 1)
        }
    }

    // Playable Methods
    override fun start(): AudioResult {
        return ksynAudioBridge.start()
    }

    override fun stop() {
        ksynAudioBridge.stop()
    }
}

class KSynInstrumentsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val player = remember { KSynInstrumentsPlayer() }
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
            ) {
                Button(onClick = { navigator.pop() }) {
                    Text("Go Back")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Voice Selection Menus
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Select Instrument", style = MaterialTheme.typography.titleMedium)
                        var voiceMenuExpanded by remember { mutableStateOf(false) }
                        
                        ExposedDropdownMenuBox(
                            expanded = voiceMenuExpanded,
                            onExpandedChange = { voiceMenuExpanded = !voiceMenuExpanded }
                        ) {
                            TextField(
                                readOnly = true,
                                value = player.activeVoiceDesc.name,
                                onValueChange = {},
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceMenuExpanded) },
                                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = voiceMenuExpanded,
                                onDismissRequest = { voiceMenuExpanded = false }
                            ) {
                                KSynInstrumentLibrary.voiceDescriptions.forEachIndexed { index, desc ->
                                    DropdownMenuItem(
                                        text = { Text(desc.name) },
                                        onClick = {
                                            player.activeInstrumentIndex = index
                                            // Do NOT reset player.activePresetIndex = 0 here. 
                                            // We want to preserve the preset the instrument is already in.
                                            player.updateRouting()
                                            voiceMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("Select Preset", style = MaterialTheme.typography.titleMedium)
                        var presetMenuExpanded by remember { mutableStateOf(false) }
                        val presetNames = player.activeVoiceDesc.presetNames
                        val currentPresetName = if (presetNames.isNotEmpty() && player.activePresetIndex < presetNames.size) {
                            presetNames[player.activePresetIndex]
                        } else {
                            "Preset ${player.activePresetIndex}"
                        }
                        
                        ExposedDropdownMenuBox(
                            expanded = presetMenuExpanded,
                            onExpandedChange = { presetMenuExpanded = !presetMenuExpanded }
                        ) {
                            TextField(
                                readOnly = true,
                                value = currentPresetName,
                                onValueChange = {},
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetMenuExpanded) },
                                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = presetMenuExpanded,
                                onDismissRequest = { presetMenuExpanded = false }
                            ) {
                                presetNames.forEachIndexed { index, name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            player.activePresetIndex = index
                                            player.synth.queueCommand {
                                                player.polyphonicInstrument?.usePreset(index)
                                            }
                                            presetMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Keyboard Input
                BlackWhiteKeyboard(
                    onKeyDown = { noteNumber ->
                        player.polyphonicInstrument?.noteOn(
                            noteNumber,
                            pitch = noteNumber.toDouble(),
                            amplitude = 0.5,
                            timeStamp = TimeStamp(player.synth.currentTime)
                        )
                    },
                    onKeyUp = { noteNumber ->
                        player.polyphonicInstrument?.noteOff(noteNumber,
                            timeStamp = TimeStamp(player.synth.currentTime)
                        )
                    },
                    numNotes = 25
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                // Active Voice Faders
                // Wrap in a Column/Box that limits size or allows scrolling natively if preferred.
                // Using weight to let the faders take available remaining vertical space while keeping the keyboard visible.
                Box(modifier = Modifier.weight(1f)) {
                    val activeGenerator = player.polyphonicInstrument
                    if (activeGenerator != null) {
                        UnitGeneratorFaders(
                            unitGenerator = activeGenerator.getUnitGenerator(),
                            presetKey = player.activePresetIndex,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                player.stop()
            }
        }
    }
}
