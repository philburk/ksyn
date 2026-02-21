package com.softsynth.ksyn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softsynth.ksyn.ports.UnitInputPort
import kotlin.math.ln
import kotlin.math.exp

/**
 * A general-purpose fader (slider) that can control a UnitInputPort.
 * It supports both linear and exponential tapers.
 */
@Composable
fun PortFader(
    port: UnitInputPort,
    minValue: Float = 0.0f,
    maxValue: Float = 1.0f,
    isExponential: Boolean = false,
    name: String = port.name,
    modifier: Modifier = Modifier
) {
    // Determine the initial value appropriately bounded
    val initialValue = port.get(0).toFloat().coerceIn(minValue, maxValue)

    // Calculate the initial slider value (0.0 to 1.0)
    val initialSliderValue = if (isExponential) {
        if (initialValue <= 0f || minValue <= 0f) {
            // Cannot use exponential with 0 or negative values, fallback to linear
            (initialValue - minValue) / (maxValue - minValue)
        } else {
            // value = min * (max/min)^slider
            // ln(value / min) = slider * ln(max / min)
            // slider = ln(value / min) / ln(max / min)
            (ln(initialValue / minValue) / ln(maxValue / minValue)).toFloat()
        }
    } else {
        (initialValue - minValue) / (maxValue - minValue)
    }

    var sliderPosition by remember { mutableFloatStateOf(initialSliderValue.coerceIn(0.0f, 1.0f)) }
    var currentValue by remember { mutableFloatStateOf(initialValue) }

    Column(modifier = modifier.padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "$name: ")
            // Format to 2 decimal places for cleaner display
            val displayValue = kotlin.math.round(currentValue * 100f) / 100f
            Text(text = displayValue.toString())
        }

        Slider(
            value = sliderPosition,
            onValueChange = { newSliderValue ->
                sliderPosition = newSliderValue
                
                // Calculate the actual value based on taper
                val newValue = if (isExponential && minValue > 0f) {
                    // value = min * exp(slider * ln(max/min))
                    (minValue * exp(newSliderValue * ln(maxValue / minValue))).toFloat()
                } else {
                    minValue + (newSliderValue * (maxValue - minValue))
                }
                
                currentValue = newValue
                port.set(newValue)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
