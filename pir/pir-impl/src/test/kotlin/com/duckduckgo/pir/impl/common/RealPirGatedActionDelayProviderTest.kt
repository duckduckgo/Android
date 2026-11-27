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

package com.duckduckgo.pir.impl.common

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.pir.impl.PirRemoteFeatures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealPirGatedActionDelayProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockPirRemoteFeatures: PirRemoteFeatures = mock()
    private val mockSelfToggle: Toggle = mock()

    private lateinit var testee: RealPirGatedActionDelayProvider

    @Before
    fun setUp() {
        whenever(mockPirRemoteFeatures.self()).thenReturn(mockSelfToggle)
        testee = RealPirGatedActionDelayProvider(
            pirRemoteFeatures = mockPirRemoteFeatures,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenSettingsNullThenReturnsDefault() = runTest {
        whenever(mockSelfToggle.getSettings()).thenReturn(null)

        assertEquals(3_000L, testee.getGatedActionDelayMs())
    }

    @Test
    fun whenSettingsMissingKeyThenReturnsDefault() = runTest {
        whenever(mockSelfToggle.getSettings()).thenReturn("""{"detachedWebViewCount": 8}""")

        assertEquals(3_000L, testee.getGatedActionDelayMs())
    }

    @Test
    fun whenSettingsMalformedJsonThenReturnsDefault() = runTest {
        whenever(mockSelfToggle.getSettings()).thenReturn("not-json")

        assertEquals(3_000L, testee.getGatedActionDelayMs())
    }

    @Test
    fun whenSettingsValueNotANumberThenReturnsDefault() = runTest {
        whenever(mockSelfToggle.getSettings()).thenReturn("""{"gatedActionDelayMs": "abc"}""")

        assertEquals(3_000L, testee.getGatedActionDelayMs())
    }

    @Test
    fun whenValidInRangeValueThenReturnsValue() = runTest {
        whenever(mockSelfToggle.getSettings()).thenReturn("""{"gatedActionDelayMs": 1500}""")

        assertEquals(1_500L, testee.getGatedActionDelayMs())
    }

    @Test
    fun whenValueZeroThenReturnsZero() = runTest {
        whenever(mockSelfToggle.getSettings()).thenReturn("""{"gatedActionDelayMs": 0}""")

        assertEquals(0L, testee.getGatedActionDelayMs())
    }

    @Test
    fun whenValueNegativeThenClampedToZero() = runTest {
        whenever(mockSelfToggle.getSettings()).thenReturn("""{"gatedActionDelayMs": -500}""")

        assertEquals(0L, testee.getGatedActionDelayMs())
    }

    @Test
    fun whenValueAboveCeilingThenClampedToCeiling() = runTest {
        whenever(mockSelfToggle.getSettings()).thenReturn("""{"gatedActionDelayMs": 60000}""")

        assertEquals(15_000L, testee.getGatedActionDelayMs())
    }
}
