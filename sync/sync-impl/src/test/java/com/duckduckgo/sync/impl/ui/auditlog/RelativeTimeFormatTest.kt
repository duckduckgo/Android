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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.sync.impl.ui.auditlog.RelativeTime.Days
import com.duckduckgo.sync.impl.ui.auditlog.RelativeTime.Hours
import com.duckduckgo.sync.impl.ui.auditlog.RelativeTime.JustNow
import com.duckduckgo.sync.impl.ui.auditlog.RelativeTime.Minutes
import com.duckduckgo.sync.impl.ui.auditlog.RelativeTime.Months
import com.duckduckgo.sync.impl.ui.auditlog.RelativeTime.Years
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelativeTimeFormatTest {

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun whenJustNowThenJustNow() {
        assertEquals("Just now", JustNow.format(resources))
    }

    @Test
    fun whenOneOfEachUnitThenSingular() {
        assertEquals("1 minute ago", Minutes(1).format(resources))
        assertEquals("1 hour ago", Hours(1).format(resources))
        assertEquals("1 day ago", Days(1).format(resources))
        assertEquals("1 month ago", Months(1).format(resources))
        assertEquals("1 year ago", Years(1).format(resources))
    }

    @Test
    fun whenManyOfEachUnitThenPlural() {
        assertEquals("5 minutes ago", Minutes(5).format(resources))
        assertEquals("5 hours ago", Hours(5).format(resources))
        assertEquals("5 days ago", Days(5).format(resources))
        assertEquals("5 months ago", Months(5).format(resources))
        assertEquals("5 years ago", Years(5).format(resources))
    }
}
