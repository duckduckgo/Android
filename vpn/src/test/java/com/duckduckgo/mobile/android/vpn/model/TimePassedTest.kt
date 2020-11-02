/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TimePassedTest {

    @Test
    fun whenOnlyHoursPassedThenPrintsProperTime() {
        val timePassed = TimePassed(1, 0, 0)
        assertEquals("1 hr", timePassed.toString())
    }

    @Test
    fun whenOnlyMinutesPassedThenPrintsProperTime() {
        val timePassed = TimePassed(0, 10, 0)
        assertEquals("10 min", timePassed.toString())
    }

    @Test
    fun whenOnlySecondsPassedThenPrintsProperTime() {
        val timePassed = TimePassed(0, 0, 25)
        assertEquals("25 sec", timePassed.toString())
    }

    @Test
    fun whenHoursAndMinutesPassedThenPrintsProperTime() {
        val timePassed = TimePassed(1, 10, 0)
        assertEquals("1 hr 10 min", timePassed.toString())
    }

    @Test
    fun whenHoursAndSecondsPassedThenPrintsProperTime() {
        val timePassed = TimePassed(1, 0, 30)
        assertEquals("1 hr", timePassed.toString())
    }

    @Test
    fun whenMinutesAndSecondsPassedThenPrintsProperTime() {
        val timePassed = TimePassed(0, 10, 10)
        assertEquals("10 min", timePassed.toString())
    }

}