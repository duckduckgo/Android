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

package com.duckduckgo.autofill.impl.importing.promo

import android.annotation.SuppressLint
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.importing.CredentialImportTimestamp
import com.duckduckgo.autofill.impl.importing.capability.PasswordImportAvailability
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.time.TimeProvider
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.minutes

@SuppressLint("DenyListedApi")
class RealPasswordsInBookmarksImportPromotionTest {

    private val passwordImportAvailability: PasswordImportAvailability = mock()
    private val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)
    private val autofillStore: InternalAutofillStore = mock()
    private val credentialImportTimestamp: CredentialImportTimestamp = mock()
    private var currentTimeMillis = 30.minutes.inWholeMilliseconds
    private val timeProvider = object : TimeProvider {
        override fun currentTimeMillis() = currentTimeMillis
    }

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun setup() = runTest {
        configureAllConditionsToAllowPromo()
    }

    private val testee = RealPasswordsInBookmarksImportPromotion(
        autofillStore = autofillStore,
        autofillFeature = autofillFeature,
        passwordImportAvailability = passwordImportAvailability,
        credentialImportTimestamp = credentialImportTimestamp,
        timeProvider = timeProvider,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenAllConditionsAllowItThenPromoCanShow() = runTest {
        assertTrue(testee.canShowPromotion(importedBookmarksCount = 1))
    }

    @Test
    fun whenNoBookmarksWereImportedThenPromoCannotShow() = runTest {
        assertFalse(testee.canShowPromotion(importedBookmarksCount = 0))
    }

    @Test
    fun whenAutofillTopLevelFeatureDisabledThenPromoCannotShow() = runTest {
        autofillFeature.self().setRawStoredState(Toggle.State(enable = false))
        assertFalse(testee.canShowPromotion(importedBookmarksCount = 1))
    }

    @Test
    fun whenCrossFeaturePromoDisabledThenPromoCannotShow() = runTest {
        autofillFeature.showCrossFeaturePromo().setRawStoredState(Toggle.State(enable = false))
        assertFalse(testee.canShowPromotion(importedBookmarksCount = 1))
    }

    @Test
    fun whenGooglePasswordManagerImportDisabledThenPromoCannotShow() = runTest {
        autofillFeature.canImportFromGooglePasswordManager().setRawStoredState(Toggle.State(enable = false))
        assertFalse(testee.canShowPromotion(importedBookmarksCount = 1))
    }

    @Test
    fun whenPasswordImportNotAvailableThenPromoCannotShow() = runTest {
        configurePasswordImportAvailability(isAvailable = false)
        assertFalse(testee.canShowPromotion(importedBookmarksCount = 1))
    }

    @Test
    fun whenPreviouslyImportedPasswordsThenPromoCanShow() = runTest {
        whenever(autofillStore.hasEverImportedPasswords).thenReturn(true)
        assertTrue(testee.canShowPromotion(importedBookmarksCount = 1))
    }

    @Test
    fun whenSavedPasswordsCountReachesLimitThenPromoCannotShow() = runTest {
        whenever(autofillStore.getCredentialCount()).thenReturn(flowOf(Result.success(9)))
        assertFalse(testee.canShowPromotion(importedBookmarksCount = 1))
    }

    @Test
    fun whenSavedPasswordsCountBelowLimitThenPromoCanShow() = runTest {
        whenever(autofillStore.getCredentialCount()).thenReturn(flowOf(Result.success(8)))
        assertTrue(testee.canShowPromotion(importedBookmarksCount = 1))
    }

    @Test
    fun whenCredentialsCountFailsThenPromoCannotShow() = runTest {
        whenever(autofillStore.getCredentialCount()).thenReturn(flowOf(Result.failure(Exception("test"))))
        assertFalse(testee.canShowPromotion(importedBookmarksCount = 1))
    }

    @Test
    fun whenPasswordsImportFinishedWithinLast15MinutesThenPromoCannotShow() = runTest {
        whenever(credentialImportTimestamp.lastPasswordsImportMillis()).thenReturn(currentTimeMillis - 15.minutes.inWholeMilliseconds + 1)
        assertFalse(testee.canShowPromotion(importedBookmarksCount = 1))
    }

    @Test
    fun whenPasswordsImportFinished15MinutesAgoThenPromoCanShow() = runTest {
        whenever(credentialImportTimestamp.lastPasswordsImportMillis()).thenReturn(currentTimeMillis - 15.minutes.inWholeMilliseconds)
        assertTrue(testee.canShowPromotion(importedBookmarksCount = 1))
    }

    private suspend fun configurePasswordImportAvailability(isAvailable: Boolean) {
        whenever(passwordImportAvailability.canImport()).thenReturn(isAvailable)
    }

    private suspend fun configureAllConditionsToAllowPromo() {
        autofillFeature.self().setRawStoredState(Toggle.State(enable = true))
        autofillFeature.showCrossFeaturePromo().setRawStoredState(Toggle.State(enable = true))
        autofillFeature.canImportFromGooglePasswordManager().setRawStoredState(Toggle.State(enable = true))
        whenever(autofillStore.getCredentialCount()).thenReturn(flowOf(Result.success(0)))
        configurePasswordImportAvailability(isAvailable = true)
    }
}
