/*
 * Copyright (c) 2025 DuckDuckGo
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

class RealPirWebViewCountProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockPirRemoteFeatures: PirRemoteFeatures = mock()
    private val mockSelfToggle: Toggle = mock()

    private lateinit var testee: RealPirWebViewCountProvider

    @Before
    fun setUp() {
        whenever(mockPirRemoteFeatures.self()).thenReturn(mockSelfToggle)
        testee = RealPirWebViewCountProvider(
            pirRemoteFeatures = mockPirRemoteFeatures,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenSettingsNullThenReturnsDefault() = runTest {
        whenever(mockSelfToggle.getSettings()).thenReturn(null)

        assertEquals(20, testee.getMaxWebViewCount())
    }

    @Test
    fun whenSettingsMissingKeyThenReturnsDefault() = runTest {
        whenever(mockSelfToggle.getSettings()).thenReturn("{}")

        assertEquals(20, testee.getMaxWebViewCount())
    }

    @Test
    fun whenSettingsMalformedJsonThenReturnsDefault() = runTest {
        whenever(mockSelfToggle.getSettings()).thenReturn("not-json")

        assertEquals(20, testee.getMaxWebViewCount())
    }

    @Test
    fun whenSettingsValueNotAnIntegerThenReturnsDefault() = runTest {
        whenever(mockSelfToggle.getSettings()).thenReturn("""{"detachedWebViewCount": "abc"}""")

        assertEquals(20, testee.getMaxWebViewCount())
    }

    @Test
    fun whenValidInRangeValueThenReturnsValue() = runTest {
        whenever(mockSelfToggle.getSettings()).thenReturn("""{"detachedWebViewCount": 8}""")

        assertEquals(8, testee.getMaxWebViewCount())
    }

    @Test
    fun whenValueBelowMinThenClampedToMin() = runTest {
        whenever(mockSelfToggle.getSettings()).thenReturn("""{"detachedWebViewCount": 0}""")

        assertEquals(1, testee.getMaxWebViewCount())
    }

    @Test
    fun whenValueNegativeThenClampedToMin() = runTest {
        whenever(mockSelfToggle.getSettings()).thenReturn("""{"detachedWebViewCount": -5}""")

        assertEquals(1, testee.getMaxWebViewCount())
    }

    @Test
    fun whenValueAboveCeilingThenClampedToCeiling() = runTest {
        whenever(mockSelfToggle.getSettings()).thenReturn("""{"detachedWebViewCount": 100}""")

        assertEquals(40, testee.getMaxWebViewCount())
    }

    @Test
    fun whenValueEqualsCeilingThenReturnsCeiling() = runTest {
        whenever(mockSelfToggle.getSettings()).thenReturn("""{"detachedWebViewCount": 40}""")

        assertEquals(40, testee.getMaxWebViewCount())
    }
}
