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

package com.duckduckgo.duckchat.impl.terms

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class RealDuckAiTermsConsentTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val termsRepository: DuckAiTermsRepository = mock()
    private val feature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java, ioDispatcher = coroutineRule.testDispatcher)

    private val testee = RealDuckAiTermsConsent(feature, termsRepository, coroutineRule.testScope)

    @Before
    fun setUp() {
        feature.nativeToSConsent().setRawStoredState(Toggle.State(enable = true))
    }

    @Test
    fun whenFlagIsOnThenPayloadCarriesTermsAcceptedAndAcceptanceIsStored() = runTest {
        val params = JSONObject()

        testee.carry(params, BrowserMode.REGULAR)
        coroutineRule.testScope.advanceUntilIdle()

        assertTrue(params.getBoolean("termsAccepted"))
        verify(termsRepository).markTermsAccepted(BrowserMode.REGULAR)
    }

    @Test
    fun whenFlagIsOffThenPayloadIsUntouchedAndNothingIsStored() = runTest {
        feature.nativeToSConsent().setRawStoredState(Toggle.State(enable = false))
        val params = JSONObject()

        testee.carry(params, BrowserMode.REGULAR)
        coroutineRule.testScope.advanceUntilIdle()

        assertFalse(params.has("termsAccepted"))
        verify(termsRepository, never()).markTermsAccepted(BrowserMode.REGULAR)
    }

    @Test
    fun whenInFireModeThenAcceptanceIsStoredForFireMode() = runTest {
        testee.carry(JSONObject(), BrowserMode.FIRE)
        coroutineRule.testScope.advanceUntilIdle()

        verify(termsRepository).markTermsAccepted(BrowserMode.FIRE)
    }
}
