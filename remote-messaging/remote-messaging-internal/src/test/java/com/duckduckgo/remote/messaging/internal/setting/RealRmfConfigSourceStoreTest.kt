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

package com.duckduckgo.remote.messaging.internal.setting

import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class RealRmfConfigSourceStoreTest {

    private val store = RealRmfConfigSourceStore(FakeSharedPreferencesProvider())

    @Test
    fun whenNothingStoredThenDefaultsReturned() {
        assertEquals(RmfConfigMode.PRODUCTION, store.mode)
        assertEquals("", store.prNumber)
        assertEquals("", store.customUrl)
    }

    @Test
    fun whenModeSetThenPersisted() {
        store.mode = RmfConfigMode.PR_NUMBER

        assertEquals(RmfConfigMode.PR_NUMBER, store.mode)
    }

    @Test
    fun whenPrNumberSetThenPersisted() {
        store.prNumber = "387"

        assertEquals("387", store.prNumber)
    }

    @Test
    fun whenCustomUrlSetThenPersisted() {
        store.customUrl = "https://example.com/android-config.json"

        assertEquals("https://example.com/android-config.json", store.customUrl)
    }
}
