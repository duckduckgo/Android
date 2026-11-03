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
import com.duckduckgo.autofill.api.ImportFromGoogleAvailability
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.savedsites.api.SavedSitesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class RealBookmarksInPasswordsImportPromotionTest {

    private val importFromGoogleAvailability: ImportFromGoogleAvailability = mock()
    private val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)
    private val savedSitesRepository: SavedSitesRepository = mock()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun setup() = runTest {
        configureAllConditionsToAllowPromo()
    }

    private val testee = RealBookmarksInPasswordsImportPromotion(
        savedSitesRepository = savedSitesRepository,
        autofillFeature = autofillFeature,
        importFromGoogleAvailability = importFromGoogleAvailability,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenAllConditionsAllowItThenPromoCanShow() = runTest {
        assertTrue(testee.canShowPromotion(importedPasswordsCount = 1))
    }

    @Test
    fun whenNoPasswordsWereImportedThenPromoCannotShow() = runTest {
        assertFalse(testee.canShowPromotion(importedPasswordsCount = 0))
    }

    @Test
    fun whenAutofillTopLevelFeatureDisabledThenPromoCannotShow() = runTest {
        autofillFeature.self().setRawStoredState(Toggle.State(enable = false))
        assertFalse(testee.canShowPromotion(importedPasswordsCount = 1))
    }

    @Test
    fun whenCrossFeaturePromoDisabledThenPromoCannotShow() = runTest {
        autofillFeature.showCrossFeaturePromo().setRawStoredState(Toggle.State(enable = false))
        assertFalse(testee.canShowPromotion(importedPasswordsCount = 1))
    }

    @Test
    fun whenBookmarksImportNotAvailableThenPromoCannotShow() = runTest {
        whenever(importFromGoogleAvailability.isBookmarksImportAvailable()).thenReturn(false)
        assertFalse(testee.canShowPromotion(importedPasswordsCount = 1))
    }

    @Test
    fun whenSavedBookmarksCountReachesLimitThenPromoCannotShow() = runTest {
        whenever(savedSitesRepository.bookmarksCount()).thenReturn(9)
        assertFalse(testee.canShowPromotion(importedPasswordsCount = 1))
    }

    @Test
    fun whenSavedBookmarksCountBelowLimitThenPromoCanShow() = runTest {
        whenever(savedSitesRepository.bookmarksCount()).thenReturn(8)
        assertTrue(testee.canShowPromotion(importedPasswordsCount = 1))
    }

    private suspend fun configureAllConditionsToAllowPromo() {
        autofillFeature.self().setRawStoredState(Toggle.State(enable = true))
        autofillFeature.showCrossFeaturePromo().setRawStoredState(Toggle.State(enable = true))
        whenever(savedSitesRepository.bookmarksCount()).thenReturn(0)
        whenever(importFromGoogleAvailability.isBookmarksImportAvailable()).thenReturn(true)
    }
}
