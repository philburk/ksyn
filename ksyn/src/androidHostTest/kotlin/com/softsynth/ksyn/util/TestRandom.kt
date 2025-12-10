package com.softsynth.ksyn.util

import kotlinx.coroutines.runBlocking
import com.softsynth.ksyn.KSyn;
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.toSample
import com.softsynth.ksyn.unitgen.LineOut
import com.softsynth.ksyn.unitgen.Multiply
import com.softsynth.ksyn.unitgen.SawtoothOscillator
import kotlinx.coroutines.launch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestRandom {
    @Test
    fun testInitialSeedIsRandom() {
        val r1 = PseudoRandom()
        val r2 = PseudoRandom()
        val r3 = PseudoRandom()
        val r1s = r1.nextRandomSample()
        val r2s = r2.nextRandomSample()
        val r3s = r3.nextRandomSample()
        assertTrue((r1s != r2s) && (r1s != r3s))
    }

    @Test
    fun testInitialSeedSetIsDeterministic() {
        val r1 = PseudoRandom(123456)
        val r2 = PseudoRandom(123456)
        val r3 = PseudoRandom(123456)
        val r1s = r1.nextRandomSample()
        val r2s = r2.nextRandomSample()
        val r3s = r3.nextRandomSample()
        assertTrue((r1s == r2s) && (r1s == r3s))
    }
    @Test
    fun testChoose() {
        val r = PseudoRandom()
        val range = 10
        val values = IntArray(range)
        for (i in 0 until 10000) {
            values[r.choose(range)]++
        }
        for (i in 0 until range) {
            assertTrue(values[i] > 0)
        }
    }
}