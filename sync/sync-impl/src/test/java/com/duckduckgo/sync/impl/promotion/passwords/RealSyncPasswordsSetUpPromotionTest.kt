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

package com.duckduckgo.sync.impl.promotion.passwords

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.impl.promotion.FakeSyncPromotionDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionAppInstall
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.PasswordsScreen
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
import kotlin.time.Duration.Companion.milliseconds

class RealSyncPasswordsSetUpPromotionTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncPromotionFeature = FakeFeatureToggleFactory.create(SyncPromotionFeature::class.java)
    private val promotionDataStore = FakeSyncPromotionDataStore()

    // DeviceSyncState properties
    private var isSyncEnabled = false
    private var isUserSignedIn = false

    // SyncPromotionAppInstall properties
    private var installAge: Duration? = null

    private val testee = RealSyncPasswordsSetUpPromotion(
        syncState = mock {
            on { isFeatureEnabled() } doAnswer { isSyncEnabled }
            on { isUserSignedInOnDevice() } doAnswer { isUserSignedIn }
        },
        syncPromotionFeature = syncPromotionFeature,
        promotionDataStore = promotionDataStore,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        appInstall = mock<SyncPromotionAppInstall> {
            onBlocking { getInstallAge() } doAnswer { installAge }
        },
    )

    @Test
    fun `when all preconditions are met can show promotion`() = runTest {
        configurePromotionToShow()

        assertTrue(testee.canShowPromotion(savedPasswords = 1))
    }

    @Test
    fun `when no passwords are saved cannot show promotion`() = runTest {
        configurePromotionToShow()

        assertFalse(testee.canShowPromotion(savedPasswords = 0))
    }

    @Test
    fun `when top level promotion flag is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        syncPromotionFeature.self().setRawStoredState(State(enable = false))
        assertFalse(testee.canShowPromotion(savedPasswords = 1))
    }

    @Test
    fun `when passwords promotion flag is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        syncPromotionFeature.passwords().setRawStoredState(State(enable = false))
        assertFalse(testee.canShowPromotion(savedPasswords = 1))
    }

    @Test
    fun `when redesign flag is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        syncPromotionFeature.leveragePromosRedesign().setRawStoredState(State(enable = false))
        assertFalse(testee.canShowPromotion(savedPasswords = 1))
    }

    @Test
    fun `when sync is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        isSyncEnabled = false
        assertFalse(testee.canShowPromotion(savedPasswords = 1))
    }

    @Test
    fun `when user is signed in cannot show promotion`() = runTest {
        configurePromotionToShow()

        isUserSignedIn = true
        assertFalse(testee.canShowPromotion(savedPasswords = 1))
    }

    @Test
    fun `when passwords screen promotion was dismissed cannot show promotion`() = runTest {
        configurePromotionToShow()

        promotionDataStore.recordPromoDismissed(PasswordsScreen)
        assertFalse(testee.canShowPromotion(savedPasswords = 1))
    }

    @Test
    fun `when app is installed for exactly three days cannot show promotion`() = runTest {
        configurePromotionToShow()

        installAge = 3.days
        assertFalse(testee.canShowPromotion(savedPasswords = 1))
    }

    @Test
    fun `when app is installed for more than three days can show promotion`() = runTest {
        configurePromotionToShow()

        installAge = 3.days + 1.milliseconds
        assertTrue(testee.canShowPromotion(savedPasswords = 1))
    }

    @Test
    fun `when app install age is unknown cannot show promotion`() = runTest {
        configurePromotionToShow()

        installAge = null
        assertFalse(testee.canShowPromotion(savedPasswords = 1))
    }

    private fun configurePromotionToShow() {
        syncPromotionFeature.self().setRawStoredState(State(enable = true))
        syncPromotionFeature.passwords().setRawStoredState(State(enable = true))
        syncPromotionFeature.leveragePromosRedesign().setRawStoredState(State(enable = true))
        isSyncEnabled = true
        isUserSignedIn = false
        installAge = 4.days
    }
}
