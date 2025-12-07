package com.softsynth.ksyn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import com.softsynth.ksyn.JSyn


/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class TestJSyn {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun versionIsReadable() {
        assertTrue(JSyn.VERSION_CODE >= (17 shl 16))
    }

    @Test
    fun createSynthesizer() {
        val synth = JSyn.createSynthesizer()
        assertTrue(synth.isRunning == false)
    }
}