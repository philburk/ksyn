package com.softsynth.ksyn.engine;

import kotlinx.coroutines.runBlocking
import com.softsynth.ksyn.KSyn;
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.Multiply
import com.softsynth.ksyn.unitgen.SawtoothOscillator
import kotlinx.coroutines.launch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

public class TestEngine {

    @Test
    fun testCreateSynthesizer() {
        val synth = KSyn.createSynthesizer()
        assertNotNull(synth)
        assertEquals(44100, synth.frameRate)
        assertEquals(0, synth.frameCount)
    }

    @Test
    fun testMultiplyUnit() = runBlocking {
        val synth = SynthesisEngine()
        val multiplier = Multiply()
        synth.add(multiplier)
        synth.start()
        multiplier.start()
        multiplier.inputA.set(2.0)
        multiplier.inputB.set(3.0)
        assertEquals(0.0, multiplier.output.get(0))
        launch {
            synth.generateNextBuffer()
        }.join()
        assertEquals(6.0, multiplier.output.getValues()[0])
    }

    @Test
    fun testDelayedSet() = runBlocking {
        val synth = SynthesisEngine()
        val multiplier = Multiply()
        synth.add(multiplier)
        synth.start()
        multiplier.start()
        val future = TimeStamp(synth.currentTime).makeRelative(0.01)
        multiplier.inputA.set(2.0, future)
        multiplier.inputB.set(3.0, future)
        val expected = 6.0
        // Have to generate buffers until we reach the future timestamp to get the delayed result.
        assertEquals(0.0, multiplier.output.get(0))
        launch {
            var counter = 0
            while (synth.currentTime < future.time) {
                synth.generateNextBuffer()
                assertEquals(0.0, multiplier.output.getValues()[0])
                counter++
                assertTrue(counter < 100) // Don't run wild.
            }
        }.join()
        // This next run will process the delayed set() commands.
        launch {
            synth.generateNextBuffer()
        }.join()
        val data = multiplier.output.getValues()
        for (i in 0 until data.size) {
            assertEquals(expected, data[i])
        }
        assertEquals(expected, multiplier.output.value)
    }

    @Test
    fun testSawtoothOscillator() = runBlocking {
        val synth = SynthesisEngine()
        val multiplier = Multiply()
        val sawtooth = SawtoothOscillator()
        synth.add(multiplier)
        synth.add(sawtooth)
        multiplier.output.connect(sawtooth.frequency)
        synth.start()
        sawtooth.start()
        multiplier.inputA.set(200.0)
        multiplier.inputB.set(3.0)
        var zeroCrossings = 0
        var lastValue = 0.0
        var numFrames = 0L
        launch {
            for (i in 0 until 1000) {
                synth.generateNextBuffer()
                val data = sawtooth.output.getValues()
                for (i in 0 until data.size) {
                    if (lastValue < 0.0 && data[i] >= 0.0) {
                        zeroCrossings++
                    }
                    lastValue = data[i]
                }
                numFrames += data.size
            }
        }.join()
        assertTrue(numFrames > 0)
        assertTrue(zeroCrossings > 0)
        assertEquals(numFrames, synth.frameCount)
        assertTrue(zeroCrossings < numFrames)
        val frequency = 600.0
        val expectedZeroCrossings = (numFrames * frequency / synth.frameRate).toInt()
        assertEquals(expectedZeroCrossings, zeroCrossings)
    }

}
