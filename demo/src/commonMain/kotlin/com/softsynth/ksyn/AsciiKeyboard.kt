package com.softsynth.ksyn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.unit.dp
import com.softsynth.ksyn.math.AudioMath

/**
 * TODO: The OS intercepts focus rendering this approach silent on target Desktop/Web builds.
 * A Multiplatform Compose ASCII Keyboard component.
 * Listens for key presses matching "zsxdcvgbhnjm,l.;/" and maps them to frequencies
 * to trigger noteOn/noteOff events.
 */
@Composable
fun AsciiKeyboard(
    modifier: Modifier = Modifier,
    onNoteOn: (frequency: Double) -> Unit,
    onNoteOff: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardMap = "zsxdcvgbhnjm,l.;/"
    val activeKeys = remember { mutableSetOf<Char>() }

    var lastPressedChar by remember { mutableStateOf<Char?>('?') }
    var lastPitch by remember { mutableStateOf<Double?>(null) }
    var lastFreq by remember { mutableStateOf<Double?>(null) }
    var lastEventRaw by remember { mutableStateOf<String>("No events") }

    // Request focus immediately so keyboard events map to this component.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .focusRequester(focusRequester)
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusRequester.requestFocus()
            }
            .onPreviewKeyEvent { event ->
                val char = event.utf16CodePoint.toChar().lowercaseChar()
                val index = keyboardMap.indexOf(char)
                
                lastEventRaw = "type:${event.type} char:'$char' code:${event.utf16CodePoint}"

                if (index != -1) {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (activeKeys.add(char)) {
                                val pitch = index + 48.0
                                val freq = AudioMath.pitchToFrequency(pitch)
                                lastPressedChar = char
                                lastPitch = pitch
                                lastFreq = freq
                                onNoteOn(freq)
                            }
                            true
                        }
                        KeyEventType.KeyUp -> {
                            if (activeKeys.remove(char)) {
                                if (activeKeys.isEmpty()) {
                                    lastPressedChar = '?'
                                    lastPitch = null
                                    lastFreq = null
                                    onNoteOff()
                                }
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        val f = lastFreq
        val dbgText = if (f != null) {
            "Pressed: '$lastPressedChar' | Pitch: ${lastPitch} | Freq: ${kotlin.math.round(f * 100) / 100.0}Hz\n[$lastEventRaw]"
        } else {
            "Click to Focus.\nPlay keys: z(C) s(C#) x(D) d(D#) ...\n[$lastEventRaw]"
        }
        Text(
            text = dbgText,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
