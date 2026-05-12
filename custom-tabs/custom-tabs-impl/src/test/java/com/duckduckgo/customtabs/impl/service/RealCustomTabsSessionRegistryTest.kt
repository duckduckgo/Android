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

package com.duckduckgo.customtabs.impl.service

import androidx.browser.customtabs.CustomTabsSessionToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock

class RealCustomTabsSessionRegistryTest {

    private val testee = RealCustomTabsSessionRegistry()

    @Test
    fun whenSessionRecordedThenLookupReturnsPackage() {
        val token = mock<CustomTabsSessionToken>()
        testee.recordSession(token, "com.example.app")
        assertEquals("com.example.app", testee.lookupClientPackage(token))
    }

    @Test
    fun whenUnknownTokenLookedUpThenNullIsReturned() {
        val token = mock<CustomTabsSessionToken>()
        assertNull(testee.lookupClientPackage(token))
    }

    @Test
    fun whenSessionClearedThenLookupReturnsNull() {
        val token = mock<CustomTabsSessionToken>()
        testee.recordSession(token, "com.example.app")
        testee.clearSession(token)
        assertNull(testee.lookupClientPackage(token))
    }
}
