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

package com.duckduckgo.sync.impl.promotion.bookmarks

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.impl.promotion.SyncPromotionFeature
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

class RealBookmarksImportSyncPromotionTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncPromotionFeature = FakeFeatureToggleFactory.create(SyncPromotionFeature::class.java)

    // DeviceSyncState properties
    private var isSyncEnabled = false
    private var isUserSignedIn = false

    private val testee = RealBookmarksImportSyncPromotion(
        syncState = mock {
            on { isFeatureEnabled() } doAnswer { isSyncEnabled }
            on { isUserSignedInOnDevice() } doAnswer { isUserSignedIn }
        },
        syncPromotionFeature = syncPromotionFeature,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun `when all preconditions are met can show promotion`() = runTest {
        configurePromotionToShow()

        assertTrue(testee.canShowPromotion(importedBookmarks = 1))
    }

    @Test
    fun `when no bookmarks are imported cannot show promotion`() = runTest {
        configurePromotionToShow()

        assertFalse(testee.canShowPromotion(importedBookmarks = 0))
    }

    @Test
    fun `when sync promotions are disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        syncPromotionFeature.self().setRawStoredState(State(enable = false))
        assertFalse(testee.canShowPromotion(importedBookmarks = 1))
    }

    @Test
    fun `when bookmarks import promotion is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        syncPromotionFeature.bookmarksImport().setRawStoredState(State(enable = false))
        assertFalse(testee.canShowPromotion(importedBookmarks = 1))
    }

    @Test
    fun `when sync is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        isSyncEnabled = false
        assertFalse(testee.canShowPromotion(importedBookmarks = 1))
    }

    @Test
    fun `when user is signed in cannot show promotion`() = runTest {
        configurePromotionToShow()

        isUserSignedIn = true
        assertFalse(testee.canShowPromotion(importedBookmarks = 1))
    }

    private fun configurePromotionToShow() {
        syncPromotionFeature.self().setRawStoredState(State(enable = true))
        syncPromotionFeature.bookmarksImport().setRawStoredState(State(enable = true))
        isSyncEnabled = true
        isUserSignedIn = false
    }
}
