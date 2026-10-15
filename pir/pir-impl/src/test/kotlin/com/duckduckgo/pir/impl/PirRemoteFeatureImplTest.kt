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

package com.duckduckgo.pir.impl

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.pir.api.dashboard.PirFeatureState
import com.duckduckgo.pir.impl.store.PirRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PirRemoteFeatureImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val pirRemoteFeatures: PirRemoteFeatures = mock()
    private val pirBetaToggle: Toggle = mock()
    private val freemiumToggle: Toggle = mock()
    private val pirRepository: PirRepository = mock()

    private lateinit var testee: PirRemoteFeatureImpl

    @Before
    fun setUp() = runTest {
        whenever(pirRemoteFeatures.pirBeta()).thenReturn(pirBetaToggle)
        whenever(pirRemoteFeatures.freemium()).thenReturn(freemiumToggle)
        whenever(pirRepository.isRepositoryAvailable()).thenReturn(true)

        testee = PirRemoteFeatureImpl(
            pirRemoteFeatures = pirRemoteFeatures,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            pirRepository = pirRepository,
        )
    }

    @Test
    fun whenPirBetaIsOffButFreemiumIsOnThenFeatureIsEnabled() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(false)
        whenever(freemiumToggle.isEnabled()).thenReturn(true)

        assertEquals(PirFeatureState.ENABLED, testee.getPirFeatureState())
    }

    @Test
    fun whenPirBetaAndFreemiumAreOffThenFeatureIsDisabled() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(false)
        whenever(freemiumToggle.isEnabled()).thenReturn(false)

        assertEquals(PirFeatureState.DISABLED, testee.getPirFeatureState())
    }

    @Test
    fun whenPirBetaIsOnThenFeatureIsEnabled() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(true)
        whenever(freemiumToggle.isEnabled()).thenReturn(false)

        assertEquals(PirFeatureState.ENABLED, testee.getPirFeatureState())
    }

    @Test
    fun whenRepositoryIsUnavailableThenFeatureIsNotAvailable() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(true)
        whenever(pirRepository.isRepositoryAvailable()).thenReturn(false)

        assertEquals(PirFeatureState.NOT_AVAILABLE, testee.getPirFeatureState())
    }
}
