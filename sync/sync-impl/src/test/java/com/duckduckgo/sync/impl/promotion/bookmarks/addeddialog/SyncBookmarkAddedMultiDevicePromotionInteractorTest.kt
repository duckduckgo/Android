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

package com.duckduckgo.sync.impl.promotion.bookmarks.addeddialog

import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.ConnectedDevicesRepository
import com.duckduckgo.sync.impl.pixels.SyncPixelName
import com.duckduckgo.sync.impl.promotion.FakeSyncPromotionDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarkAddedDialogMultiDevice
import com.duckduckgo.sync.impl.promotion.bookmarks.addeddialog.SyncBookmarkAddedMultiDevicePromotionInteractor.Command.DismissPromotion
import com.duckduckgo.sync.impl.promotion.bookmarks.addeddialog.SyncBookmarkAddedMultiDevicePromotionInteractor.Command.LaunchGetDesktopApp
import com.duckduckgo.sync.impl.promotion.bookmarks.addeddialog.SyncBookmarkAddedMultiDevicePromotionInteractor.Command.LaunchSyncSettings
import com.duckduckgo.sync.impl.promotion.bookmarks.addeddialog.SyncBookmarkAddedMultiDevicePromotionInteractor.Command.LaunchSyncWithDesktopChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class SyncBookmarkAddedMultiDevicePromotionInteractorTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val connectedDeviceCount = MutableStateFlow(1)
    private val promotionDataStore = FakeSyncPromotionDataStore()
    private val pixel: Pixel = mock()

    private lateinit var testee: SyncBookmarkAddedMultiDevicePromotionInteractor

    @Before
    fun setUp() {
        testee = SyncBookmarkAddedMultiDevicePromotionInteractor(
            scope = coroutineTestRule.testScope,
            connectedDevicesRepository = mock<ConnectedDevicesRepository> {
                on { latestConnectedDeviceCountFlow() } doReturn connectedDeviceCount
            },
            promotionDataStore = promotionDataStore,
            pixel = pixel,
        )
    }

    @Test
    fun `when impression is recorded then fire displayed pixel`() = runTest {
        testee.recordImpression()

        verify(pixel).fire(SyncPixelName.SYNC_FEATURE_PROMOTION_DISPLAYED, mapOf("source" to "bookmark_multi_device_added_dialog"))
    }

    @Test
    fun `when impression is recorded then record promotion impression`() = runTest {
        testee.recordImpression()

        assertEquals(1L, promotionDataStore.getPromoImpressionCount(BookmarkAddedDialogMultiDevice))
    }

    @Test
    fun `when sync with desktop choice is asked then fire confirmed pixel and launch sync with desktop choice`() = runTest {
        testee.commands.test {
            testee.askSyncWithDesktop()

            assertEquals(LaunchSyncWithDesktopChoice, awaitItem())
            verify(pixel).fire(SyncPixelName.SYNC_FEATURE_PROMOTION_CONFIRMED, mapOf("source" to "bookmark_multi_device_added_dialog"))
        }
    }

    @Test
    fun `when sync with desktop choice is asked then do not record promotion dismissal`() = runTest {
        testee.commands.test {
            testee.askSyncWithDesktop()

            awaitItem()
            assertFalse(promotionDataStore.hasPromoBeenDismissed(BookmarkAddedDialogMultiDevice))
        }
    }

    @Test
    fun `when synced with desktop then fire confirmed pixel and launch sync settings`() = runTest {
        testee.commands.test {
            testee.syncWithDesktop()

            assertEquals(LaunchSyncSettings, awaitItem())
            verify(pixel).fire(SyncPixelName.SYNC_FEATURE_PROMOTION_CONFIRMED, mapOf("source" to "bookmark_multi_device_added_dialog"))
        }
    }

    @Test
    fun `when desktop app is requested then launch get desktop app without firing pixels`() = runTest {
        testee.commands.test {
            testee.getDesktopApp()

            assertEquals(LaunchGetDesktopApp, awaitItem())
            verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
        }
    }

    @Test
    fun `when promotion is dismissed then fire dismissed pixel, record dismissal and dismiss promotion`() = runTest {
        testee.commands.test {
            testee.dismissPromotion()

            assertEquals(DismissPromotion, awaitItem())
            verify(pixel).fire(SyncPixelName.SYNC_FEATURE_PROMOTION_DISMISSED, mapOf("source" to "bookmark_multi_device_added_dialog"))
            assertTrue(promotionDataStore.hasPromoBeenDismissed(BookmarkAddedDialogMultiDevice))
        }
    }

    @Test
    fun `when sync with desktop choice is asked twice before command is delivered then launch sync with desktop choice once`() = runTest {
        testee.askSyncWithDesktop()
        testee.askSyncWithDesktop()

        testee.commands.test {
            assertEquals(LaunchSyncWithDesktopChoice, awaitItem())
            expectNoEvents()
        }
        verify(pixel, times(1)).fire(SyncPixelName.SYNC_FEATURE_PROMOTION_CONFIRMED, mapOf("source" to "bookmark_multi_device_added_dialog"))
    }

    @Test
    fun `when promotion is dismissed while sync with desktop choice launch is pending then ignore dismissal`() = runTest {
        testee.askSyncWithDesktop()
        testee.dismissPromotion()

        testee.commands.test {
            assertEquals(LaunchSyncWithDesktopChoice, awaitItem())
            expectNoEvents()
        }
        assertFalse(promotionDataStore.hasPromoBeenDismissed(BookmarkAddedDialogMultiDevice))
    }

    @Test
    fun `when sync with desktop choice command is delivered then sync with desktop choice can be asked again`() = runTest {
        testee.commands.test {
            testee.askSyncWithDesktop()
            assertEquals(LaunchSyncWithDesktopChoice, awaitItem())

            testee.askSyncWithDesktop()
            assertEquals(LaunchSyncWithDesktopChoice, awaitItem())
        }
    }

    @Test
    fun `when only this device is connected then do not dismiss promotion`() = runTest {
        testee.commands.test {
            expectNoEvents()
        }
    }

    @Test
    fun `when another device joins sync account then dismiss promotion without recording dismissal`() = runTest {
        testee.commands.test {
            connectedDeviceCount.value = 2

            assertEquals(DismissPromotion, awaitItem())
            assertFalse(promotionDataStore.hasPromoBeenDismissed(BookmarkAddedDialogMultiDevice))
            verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
        }
    }

    @Test
    fun `when this device leaves sync account then dismiss promotion without recording dismissal`() = runTest {
        testee.commands.test {
            connectedDeviceCount.value = 0

            assertEquals(DismissPromotion, awaitItem())
            assertFalse(promotionDataStore.hasPromoBeenDismissed(BookmarkAddedDialogMultiDevice))
            verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
        }
    }
}
