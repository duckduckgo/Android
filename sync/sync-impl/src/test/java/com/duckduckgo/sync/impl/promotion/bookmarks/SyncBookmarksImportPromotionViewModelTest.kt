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

import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.pixels.SyncPixelName
import com.duckduckgo.sync.impl.promotion.FakeSyncPromotionDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarksImport
import com.duckduckgo.sync.impl.promotion.bookmarks.SyncBookmarksImportPromotionViewModel.Command.LaunchSyncSetup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class SyncBookmarksImportPromotionViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val promotionDataStore = FakeSyncPromotionDataStore()
    private val pixel: Pixel = mock()

    private val testee = SyncBookmarksImportPromotionViewModel(
        promotionDataStore = promotionDataStore,
        pixel = pixel,
    )

    @Test
    fun `when impression is recorded then fire displayed pixel`() = runTest {
        testee.recordImpression()

        verify(pixel).fire(SyncPixelName.SYNC_FEATURE_PROMOTION_DISPLAYED, mapOf("source" to "bookmarks_import"))
    }

    @Test
    fun `when impression is recorded then record promotion impression`() = runTest {
        testee.recordImpression()

        assertEquals(1L, promotionDataStore.getPromoImpressionCount(BookmarksImport))
    }

    @Test
    fun `when sync is set up then fire confirmed pixel and launch sync setup`() = runTest {
        testee.commands.test {
            testee.setUpSync()

            assertEquals(LaunchSyncSetup, awaitItem())
            verify(pixel).fire(SyncPixelName.SYNC_FEATURE_PROMOTION_CONFIRMED, mapOf("source" to "bookmarks_import"))
        }
    }

    @Test
    fun `when sync is set up twice before command is delivered then launch sync setup once`() = runTest {
        testee.setUpSync()
        testee.setUpSync()

        testee.commands.test {
            assertEquals(LaunchSyncSetup, awaitItem())
            expectNoEvents()
        }
        verify(pixel, times(1)).fire(SyncPixelName.SYNC_FEATURE_PROMOTION_CONFIRMED, mapOf("source" to "bookmarks_import"))
    }

    @Test
    fun `when sync setup command is delivered then sync can be set up again`() = runTest {
        testee.commands.test {
            testee.setUpSync()
            assertEquals(LaunchSyncSetup, awaitItem())

            testee.setUpSync()
            assertEquals(LaunchSyncSetup, awaitItem())
        }
    }
}
