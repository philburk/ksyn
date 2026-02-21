package com.softsynth.ksyn

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.unitgen.UnitGenerator

import androidx.compose.runtime.key

/**
 * A generic Composable that inspects a UnitGenerator and automatically
 * generates a PortFader for every UnitInputPort it has.
 */
@Composable
fun UnitGeneratorFaders(
    unitGenerator: UnitGenerator,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Retrieve all UnitPorts from the UnitGenerator
        for (port in unitGenerator.getPorts()) {
            // Only generate faders for Input ports
            if (port is UnitInputPort) {
                // Minimum and Maximum properties are stored as AudioSamples, we cast them to Float
                val minValue = port.minimum.toFloat()
                val maxValue = port.maximum.toFloat()
                
                // Use exponential taper if the minimum bound is strictly greater than 0
                val isExponential = minValue > 0f
                
                // Add the fader
                key(unitGenerator, port.name) {
                    PortFader(
                        port = port,
                        minValue = minValue,
                        maxValue = maxValue,
                        isExponential = isExponential,
                        name = port.name
                    )
                }
            }
        }
    }
}
