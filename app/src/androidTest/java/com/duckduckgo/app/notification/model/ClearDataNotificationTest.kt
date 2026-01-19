/*
 * Copyright (c) 2019 DuckDuckGo
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

@file:Suppress("RemoveExplicitTypeArguments")

package com.duckduckgo.app.notification.model

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.fire.AutomaticDataClearing
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ClearDataNotificationTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notificationsDao: NotificationDao = mock()
    private val settingsDataStore: SettingsDataStore = mock()
    private val automaticDataClearing: AutomaticDataClearing = mock()
    private val androidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private val dispatcherProvider: DispatcherProvider = coroutineRule.testDispatcherProvider

    private lateinit var testee: ClearDataNotification

    @Before
    fun before() {
        testee = ClearDataNotification(
            context,
            notificationsDao,
            settingsDataStore,
            automaticDataClearing,
            androidBrowserConfigFeature,
            dispatcherProvider,
        )
    }

    @Test
    fun whenNotificationNotSeenAndOptionNotSetThenCanShowIsTrue() = runTest {
        givenFeatureFlagDisabled()
        whenever(notificationsDao.exists(any())).thenReturn(false)
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_NONE)
        assertTrue(testee.canShow())
    }

    @Test
    fun whenNotificationNotSeenButOptionAlreadySetThenCanShowIsFalse() = runTest {
        givenFeatureFlagDisabled()
        whenever(notificationsDao.exists(any())).thenReturn(false)
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_TABS_ONLY)
        assertFalse(testee.canShow())
    }

    @Test
    fun whenNotificationAlreadySeenAndOptionNotSetThenCanShowIsFalse() = runTest {
        givenFeatureFlagDisabled()
        whenever(notificationsDao.exists(any())).thenReturn(true)
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_NONE)
        assertFalse(testee.canShow())
    }

    @Test
    fun whenFeatureFlagEnabledAndNotificationNotSeenAndNoAutomaticClearOptionThenCanShowIsTrue() = runTest {
        givenFeatureFlagEnabled()
        whenever(notificationsDao.exists(any())).thenReturn(false)
        whenever(automaticDataClearing.isAutomaticDataClearingOptionSelected()).thenReturn(false)
        assertTrue(testee.canShow())
    }

    @Test
    fun whenFeatureFlagEnabledAndNotificationNotSeenButAutomaticClearOptionSetThenCanShowIsFalse() = runTest {
        givenFeatureFlagEnabled()
        whenever(notificationsDao.exists(any())).thenReturn(false)
        whenever(automaticDataClearing.isAutomaticDataClearingOptionSelected()).thenReturn(true)
        assertFalse(testee.canShow())
    }

    @Test
    fun whenFeatureFlagEnabledAndNotificationAlreadySeenThenCanShowIsFalse() = runTest {
        givenFeatureFlagEnabled()
        whenever(notificationsDao.exists(any())).thenReturn(true)
        assertFalse(testee.canShow())
    }

    private fun givenFeatureFlagEnabled() {
        androidBrowserConfigFeature.improvedDataClearingOptions().setRawStoredState(Toggle.State(true))
    }

    private fun givenFeatureFlagDisabled() {
        androidBrowserConfigFeature.improvedDataClearingOptions().setRawStoredState(Toggle.State(false))
    }
}
