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

package com.duckduckgo.adblocking.impl.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealAdBlockingUserPreferencesTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val testDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("ad_blocking_user_preferences_test") },
        )

    private val testee = RealAdBlockingUserPreferences(testDataStore)

    @Test
    fun whenNothingStoredThenNeitherUserSettingNorPixelConsentIsRecorded() = runTest {
        assertNull(testee.isEnabled())
        assertNull(testee.hasPixelConsentFlow().first())
    }

    @Test
    fun whenEnabledWithoutPixelConsentThenBothFlowsReportIt() = runTest {
        testee.setEnabled(enabled = true, withPixelConsent = false)

        assertTrue(testee.isEnabled()!!)
        assertEquals(false, testee.hasPixelConsentFlow().first())
    }

    @Test
    fun whenEnabledWithoutPixelConsentThenBothKeysLandInASingleEmission() = coroutineRule.testScope.runTest {
        combine(testee.isEnabledFlow(), testee.hasPixelConsentFlow(), ::Pair)
            .distinctUntilChanged()
            .test {
                assertEquals(null to null, awaitItem())

                testee.setEnabled(enabled = true, withPixelConsent = false)

                // write needs to be a single emission. If it wasn't, (true, null) or (null, false) would surface here
                assertEquals(true to false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
    }
}
