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

package com.duckduckgo.malicioussiteprotection.impl.domain

import com.duckduckgo.malicioussiteprotection.impl.MaliciousSiteProtectionRCFeature
import com.duckduckgo.url.test.FakeDaxUrlProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RealUrlCanonicalizationTest {

    private val rcFeature: MaliciousSiteProtectionRCFeature = mock()
    private val daxUrlProvider = FakeDaxUrlProvider()
    private val testee = RealUrlCanonicalization(rcFeature, daxUrlProvider)

    @Test
    fun whenStripWWWPrefixDisabledThenCanonicalHostKeepsWWW() {
        whenever(rcFeature.stripWWWPrefix()).thenReturn(false)

        val canonical = testee.canonicalize(daxUrlProvider.getUrl("https://www.example.com/path"))

        assertEquals("www.example.com", canonical.host)
        assertEquals("https://www.example.com/path", canonical.serialized)
    }

    @Test
    fun whenStripWWWPrefixEnabledThenCanonicalHostDropsWWW() {
        whenever(rcFeature.stripWWWPrefix()).thenReturn(true)

        val canonical = testee.canonicalize(daxUrlProvider.getUrl("https://www.example.com/path"))

        assertEquals("example.com", canonical.host)
        assertEquals("https://example.com/path", canonical.serialized)
    }
}
