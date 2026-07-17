package com.softsynth.ksyn

import com.softsynth.audiobridge.AudioResult
import com.softsynth.ksyn.unitgen.ScopeProbe
import com.softsynth.ksyn.unitgen.UnitGenerator

abstract class KSynPlayable {
    abstract fun start(): AudioResult
    abstract fun stop()
    open fun getUnitGenerator(): UnitGenerator? = null
    open fun getScopeProbe(): ScopeProbe? = null
}
