package com.softsynth.ksyn.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val whiteKeysBeforeNote = intArrayOf(0, 1, 1, 2, 2, 3, 4, 4, 5, 5, 6, 6)

private fun whiteKeysBefore(note: Int): Int {
    val nonNegativeNote = note.coerceAtLeast(0)
    return (nonNegativeNote / 12) * 7 + whiteKeysBeforeNote[nonNegativeNote % 12]
}

/**
 * A visual keyboard widget laid out like a real piano with mathematically correct key spacing.
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

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.Companion.CenterHorizontally
    ) {
        Text(
            text = "Last Pressed: $lastNoteName | $lastNoteNumber",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.Companion.padding(bottom = 16.dp)
        )

        BoxWithConstraints(modifier = Modifier.Companion.fillMaxWidth()) {
            val totalWhiteKeys = whiteKeysBefore(startingNote + numNotes) - whiteKeysBefore(startingNote)
            val startingWhiteKeys = whiteKeysBefore(startingNote)

            val widthPx = constraints.maxWidth
            val heightPx = if (constraints.maxHeight != Constraints.Infinity) {
                constraints.maxHeight
            } else {
                with(LocalDensity.current) { 140.dp.roundToPx() }
            }

            // Calculate pixel boundaries for all white keys to avoid rounding errors
            val whiteKeyLefts = IntArray(totalWhiteKeys)
            val whiteKeyWidths = IntArray(totalWhiteKeys)
            for (index in 0..<totalWhiteKeys) {
                whiteKeyLefts[index] = (widthPx * index) / totalWhiteKeys
                val nextLeft = (widthPx * (index + 1)) / totalWhiteKeys
                whiteKeyWidths[index] = nextLeft - whiteKeyLefts[index]
            }

            val blackKeyHeightPx = (heightPx * 64) / 100

            // Collect the black key note numbers to map measurables correctly
            val blackKeyNoteNumbers = mutableListOf<Int>()
            for (i in 0..<numNotes) {
                val noteNumber = i + startingNote
                val keyIndex = noteNumber % 12
                val noteName = noteNames[keyIndex]
                if (noteName.length > 1) {
                    blackKeyNoteNumbers.add(noteNumber)
                }
            }

            val numWhiteKeys = totalWhiteKeys
            val numBlackKeys = blackKeyNoteNumbers.size

            Layout(
                content = {
                    // White keys first
                    for (i in 0..<numNotes) {
                        val noteNumber = i + startingNote
                        val keyIndex = noteNumber % 12
                        val noteName = noteNames[keyIndex]
                        if (noteName.length == 1) {
                            PianoKey(
                                noteName = noteName,
                                index = noteNumber,
                                isBlack = false,
                                width = with(LocalDensity.current) { 0.dp }, // Layout ignores these widths since we force Constraints.fixed
                                height = with(LocalDensity.current) { 0.dp },
                                onKeyDown = { noteNum ->
                                    lastNoteName = noteName
                                    lastNoteNumber = noteNum
                                    onKeyDown(noteNum)
                                },
                                onKeyUp = onKeyUp
                            )
                        }
                    }
                    // Black keys
                    for (i in 0..<numNotes) {
                        val noteNumber = i + startingNote
                        val keyIndex = noteNumber % 12
                        val noteName = noteNames[keyIndex]
                        if (noteName.length > 1) {
                            PianoKey(
                                noteName = noteName,
                                index = noteNumber,
                                isBlack = true,
                                width = with(LocalDensity.current) { 0.dp }, // Layout ignores these widths since we force Constraints.fixed
                                height = with(LocalDensity.current) { 0.dp },
                                onKeyDown = { noteNum ->
                                    lastNoteName = noteName
                                    lastNoteNumber = noteNum
                                    onKeyDown(noteNum)
                                },
                                onKeyUp = onKeyUp
                            )
                        }
                    }
                },
                modifier = Modifier.Companion.size(
                    width = with(LocalDensity.current) { widthPx.toDp() },
                    height = with(LocalDensity.current) { heightPx.toDp() }
                )
            ) { measurables, childConstraints ->
                val placeables = measurables.mapIndexed { index, measurable ->
                    if (index < numWhiteKeys) {
                        measurable.measure(
                            Constraints.fixed(
                                width = whiteKeyWidths[index],
                                height = heightPx
                            )
                        )
                    } else {
                        val blackKeyIndex = index - numWhiteKeys
                        val noteNumber = blackKeyNoteNumbers[blackKeyIndex]

                        val leftEdgePx = (widthPx * (7 * noteNumber - 12 * startingWhiteKeys)) / (12 * totalWhiteKeys)
                        val nextLeftEdgePx = (widthPx * (7 * noteNumber - 12 * startingWhiteKeys + 8)) / (12 * totalWhiteKeys)
                        val blackKeyWidthPx = nextLeftEdgePx - leftEdgePx

                        measurable.measure(
                            Constraints.fixed(
                                width = blackKeyWidthPx,
                                height = blackKeyHeightPx
                            )
                        )
                    }
                }

                layout(widthPx, heightPx) {
                    placeables.forEachIndexed { index, placeable ->
                        if (index < numWhiteKeys) {
                            placeable.placeRelative(x = whiteKeyLefts[index], y = 0)
                        } else {
                            val blackKeyIndex = index - numWhiteKeys
                            val noteNumber = blackKeyNoteNumbers[blackKeyIndex]
                            val leftEdgePx = (widthPx * (7 * noteNumber - 12 * startingWhiteKeys)) / (12 * totalWhiteKeys)
                            placeable.placeRelative(x = leftEdgePx, y = 0)
                        }
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