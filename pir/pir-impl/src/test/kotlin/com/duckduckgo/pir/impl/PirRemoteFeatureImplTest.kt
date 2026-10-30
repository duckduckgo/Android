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
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
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
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val pirRemoteFeatures = FakeFeatureToggleFactory.create(PirRemoteFeatures::class.java)
    private val pirRepository: PirRepository = mock()

    private lateinit var testee: PirRemoteFeatureImpl

    @Before
    fun setUp() = runTest {
        pirRemoteFeatures.pirBeta().setRawStoredState(State(enable = true))
        whenever(pirRepository.isRepositoryAvailable()).thenReturn(true)

        testee = PirRemoteFeatureImpl(
            pirRemoteFeatures = pirRemoteFeatures,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            pirRepository = pirRepository,
        )
    }

    @Test
    fun whenPirBetaDisabledThenDisabled() = runTest {
        pirRemoteFeatures.pirBeta().setRawStoredState(State(enable = false))

        assertEquals(PirFeatureState.Disabled, testee.getPirFeatureState())
    }

    @Test
    fun whenRepositoryUnavailableThenNotAvailable() = runTest {
        whenever(pirRepository.isRepositoryAvailable()).thenReturn(false)

        assertEquals(PirFeatureState.NotAvailable, testee.getPirFeatureState())
    }

    @Test
    fun whenValidProfileStoredThenEnabledWithUserProfile() = runTest {
        whenever(pirRepository.hasValidUserProfileQueries()).thenReturn(true)

        assertEquals(PirFeatureState.Enabled(hasUserProfile = true), testee.getPirFeatureState())
    }

    @Test
    fun whenNoValidProfileStoredThenEnabledWithoutUserProfile() = runTest {
        whenever(pirRepository.hasValidUserProfileQueries()).thenReturn(false)

        assertEquals(PirFeatureState.Enabled(hasUserProfile = false), testee.getPirFeatureState())
    }

    @Test
    fun whenReadingProfileFailsThenNotAvailable() = runTest {
        whenever(pirRepository.hasValidUserProfileQueries()).thenThrow(IllegalStateException("query failed"))

        assertEquals(PirFeatureState.NotAvailable, testee.getPirFeatureState())
    }
}
