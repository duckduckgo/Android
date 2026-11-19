/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.auditlog

import com.duckduckgo.sync.impl.ui.auditlog.RelativeTime.Days
import com.duckduckgo.sync.impl.ui.auditlog.RelativeTime.Hours
import com.duckduckgo.sync.impl.ui.auditlog.RelativeTime.JustNow
import com.duckduckgo.sync.impl.ui.auditlog.RelativeTime.Minutes
import com.duckduckgo.sync.impl.ui.auditlog.RelativeTime.Months
import com.duckduckgo.sync.impl.ui.auditlog.RelativeTime.Years
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RelativeTimeTest {

    @Test
    fun whenUnderOneMinuteThenJustNow() {
        assertEquals(JustNow, ago(Duration.ofSeconds(59)))
    }

    @Test
    fun whenInTheFutureThenJustNow() {
        assertEquals(JustNow, ago(Duration.ofMinutes(-5)))
    }

    @Test
    fun whenUnderOneHourThenMinutes() {
        assertEquals(Minutes(1), ago(Duration.ofSeconds(60)))
        assertEquals(Minutes(59), ago(Duration.ofMinutes(59).plusSeconds(59)))
    }

    @Test
    fun whenUnderOneDayThenHours() {
        assertEquals(Hours(1), ago(Duration.ofHours(1)))
        assertEquals(Hours(23), ago(Duration.ofHours(23).plusMinutes(59)))
    }

    @Test
    fun whenUnderThirtyDaysThenDays() {
        assertEquals(Days(1), ago(Duration.ofDays(1)))
        assertEquals(Days(29), ago(Duration.ofDays(29).plusHours(23)))
    }

    @Test
    fun whenUnderOneYearThenMonths() {
        assertEquals(Months(1), ago(Duration.ofDays(30)))
        assertEquals(Months(2), ago(Duration.ofDays(60)))
        assertEquals(Months(12), ago(Duration.ofDays(364)))
    }

    @Test
    fun whenOneYearOrMoreThenYears() {
        assertEquals(Years(1), ago(Duration.ofDays(365)))
        assertEquals(Years(2), ago(Duration.ofDays(730)))
    }

    private fun ago(duration: Duration): RelativeTime = RelativeTime.between(occurredAt = NOW.minus(duration), now = NOW)

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-12T12:00:00Z")
    }
}
