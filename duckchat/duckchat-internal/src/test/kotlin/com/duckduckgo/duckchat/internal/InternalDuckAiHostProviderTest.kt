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

package com.duckduckgo.duckchat.internal

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.internal.store.DuckAiInternalSettingsDataStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class InternalDuckAiHostProviderTest {

    private val mockDataStore: DuckAiInternalSettingsDataStore = mock()

    private fun createProvider(customUrl: String? = null): InternalDuckAiHostProvider {
        whenever(mockDataStore.customUrl).thenReturn(customUrl)
        return InternalDuckAiHostProvider(mockDataStore)
    }

    @Test
    fun whenNoCustomUrlThenGetHostReturnsDefault() {
        val provider = createProvider(customUrl = null)

        assertEquals("duck.ai", provider.getHost())
    }

    @Test
    fun whenCustomUrlWithSchemeThenGetHostReturnsCustomHost() {
        val provider = createProvider(customUrl = "https://staging.duck.ai")

        assertEquals("staging.duck.ai", provider.getHost())
    }

    @Test
    fun whenCustomUrlHasNoSchemeThenHostIsExtracted() {
        val provider = createProvider(customUrl = "staging.duck.ai")

        assertEquals("staging.duck.ai", provider.getHost())
    }

    @Test
    fun whenCustomUrlHasPathThenOnlyHostIsExtracted() {
        val provider = createProvider(customUrl = "https://staging.duck.ai/chat")

        assertEquals("staging.duck.ai", provider.getHost())
    }

    @Test
    fun whenSetCustomUrlCalledThenHostUpdatesImmediately() {
        val provider = createProvider(customUrl = null)
        assertEquals("duck.ai", provider.getHost())

        provider.setCustomUrl("https://staging.duck.ai")

        assertEquals("staging.duck.ai", provider.getHost())
    }

    @Test
    fun whenSetCustomUrlToNullThenHostReverts() {
        val provider = createProvider(customUrl = "https://staging.duck.ai")
        assertEquals("staging.duck.ai", provider.getHost())

        provider.setCustomUrl(null)

        assertEquals("duck.ai", provider.getHost())
    }

    @Test
    fun whenSetCustomUrlCalledThenDataStoreIsUpdated() {
        val provider = createProvider(customUrl = null)

        provider.setCustomUrl("https://staging.duck.ai")

        verify(mockDataStore).customUrl = "https://staging.duck.ai"
    }

    @Test
    fun whenGetCustomUrlCalledThenDelegatesToDataStore() {
        val provider = createProvider(customUrl = "https://staging.duck.ai")

        assertEquals("https://staging.duck.ai", provider.getCustomUrl())
    }

    @Test
    fun whenCustomUrlIsBlankThenGetHostReturnsDefault() {
        val provider = createProvider(customUrl = null)

        assertEquals("duck.ai", provider.getHost())
    }

    @Test
    fun whenGetCustomUrlAndNoneSetThenReturnsNull() {
        val provider = createProvider(customUrl = null)

        assertNull(provider.getCustomUrl())
    }
}
