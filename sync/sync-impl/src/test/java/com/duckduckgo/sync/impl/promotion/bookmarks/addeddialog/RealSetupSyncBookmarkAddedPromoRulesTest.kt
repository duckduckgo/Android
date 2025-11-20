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

package com.duckduckgo.sync.impl.promotion.bookmarks.addeddialog

import android.annotation.SuppressLint
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType
import com.duckduckgo.sync.impl.promotion.SyncPromotionFeature
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class RealSetupSyncBookmarkAddedPromoRulesTest {

    private val syncPromotionDataStore: SyncPromotionDataStore = mock()
    private val syncState: DeviceSyncState = mock()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncPromotionFeature = FakeFeatureToggleFactory.create(SyncPromotionFeature::class.java)

    private val testee = RealSetupSyncBookmarkAddedPromoRules(
        syncState = syncState,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        syncPromotionDataStore = syncPromotionDataStore,
        syncPromotionFeature = syncPromotionFeature,
    )

    @Before
    fun setup() = runTest {
        configureAllCriteriaMet()
    }

    @Test
    fun whenSyncStateDisabledThenCannotShowPromo() = runTest {
        whenever(syncState.isFeatureEnabled()).thenReturn(false)
        assertFalse(testee.canShowPromo())
    }

    @Test
    fun whenUserAlreadySyncingThisDeviceThenCannotShowPromo() = runTest {
        whenever(syncState.isUserSignedInOnDevice()).thenReturn(true)
        assertFalse(testee.canShowPromo())
    }

    @Test
    fun whenUserHasPreviouslyDismissedPromoThenCannotShowPromo() = runTest {
        whenever(syncPromotionDataStore.hasPromoBeenDismissed(PromotionType.BookmarkAddedDialog)).thenReturn(true)
        assertFalse(testee.canShowPromo())
    }

    @Test
    fun whenGlobalSyncPromotionFeatureDisabledThenCannotShowPromo() = runTest {
        syncPromotionFeature.self().setRawStoredState(State(enable = false))
        assertFalse(testee.canShowPromo())
    }

    @Test
    fun whenBookmarkAddedDialogPromotionFeatureDisabledThenCannotShowPromo() = runTest {
        syncPromotionFeature.bookmarkAddedDialog().setRawStoredState(State(enable = false))
        assertFalse(testee.canShowPromo())
    }

    @Test
    fun whenAllCriteriaMetThenCanShowPromo() = runTest {
        configureAllCriteriaMet()
        assertTrue(testee.canShowPromo())
    }

    private suspend fun configureAllCriteriaMet() {
        syncPromotionFeature.self().setRawStoredState(State(enable = true))
        syncPromotionFeature.bookmarkAddedDialog().setRawStoredState(State(enable = true))
        whenever(syncState.isFeatureEnabled()).thenReturn(true)
        whenever(syncState.isUserSignedInOnDevice()).thenReturn(false)
        whenever(syncPromotionDataStore.hasPromoBeenDismissed(PromotionType.BookmarkAddedDialog)).thenReturn(false)
    }
}
