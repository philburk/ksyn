package com.softsynth.ksyn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.softsynth.audiobridge.AudioDeviceManager
import com.softsynth.audiobridge.AudioInputBridge
import com.softsynth.audiobridge.AudioOutputBridge
import com.softsynth.audiobridge.AudioPermissionState
import com.softsynth.audiobridge.AudioResult
import com.softsynth.audiobridge.writeSuspending
import com.softsynth.ksyn.unitgen.LineIn
import com.softsynth.ksyn.unitgen.LineOut
import com.softsynth.ksyn.unitgen.Multiply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

enum class DuplexMode {
    IDLE,
    PREPARING,
    STABLE
}

class FullDuplexScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val context = getPlatformContext()
        val audioInputSupported = remember { AudioInputBridge.isSupported() }

        var duplexMode by remember { mutableStateOf(DuplexMode.IDLE) }
        var loopGain by remember { mutableStateOf(0.1f) }

        var framesReadCount by remember { mutableStateOf(0L) }
        var framesWrittenCount by remember { mutableStateOf(0L) }
        var permissionStatusMessage by remember { mutableStateOf("") }
        var preparingLoopsCount by remember { mutableStateOf(0) }

        var activeInputDeviceName by remember { mutableStateOf("None") }
        var activeOutputDeviceName by remember { mutableStateOf("None") }
        var averageInputLevel by remember { mutableStateOf(0.0f) }
        var lastFramesReadPreparing by remember { mutableStateOf(0) }

        var duplexJob by remember { mutableStateOf<Job?>(null) }

        fun stopDuplex() {
            duplexJob?.cancel()
            duplexJob = null
            duplexMode = DuplexMode.IDLE
            activeInputDeviceName = "None"
            activeOutputDeviceName = "None"
            averageInputLevel = 0.0f
        }

        fun startDuplex() {
            scope.launch {
                permissionStatusMessage = ""
                var state = AudioInputBridge.getPermissionState(context)
                if (state != AudioPermissionState.GRANTED) {
                    permissionStatusMessage = "Requesting permission..."
                    state = AudioInputBridge.requestPermission(context)
                    if (state != AudioPermissionState.GRANTED) {
                        permissionStatusMessage = "Permission denied."
                        return@launch
                    }
                    permissionStatusMessage = "Permission now granted."
                } else {
                    permissionStatusMessage = "Permission already granted."
                }

                stopDuplex()
                duplexMode = DuplexMode.PREPARING
                framesReadCount = 0L
                framesWrittenCount = 0L
                preparingLoopsCount = 0
                averageInputLevel = 0.0f

                val optimalSampleRate = AudioDeviceManager.getOptimalSampleRate()
                val bufferSizeFrames = 64

                val inputBridge = AudioInputBridge.create {
                    sampleRate = optimalSampleRate
                    framesPerBuffer = bufferSizeFrames
                    channels = 1
                }
                val outputBridge = AudioOutputBridge.create {
                    sampleRate = optimalSampleRate
                    framesPerBuffer = bufferSizeFrames
                }

                duplexJob = scope.launch(Dispatchers.Default) {
                    val inputOpen = inputBridge.open()
                    if (inputOpen != AudioResult.OK) {
                        println("Duplex: Failed to open input: $inputOpen")
                        stopDuplex()
                        return@launch
                    }

                    val outputOpen = outputBridge.open()
                    if (outputOpen != AudioResult.OK) {
                        println("Duplex: Failed to open output: $outputOpen")
                        inputBridge.close()
                        stopDuplex()
                        return@launch
                    }

                    val inputStart = inputBridge.start()
                    if (inputStart != AudioResult.OK) {
                        println("Duplex: Failed to start input: $inputStart")
                        inputBridge.close()
                        outputBridge.close()
                        stopDuplex()
                        return@launch
                    }

                    val outputStart = outputBridge.start()
                    if (outputStart != AudioResult.OK) {
                        println("Duplex: Failed to start output: $outputStart")
                        inputBridge.stop()
                        inputBridge.close()
                        outputBridge.close()
                        stopDuplex()
                        return@launch
                    }

                    activeInputDeviceName = inputBridge.getCurrentDeviceName()
                    activeOutputDeviceName = outputBridge.getCurrentDeviceName()

                    // Instantiate KSyn Synthesizer
                    val synth = KSyn.createSynthesizer()
                    val lineIn = LineIn()
                    val multiplyLeft = Multiply()
                    val multiplyRight = Multiply()
                    val lineOut = LineOut()

                    synth.add(lineIn)
                    synth.add(multiplyLeft)
                    synth.add(multiplyRight)
                    synth.add(lineOut)

                    // Connect LineIn channels 0 and 1 to multipliers inputA
                    lineIn.output.connect(0, multiplyLeft.inputA, 0)
                    lineIn.output.connect(1, multiplyRight.inputA, 0)

                    // Connect multiplier outputs to LineOut
                    multiplyLeft.output.connect(0, lineOut.input, 0)
                    multiplyRight.output.connect(0, lineOut.input, 1)

                    multiplyLeft.inputB.set(loopGain.toDouble())
                    multiplyRight.inputB.set(loopGain.toDouble())

                    // Start the synth and the units
                    synth.start(optimalSampleRate, 1, 2)
                    lineOut.start()

                    val tempInputBuffer = FloatArray(bufferSizeFrames * 4) // extra buffer headroom
                    val monoInputBuffer = FloatArray(bufferSizeFrames)
                    val silenceInputBuffer = FloatArray(bufferSizeFrames)

                    val STABLE_LOOPS_REQUIRED = 8
                    var stableLoopsConsecutive = 0

                    try {
                        while (isActive) {
                            val currentGain = loopGain.toDouble()
                            multiplyLeft.inputB.set(currentGain)
                            multiplyRight.inputB.set(currentGain)

                            if (duplexMode == DuplexMode.PREPARING) {
                                var totalReadThisLoop = 0
                                while (isActive) {
                                    val remainingSpace = tempInputBuffer.size - totalReadThisLoop
                                    if (remainingSpace <= 0) break
                                    val read = inputBridge.read(tempInputBuffer, totalReadThisLoop, min(bufferSizeFrames, remainingSpace))
                                    if (read <= 0) break
                                    totalReadThisLoop += read
                                }

                                framesReadCount += totalReadThisLoop
                                lastFramesReadPreparing = totalReadThisLoop

                                var sum = 0.0f
                                for (i in 0 until totalReadThisLoop) {
                                    sum += kotlin.math.abs(tempInputBuffer[i])
                                }
                                averageInputLevel = if (totalReadThisLoop > 0) sum / totalReadThisLoop else 0.0f

                                val limit = bufferSizeFrames * 5 / 4
                                if (totalReadThisLoop <= limit) {
                                    stableLoopsConsecutive++
                                    preparingLoopsCount = stableLoopsConsecutive
                                    if (stableLoopsConsecutive >= STABLE_LOOPS_REQUIRED) {
                                        duplexMode = DuplexMode.STABLE
                                    }
                                } else {
                                    stableLoopsConsecutive = 0
                                    preparingLoopsCount = 0
                                }

                                val stereoBuffer = synth.renderBuffer(silenceInputBuffer)

                                val written = outputBridge.writeSuspending(stereoBuffer, 0, bufferSizeFrames, timeoutMillis = 1000L)
                                if (written < 0) break
                                framesWrittenCount += written
                            } else {
                                val readCount = inputBridge.read(monoInputBuffer, 0, bufferSizeFrames)
                                framesReadCount += readCount

                                var sum = 0.0f
                                for (i in 0 until readCount) {
                                    sum += kotlin.math.abs(monoInputBuffer[i])
                                }
                                averageInputLevel = if (readCount > 0) sum / readCount else 0.0f

                                if (readCount < bufferSizeFrames) {
                                    monoInputBuffer.fill(0.0f, readCount, bufferSizeFrames)
                                }

                                val stereoBuffer = synth.renderBuffer(monoInputBuffer)

                                val written = outputBridge.writeSuspending(stereoBuffer, 0, bufferSizeFrames, timeoutMillis = 1000L)
                                if (written < 0) break
                                framesWrittenCount += written
                            }
                        }
                    } finally {
                        synth.stop()
                        inputBridge.stop()
                        inputBridge.close()
                        outputBridge.stop()
                        outputBridge.close()
                        stopDuplex()
                    }
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                duplexJob?.cancel()
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick = { navigator.pop() }
            ) {
                Text("Go Back")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Full Duplex Audio Demo (KSyn)",
                style = androidx.compose.ui.text.TextStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!audioInputSupported) {
                Text("Full Duplex Audio is not supported on this platform.", color = androidx.compose.ui.graphics.Color.Red)
            } else {
                Row {
                    Button(
                        onClick = { startDuplex() },
                        enabled = duplexMode == DuplexMode.IDLE
                    ) {
                        Text("START DUPLEX")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { stopDuplex() },
                        enabled = duplexMode != DuplexMode.IDLE
                    ) {
                        Text("STOP")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (duplexMode) {
                    DuplexMode.PREPARING -> {
                        Text("Status: Preparing... ($preparingLoopsCount / 8 stable loops)")
                        Text("Last Frames Read (Preparing): $lastFramesReadPreparing")
                    }
                    DuplexMode.STABLE -> {
                        Text("Status: Stable")
                    }
                    DuplexMode.IDLE -> {
                        Text("Status: Idle")
                    }
                }

                if (duplexMode != DuplexMode.IDLE) {
                    Text("Active Input: $activeInputDeviceName")
                    Text("Active Output: $activeOutputDeviceName")
                }

                Text("Frames Read: $framesReadCount")
                Text("Frames Written: $framesWrittenCount")
                val roundedLevel = ((averageInputLevel * 1000).toInt() / 1000.0f)
                Text("Average Input Level: $roundedLevel")

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.width(350.dp)) {
                    val roundedGain = ((loopGain * 100).toInt() / 100.0f)
                    Text("Loop Gain: $roundedGain", modifier = Modifier.width(120.dp))
                    Slider(
                        value = loopGain,
                        onValueChange = { loopGain = it },
                        valueRange = 0.0f..2.0f,
                        modifier = Modifier.weight(1.0f)
                    )
                }

                if (permissionStatusMessage.isNotEmpty()) {
                    Text("Permission: $permissionStatusMessage")
                }
            }
        }
    }
}
