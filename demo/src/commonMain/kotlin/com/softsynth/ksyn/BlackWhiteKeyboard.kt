package com.softsynth.ksyn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softsynth.math.AudioMath

/**
 * A visual 1-octave keyboard widget laid out like a real piano.
 */
@Composable
fun BlackWhiteKeyboard(
    modifier: Modifier = Modifier,
    onNoteOn: (frequency: Double) -> Unit,
    onNoteOff: () -> Unit
) {
    var lastNote by remember { mutableStateOf("None") }
    var lastFreq by remember { mutableStateOf(0.0) }

    // White keys (index relative to C = 0)
    val whiteKeys = listOf(
        Pair("C", 0), Pair("D", 2), Pair("E", 4), Pair("F", 5),
        Pair("G", 7), Pair("A", 9), Pair("B", 11), Pair("C", 12)
    )
    
    // Black keys (note, index, and which white key it follows, 1-indexed)
    val blackKeys = listOf(
        Triple("C#", 1, 1), Triple("D#", 3, 2),
        Triple("F#", 6, 4), Triple("G#", 8, 5), Triple("A#", 10, 6)
    )

    val whiteKeyWidth = 48.dp
    val blackKeyWidth = 32.dp

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val freqStr = if (lastFreq > 0) "${kotlin.math.round(lastFreq * 100) / 100.0} Hz" else "--- Hz"
        Text(
            text = "Last Pressed: $lastNote | $freqStr",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(modifier = Modifier.wrapContentSize()) {
            // White keys
            Row {
                for (note in whiteKeys) {
                    PianoKey(
                        note = note.first,
                        index = note.second,
                        isBlack = false,
                        width = whiteKeyWidth,
                        height = 140.dp,
                        onNoteOn = { freq ->
                            lastNote = note.first
                            lastFreq = freq
                            onNoteOn(freq)
                        },
                        onNoteOff = onNoteOff
                    )
                }
            }
            // Black keys overlay
            for (note in blackKeys) {
                Box(
                    modifier = Modifier.offset(x = (whiteKeyWidth * note.third) - (blackKeyWidth / 2))
                ) {
                    PianoKey(
                        note = note.first,
                        index = note.second,
                        isBlack = true,
                        width = blackKeyWidth,
                        height = 90.dp,
                        onNoteOn = { freq ->
                            lastNote = note.first
                            lastFreq = freq
                            onNoteOn(freq)
                        },
                        onNoteOff = onNoteOff
                    )
                }
            }
        }
    }
}

@Composable
fun PianoKey(
    note: String,
    index: Int,
    isBlack: Boolean,
    width: Dp,
    height: Dp,
    onNoteOn: (Double) -> Unit,
    onNoteOff: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width, height)
            .background(if (isBlack) Color.DarkGray else Color.White)
            .border(1.dp, Color.Black)
            .pointerInput(index) {
                detectTapGestures(
                    onPress = {
                        val freq = AudioMath.pitchToFrequency(48.0 + index)
                        onNoteOn(freq)
                        tryAwaitRelease()
                        onNoteOff()
                    }
                )
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = note,
            fontWeight = FontWeight.Bold,
            color = if (isBlack) Color.White else Color.Black,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}
