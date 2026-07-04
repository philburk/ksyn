package com.softsynth.ksyn.compose

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A visual 1-octave keyboard widget laid out like a real piano.
 */
@Composable
fun BlackWhiteKeyboard(
    modifier: Modifier = Modifier.Companion,
    onKeyDown: (noteNumber: Int) -> Unit,
    onKeyUp: (noteNumber: Int) -> Unit,
    numNotes: Int = 13, // Generally 12*N+1
    startingNote: Int = 48 // Middle C is 60. Must be multiple of 8
) {
    var lastNoteName by remember { mutableStateOf("None") }
    var lastNoteNumber by remember { mutableStateOf(0) }
    val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    val whiteKeyWidth = 48.dp
    val blackKeyWidth = 32.dp

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.Companion.CenterHorizontally
    ) {
        Text(
            text = "Last Pressed: $lastNoteName | $lastNoteNumber",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.Companion.padding(bottom = 16.dp)
        )

        Box(modifier = Modifier.Companion.wrapContentSize()) {
            // White keys
            Row {
                for (i in 0..<numNotes) {
                    val keyIndex = i % 12
                    val noteName = noteNames[keyIndex]
                    if (noteName.length == 1) {
                        PianoKey(
                            noteName = noteName,
                            index = i + startingNote,
                            isBlack = false,
                            width = whiteKeyWidth,
                            height = 140.dp,
                            onKeyDown = { noteNumber ->
                                lastNoteName = noteName
                                lastNoteNumber = noteNumber
                                onKeyDown(noteNumber)
                            },
                            onKeyUp = onKeyUp
                        )
                    }
                }
            }
            var whiteKeyCount = 0
            for (i in 0..<numNotes) {
                val keyIndex = i % 12
                val noteName = noteNames[keyIndex]
                if (noteName.length == 1) {
                    whiteKeyCount++
                } else { // Black key overlay.
                    Box(
                        modifier = Modifier.Companion.offset(x = (whiteKeyWidth * whiteKeyCount) - (blackKeyWidth / 2))
                    ) {
                        PianoKey(
                            noteName = noteName,
                            index = i + startingNote,
                            isBlack = true,
                            width = blackKeyWidth,
                            height = 90.dp,
                            onKeyDown = { noteNumber ->
                                lastNoteName = noteName
                                lastNoteNumber = noteNumber
                                onKeyDown(noteNumber)
                            },
                            onKeyUp = onKeyUp
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun PianoKey(
    noteName: String,
    index: Int,
    isBlack: Boolean,
    width: Dp,
    height: Dp,
    onKeyDown: (noteNumber: Int) -> Unit,
    onKeyUp: (noteNumber: Int) -> Unit
) {
    Box(
        modifier = Modifier.Companion
            .size(width, height)
            .background(if (isBlack) Color.Companion.DarkGray else Color.Companion.White)
            .border(1.dp, Color.Companion.Black)
            .pointerInput(index) {
                detectTapGestures(
                    onPress = {
                        onKeyDown(index)
                        tryAwaitRelease()
                        onKeyUp(index)
                    }
                )
            },
        contentAlignment = Alignment.Companion.BottomCenter
    ) {
        Text(
            text = noteName,
            fontWeight = FontWeight.Companion.Bold,
            color = if (isBlack) Color.Companion.White else Color.Companion.Black,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )
    }
}