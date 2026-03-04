package com.softsynth.ksyn

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.unitgen.UnitGenerator

/**
 * A generic Composable that inspects a UnitGenerator and automatically
 * generates a PortFader for every UnitInputPort it has.
 */
@Composable
fun UnitGeneratorFaders(
    unitGenerator: UnitGenerator,
    presetKey: Int = 0,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Retrieve all UnitPorts from the UnitGenerator
        for (port in unitGenerator.getPorts()) {
            // Only generate faders for unconnected Input ports.
            // If a port is driven by another unit, setting it has no effect.
            if (port is UnitInputPort && !port.isConnected()) {
                // Minimum and Maximum properties are stored as AudioSamples, we cast them to Float
                val minValue = port.minimum.toFloat()
                val maxValue = port.maximum.toFloat()
                
                // Use exponential taper if the minimum bound is strictly greater than 0
                val isExponential = minValue > 0f
                
                // Add the fader, and include presetKey so switching presets recreates the faders 
                // and pulls initial values from the ports again.
                key(unitGenerator, port.name, presetKey) {
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
