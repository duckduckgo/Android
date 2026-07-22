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

package com.duckduckgo.url.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.url.DaxUrlResolver
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealDaxUrlProviderTest {

    private val feature: UnifiedUrlModelFeature = mock()
    private val toggle: Toggle = mock()
    private lateinit var provider: RealDaxUrlProvider

    @Before
    fun setup() {
        whenever(feature.useUnifiedUrl()).thenReturn(toggle)
        provider = RealDaxUrlProvider(feature)
    }

    @After
    fun tearDown() {
        DaxUrlResolver.reset()
    }

    @Test
    fun whenFlagEnabledThenReturnsNewModel() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertTrue(provider.getUrl("example.com") is NewDaxUrl)
    }

    @Test
    fun whenFlagDisabledThenReturnsLegacyModel() {
        whenever(toggle.isEnabled()).thenReturn(false)
        assertTrue(provider.getUrl("example.com") is LegacyDaxUrl)
    }

    @Test
    fun whenSameDomainRequestedTwiceThenReturnsCachedInstance() {
        whenever(toggle.isEnabled()).thenReturn(false)
        assertSame(provider.getUrl("example.com"), provider.getUrl("example.com"))
    }

    @Test
    fun whenMatchingThenAppliesSameOrSubdomainSemantics() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val model = provider.getUrl("example.com")
        assertTrue(model.matches("https://example.com"))
        assertTrue(model.matches("https://sub.example.com"))
        assertFalse(model.matches("https://other.com"))
        assertFalse(model.matches("https://notexample.com"))
    }

    @Test
    fun whenProviderInstalledThenFeatureExceptionResolvesUrl() {
        whenever(toggle.isEnabled()).thenReturn(false)
        DaxUrlResolver.install(provider)

        val exception = FeatureException(domain = "example.com", reason = null)

        assertTrue(exception.url!!.matches("https://sub.example.com"))
    }

    @Test
    fun whenProviderNotInstalledThenFeatureExceptionUrlIsNull() {
        DaxUrlResolver.reset()
        assertFalse(FeatureException(domain = "example.com", reason = null).url != null)
    }
}
