package com.softsynth.ksyn

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mobileer.audiobridge.AudioResult
import com.softsynth.ksyn.unitgen.*

class FilterLabPlayer : KSynPlayable {
    val ksynAudioBridge: KSynAudioBridge
    val synth = KSyn.createSynthesizer()
    val lineOut = LineOut()

    val sources: List<Pair<String, UnitGenerator>> = listOf(
        Pair("WhiteNoise", WhiteNoise()),
        Pair("RedNoise", RedNoise()),
        Pair("SineOscillator", SineOscillator()),
        Pair("TriangleOscillator", TriangleOscillator()),
        Pair("SawtoothOscillator", SawtoothOscillator()),
        Pair("SawtoothOscillatorBL", SawtoothOscillatorBL()),
        Pair("SawtoothOscillatorDPW", SawtoothOscillatorDPW()),
        Pair("SquareOscillator", SquareOscillator()),
        Pair("SquareOscillatorBL", SquareOscillatorBL()),
        Pair("PulseOscillator", PulseOscillator()),
        Pair("PulseOscillatorBL", PulseOscillatorBL()),
        Pair("MorphingOscillatorBL", MorphingOscillatorBL()),
        Pair("ImpulseOscillator", ImpulseOscillator())
    ).sortedBy { it.first } // Sort alphabetically by name

    val filters: List<Pair<String, UnitFilter>> = listOf(
        Pair("PassThrough", PassThrough()),
        Pair("FilterLowPass", FilterLowPass()),
        Pair("FilterHighPass", FilterHighPass()),
        Pair("FilterBandPass", FilterBandPass()),
        Pair("FilterBandStop", FilterBandStop()),
        Pair("FilterFourPoles", FilterFourPoles()),
        Pair("FilterStateVariable", FilterStateVariable()),
        Pair("FilterPeakingEQ", FilterPeakingEQ())
    ).sortedBy { it.first } // Sort alphabetically by name

    var activeSourceIndex by mutableStateOf(0)
    var activeFilterIndex by mutableStateOf(0)

    val activeSource: UnitGenerator
        get() = sources[activeSourceIndex].second

    val activeFilter: UnitFilter
        get() = filters[activeFilterIndex].second

    init {
        ksynAudioBridge = KSynAudioBridge(synth)
        synth.add(lineOut)

        // Add all units to synthesizer
        for (source in sources) {
            synth.add(source.second)
            // Set some default amplitude for oscillators to avoid blowing out speakers when switching
            val ampPort = source.second.getPortByName("Amplitude") as? com.softsynth.ksyn.ports.UnitInputPort
            ampPort?.set(0.2)
        }
        for (filter in filters) {
            synth.add(filter.second)
        }

        updateRouting()
        lineOut.start()
    }

    fun updateRouting() {
        // Disconnect all
        for (source in sources) {
            val output = (source.second as? UnitSource)?.getOutputPort()
            output?.disconnectAll()
        }
        for (filter in filters) {
            filter.second.output.disconnectAll()
        }

        // Connect new routing: Source -> Filter -> LineOut
        val sourceOutput = (activeSource as? UnitSource)?.getOutputPort()
        if (sourceOutput != null) {
            sourceOutput.connect(0, activeFilter.input, 0)
            activeFilter.output.connect(0, lineOut.input, 0)
            activeFilter.output.connect(0, lineOut.input, 1)
        }
    }

    override fun start(): AudioResult {
        return ksynAudioBridge.start()
    }

    override fun stop() {
        ksynAudioBridge.stop()
    }
}

class FilterLab : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val player = remember { FilterLabPlayer() }
        var isPlaying by remember { mutableStateOf(false) }

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

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(
                        onClick = {
                            if (player.start() == AudioResult.OK) isPlaying = true
                        },
                        enabled = !isPlaying
                    ) { Text("START") }

                    Button(
                        onClick = {
                            player.stop()
                            isPlaying = false
                        },
                        enabled = isPlaying
                    ) { Text("STOP") }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Source Selection
                Text("Source", style = MaterialTheme.typography.titleMedium)
                var sourceMenuExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = sourceMenuExpanded,
                    onExpandedChange = { sourceMenuExpanded = !sourceMenuExpanded }
                ) {
                    TextField(
                        readOnly = true,
                        value = player.sources[player.activeSourceIndex].first,
                        onValueChange = {},
                        label = { Text("Select Source") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceMenuExpanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = sourceMenuExpanded,
                        onDismissRequest = { sourceMenuExpanded = false }
                    ) {
                        player.sources.forEachIndexed { index, pair ->
                            DropdownMenuItem(
                                text = { Text(pair.first) },
                                onClick = {
                                    player.activeSourceIndex = index
                                    player.updateRouting()
                                    sourceMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                
                UnitGeneratorFaders(unitGenerator = player.activeSource)

                Spacer(modifier = Modifier.height(24.dp))

                // Filter Selection
                Text("Filter", style = MaterialTheme.typography.titleMedium)
                var filterMenuExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = filterMenuExpanded,
                    onExpandedChange = { filterMenuExpanded = !filterMenuExpanded }
                ) {
                    TextField(
                        readOnly = true,
                        value = player.filters[player.activeFilterIndex].first,
                        onValueChange = {},
                        label = { Text("Select Filter") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterMenuExpanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false }
                    ) {
                        player.filters.forEachIndexed { index, pair ->
                            DropdownMenuItem(
                                text = { Text(pair.first) },
                                onClick = {
                                    player.activeFilterIndex = index
                                    player.updateRouting()
                                    filterMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                
                UnitGeneratorFaders(unitGenerator = player.activeFilter)
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                player.stop()
            }
        }
    }
}
