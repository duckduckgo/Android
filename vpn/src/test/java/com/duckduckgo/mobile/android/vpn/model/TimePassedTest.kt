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
    fun whenOnlyHoursPassedThenFormatsProperTime() {
        val timePassed = TimePassed(1, 0, 0)
        assertEquals("1 hr 0 min 0 sec", timePassed.format())
    }

    @Test
    fun whenOnlyMinutesPassedThenFormatsProperTime() {
        val timePassed = TimePassed(0, 10, 0)
        assertEquals("0 hr 10 min 0 sec", timePassed.format())
    }

    @Test
    fun whenOnlySecondsPassedThenFormatsProperTime() {
        val timePassed = TimePassed(0, 0, 25)
        assertEquals("0 hr 0 min 25 sec", timePassed.format())
    }

    @Test
    fun whenHoursAndMinutesPassedThenFormatsProperTime() {
        val timePassed = TimePassed(1, 10, 0)
        assertEquals("1 hr 10 min 0 sec", timePassed.format())
    }

    @Test
    fun whenHoursAndSecondsPassedThenFormatsProperTime() {
        val timePassed = TimePassed(1, 0, 30)
        assertEquals("1 hr 0 min 30 sec", timePassed.format())
    }

    @Test
    fun whenMinutesAndSecondsPassedThenFormatsProperTime() {
        val timePassed = TimePassed(0, 10, 10)
        assertEquals("0 hr 10 min 10 sec", timePassed.format())
    }

    @Test
    fun whenOnlyHoursPassedThenShortFormatsProperTime() {
        val timePassed = TimePassed(1, 0, 0)
        assertEquals("1h ago", timePassed.shortFormat())
    }

    @Test
    fun whenOnlyMinutesPassedThenShortFormatsProperTime() {
        val timePassed = TimePassed(0, 10, 0)
        assertEquals("10m ago", timePassed.shortFormat())
    }

    @Test
    fun whenOnlySecondsPassedThenShortFormatsProperTime() {
        val timePassed = TimePassed(0, 0, 45)
        assertEquals("just now", timePassed.shortFormat())
    }

    @Test
    fun whenOnlyFewSecondsPassedThenShortFormatsProperTime() {
        val timePassed = TimePassed(0, 0, 25)
        assertEquals("just now", timePassed.shortFormat())
    }

    @Test
    fun whenHoursAndMinutesPassedThenShortFormatsProperTime() {
        val timePassed = TimePassed(1, 10, 0)
        assertEquals("1h ago", timePassed.shortFormat())
    }

    @Test
    fun whenHoursAndSecondsPassedThenShortFormatsProperTime() {
        val timePassed = TimePassed(1, 0, 30)
        assertEquals("1h ago", timePassed.shortFormat())
    }

    @Test
    fun whenMinutesAndSecondsPassedShortThenFormatsProperTime() {
        val timePassed = TimePassed(0, 10, 10)
        assertEquals("10m ago", timePassed.shortFormat())
    }
}
