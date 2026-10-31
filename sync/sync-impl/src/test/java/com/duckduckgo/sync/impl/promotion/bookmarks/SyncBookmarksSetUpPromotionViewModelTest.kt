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
import com.duckduckgo.sync.impl.ConnectedDevicesRepository
import com.duckduckgo.sync.impl.pixels.SyncPixelName
import com.duckduckgo.sync.impl.promotion.FakeSyncPromotionDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarksScreen
import com.duckduckgo.sync.impl.promotion.bookmarks.SyncBookmarksSetUpPromotionViewModel.Command.DismissPromotion
import com.duckduckgo.sync.impl.promotion.bookmarks.SyncBookmarksSetUpPromotionViewModel.Command.LaunchSyncSetup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class SyncBookmarksSetUpPromotionViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val connectedDeviceCount = MutableStateFlow(0)
    private val promotionDataStore = FakeSyncPromotionDataStore()
    private val pixel: Pixel = mock()

    private val testee = SyncBookmarksSetUpPromotionViewModel(
        connectedDevicesRepository = mock<ConnectedDevicesRepository> {
            on { latestConnectedDeviceCountFlow() } doReturn connectedDeviceCount
        },
        promotionDataStore = promotionDataStore,
        pixel = pixel,
    )

    @Test
    fun `when impression is recorded then fire displayed pixel`() = runTest {
        testee.recordImpression()

        verify(pixel).fire(SyncPixelName.SYNC_FEATURE_PROMOTION_DISPLAYED, mapOf("source" to "bookmarks"))
    }

    @Test
    fun `when sync is set up then fire confirmed pixel and launch sync setup`() = runTest {
        testee.commands.test {
            testee.setUpSync()

            assertEquals(LaunchSyncSetup, awaitItem())
            verify(pixel).fire(SyncPixelName.SYNC_FEATURE_PROMOTION_CONFIRMED, mapOf("source" to "bookmarks"))
        }
    }

    @Test
    fun `when sync is set up then do not record promotion dismissal`() = runTest {
        testee.commands.test {
            testee.setUpSync()

            awaitItem()
            assertFalse(promotionDataStore.hasPromoBeenDismissed(BookmarksScreen))
        }
    }

    @Test
    fun `when promotion is dismissed then fire dismissed pixel, record dismissal and dismiss promotion`() = runTest {
        testee.commands.test {
            testee.dismissPromotion()

            assertEquals(DismissPromotion, awaitItem())
            verify(pixel).fire(SyncPixelName.SYNC_FEATURE_PROMOTION_DISMISSED, mapOf("source" to "bookmarks"))
            assertTrue(promotionDataStore.hasPromoBeenDismissed(BookmarksScreen))
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
        verify(pixel, times(1)).fire(SyncPixelName.SYNC_FEATURE_PROMOTION_CONFIRMED, mapOf("source" to "bookmarks"))
    }

    @Test
    fun `when promotion is dismissed while sync setup is pending then ignore dismissal`() = runTest {
        testee.setUpSync()
        testee.dismissPromotion()

        testee.commands.test {
            assertEquals(LaunchSyncSetup, awaitItem())
            expectNoEvents()
        }
        assertFalse(promotionDataStore.hasPromoBeenDismissed(BookmarksScreen))
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

    @Test
    fun `when no device is connected then do not dismiss promotion`() = runTest {
        testee.commands.test {
            expectNoEvents()
        }
    }

    @Test
    fun `when device joins sync account then dismiss promotion without recording dismissal`() = runTest {
        testee.commands.test {
            connectedDeviceCount.value = 1

            assertEquals(DismissPromotion, awaitItem())
            assertFalse(promotionDataStore.hasPromoBeenDismissed(BookmarksScreen))
            verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
        }
    }
}
