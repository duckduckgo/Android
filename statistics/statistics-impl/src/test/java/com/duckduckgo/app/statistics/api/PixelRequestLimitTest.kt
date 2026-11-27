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

package com.duckduckgo.app.statistics.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PixelRequestLimitTest {

    @Test
    fun whenSettingsNullThenDefault() {
        assertEquals(2, PixelRequestLimit.maxRequestsPerHost(null))
    }

    @Test
    fun whenSettingsMissingKeyThenDefault() {
        assertEquals(2, PixelRequestLimit.maxRequestsPerHost("""{"other": 3}"""))
    }

    @Test
    fun whenSettingsInvalidJsonThenDefault() {
        assertEquals(2, PixelRequestLimit.maxRequestsPerHost("not json"))
    }

    @Test
    fun whenSettingsValueNotANumberThenDefault() {
        assertEquals(2, PixelRequestLimit.maxRequestsPerHost("""{"maxRequestsPerHost": "lots"}"""))
    }

    @Test
    fun whenSettingsValueInRangeThenUsed() {
        assertEquals(1, PixelRequestLimit.maxRequestsPerHost("""{"maxRequestsPerHost": 1}"""))
        assertEquals(4, PixelRequestLimit.maxRequestsPerHost("""{"maxRequestsPerHost": 4}"""))
    }

    @Test
    fun whenSettingsValueBelowMinimumThenClampedToOne() {
        assertEquals(1, PixelRequestLimit.maxRequestsPerHost("""{"maxRequestsPerHost": 0}"""))
    }

    @Test
    fun whenSettingsValueLargeThenUsedUnchanged() {
        assertEquals(50, PixelRequestLimit.maxRequestsPerHost("""{"maxRequestsPerHost": 50}"""))
    }
}
