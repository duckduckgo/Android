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

package com.duckduckgo.duckchat.internal.store

import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DevDuckAiInternalSettingsDataStoreImplTest {

    private lateinit var testee: DevDuckAiInternalSettingsDataStoreImpl

    @Before
    fun setup() {
        testee = DevDuckAiInternalSettingsDataStoreImpl(FakeSharedPreferencesProvider())
    }

    @Test
    fun whenNoCustomUrlSetThenReturnsNull() {
        assertNull(testee.customUrl)
    }

    @Test
    fun whenCustomUrlSetThenReturnsStoredValue() {
        testee.customUrl = "https://staging.duck.ai"

        assertEquals("https://staging.duck.ai", testee.customUrl)
    }

    @Test
    fun whenCustomUrlSetToNullThenReturnsNull() {
        testee.customUrl = "https://staging.duck.ai"
        testee.customUrl = null

        assertNull(testee.customUrl)
    }

    @Test
    fun whenCustomUrlSetToBlankThenReturnsNull() {
        testee.customUrl = "   "

        assertNull(testee.customUrl)
    }

    @Test
    fun whenCustomUrlSetToEmptyStringThenReturnsNull() {
        testee.customUrl = ""

        assertNull(testee.customUrl)
    }

    @Test
    fun whenCustomUrlUpdatedThenReturnsLatestValue() {
        testee.customUrl = "https://staging.duck.ai"
        testee.customUrl = "https://dev.duck.ai"

        assertEquals("https://dev.duck.ai", testee.customUrl)
    }
}
