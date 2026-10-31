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
import com.duckduckgo.sync.impl.ConnectedDevicesRepository
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.promotion.FakeSyncPromotionDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarksScreen
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarksScreenMultiDevice
import com.duckduckgo.sync.impl.promotion.SyncPromotionFeature
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class RealSyncBookmarksMultiDevicePromotionTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncPromotionFeature = FakeFeatureToggleFactory.create(SyncPromotionFeature::class.java)
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)
    private val promotionDataStore = FakeSyncPromotionDataStore()

    // DeviceSyncState properties
    private var isSyncEnabled = false
    private var isUserSignedIn = false

    // ConnectedDevicesRepository properties
    private var connectedDeviceCount = 0

    // AppInstall properties
    private var installAge: Duration? = null

    private val testee = RealSyncBookmarksMultiDevicePromotion(
        syncState = mock {
            on { isFeatureEnabled() } doAnswer { isSyncEnabled }
            on { isUserSignedInOnDevice() } doAnswer { isUserSignedIn }
        },
        syncPromotionFeature = syncPromotionFeature,
        syncFeature = syncFeature,
        promotionDataStore = promotionDataStore,
        connectedDevicesRepository = mock<ConnectedDevicesRepository> {
            onBlocking { latestConnectedDeviceCount() } doAnswer { connectedDeviceCount }
        },
        dispatchers = coroutineTestRule.testDispatcherProvider,
        appInstall = mock {
            onBlocking { getInstallAge() } doAnswer { installAge }
        },
    )

    @Test
    fun `when all preconditions are met can show promotion`() = runTest {
        configurePromotionToShow()

        assertTrue(testee.canShowPromotion(savedBookmarks = 1))
    }

    @Test
    fun `when no bookmarks are saved cannot show promotion`() = runTest {
        configurePromotionToShow()

        assertFalse(testee.canShowPromotion(savedBookmarks = 0))
    }

    @Test
    fun `when top level promotion flag is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        syncPromotionFeature.self().setRawStoredState(State(enable = false))
        assertFalse(testee.canShowPromotion(savedBookmarks = 1))
    }

    @Test
    fun `when bookmarks promotion flag is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        syncPromotionFeature.bookmarks().setRawStoredState(State(enable = false))
        assertFalse(testee.canShowPromotion(savedBookmarks = 1))
    }

    @Test
    fun `when redesign flag is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        syncPromotionFeature.leveragePromosRedesign().setRawStoredState(State(enable = false))
        assertFalse(testee.canShowPromotion(savedBookmarks = 1))
    }

    @Test
    fun `when connected devices refresh on sync is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        syncFeature.refreshConnectedDevicesOnSync().setRawStoredState(State(enable = false))
        assertFalse(testee.canShowPromotion(savedBookmarks = 1))
    }

    @Test
    fun `when sync is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        isSyncEnabled = false
        assertFalse(testee.canShowPromotion(savedBookmarks = 1))
    }

    @Test
    fun `when user is signed out cannot show promotion`() = runTest {
        configurePromotionToShow()

        isUserSignedIn = false
        assertFalse(testee.canShowPromotion(savedBookmarks = 1))
    }

    @Test
    fun `when connected device count is not known yet cannot show promotion`() = runTest {
        configurePromotionToShow()

        connectedDeviceCount = 0
        assertFalse(testee.canShowPromotion(savedBookmarks = 1))
    }

    @Test
    fun `when more than one device is connected cannot show promotion`() = runTest {
        configurePromotionToShow()

        connectedDeviceCount = 2
        assertFalse(testee.canShowPromotion(savedBookmarks = 1))
    }

    @Test
    fun `when multi device promotion was dismissed cannot show promotion`() = runTest {
        configurePromotionToShow()

        promotionDataStore.recordPromoDismissed(BookmarksScreenMultiDevice)
        assertFalse(testee.canShowPromotion(savedBookmarks = 1))
    }

    @Test
    fun `when app is installed for exactly seven days cannot show promotion`() = runTest {
        configurePromotionToShow()

        installAge = 7.days
        assertFalse(testee.canShowPromotion(savedBookmarks = 1))
    }

    @Test
    fun `when app is installed for more than seven days can show promotion`() = runTest {
        configurePromotionToShow()

        installAge = 7.days + 1.milliseconds
        assertTrue(testee.canShowPromotion(savedBookmarks = 1))
    }

    @Test
    fun `when app install age is unknown cannot show promotion`() = runTest {
        configurePromotionToShow()

        installAge = null
        assertFalse(testee.canShowPromotion(savedBookmarks = 1))
    }

    @Test
    fun `when set up sync promotion was shown less than a day ago cannot show promotion`() = runTest {
        configurePromotionToShow()

        promotionDataStore.setTimeSinceLastPromoImpression(BookmarksScreen, 24.hours - 1.milliseconds)
        assertFalse(testee.canShowPromotion(savedBookmarks = 1))
    }

    @Test
    fun `when set up sync promotion was shown a day ago can show promotion`() = runTest {
        configurePromotionToShow()

        promotionDataStore.setTimeSinceLastPromoImpression(BookmarksScreen, 24.hours)
        assertTrue(testee.canShowPromotion(savedBookmarks = 1))
    }

    @Test
    fun `when set up sync promotion was dismissed can show promotion`() = runTest {
        configurePromotionToShow()

        promotionDataStore.recordPromoDismissed(BookmarksScreen)
        assertTrue(testee.canShowPromotion(savedBookmarks = 1))
    }

    private fun configurePromotionToShow() {
        syncPromotionFeature.self().setRawStoredState(State(enable = true))
        syncPromotionFeature.bookmarks().setRawStoredState(State(enable = true))
        syncPromotionFeature.leveragePromosRedesign().setRawStoredState(State(enable = true))
        syncFeature.refreshConnectedDevicesOnSync().setRawStoredState(State(enable = true))
        isSyncEnabled = true
        isUserSignedIn = true
        connectedDeviceCount = 1
        installAge = 8.days
    }
}
