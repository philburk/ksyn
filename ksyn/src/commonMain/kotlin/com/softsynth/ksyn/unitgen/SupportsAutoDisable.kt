/*
 * Copyright 2026 Phil Burk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.softsynth.ksyn.unitgen

/**
 * Implemented by units that can disable another unit when they finish,
 * for example when an envelope or sample ends or a string becomes quiet.
 * This can be used to reduce CPU load.
 *
 * Test for this interface instead of checking for specific classes:
 *
 * <pre>
 * if (unit is SupportsAutoDisable) {
 *     unit.setupAutoDisable(voice)
 *     ...
 *     unit.setAutoDisableEnabled(false) // run continuously
 *     unit.setAutoDisableEnabled(true)  // disable the voice again when finished
 * }
 * </pre>
 */
interface SupportsAutoDisable {
    /** @return the unit that implements this interface */
    fun getUnitGenerator(): UnitGenerator

    /** True if the target unit will be disabled when this unit finishes. */
    val isAutoDisableEnabled: Boolean

    /**
     * Specify a unit to be disabled when this unit finishes, and turn auto disable on.
     * The target unit starts off disabled until this unit is started, for example by a note on.
     *
     * @param unit the UnitGenerator, such as a Circuit or voice, to disable. Defaults to this unit.
     */
    fun setupAutoDisable(unit: UnitGenerator = getUnitGenerator())

    /**
     * Turn auto disable on or off. The target unit from [setupAutoDisable] is remembered.
     * If it was never set then the target is this unit.
     *
     * Turning it on does not disable the target right away, so a sound in progress is not cut off.
     * The target is disabled the next time this unit finishes.
     *
     * Turning it off enables the target, so that it runs continuously.
     */
    fun setAutoDisableEnabled(enabled: Boolean)
}
