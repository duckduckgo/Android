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

package com.duckduckgo.app.fire.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock

class SharedPreferencesFireDataStoreTest {

    @get:Rule val coroutineRule = CoroutineTestRule()

    @get:Rule val tempFolder = TemporaryFolder()

    private val settingsDataStore: SettingsDataStore = mock()

    private fun store(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = coroutineRule.testScope.backgroundScope,
    ) { tempFolder.newFile("fire_test.preferences_pb") }

    private fun testee(): SharedPreferencesFireDataStore = SharedPreferencesFireDataStore(
        store = store(),
        settingsDataStore = settingsDataStore,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenNtpPromoNotSetThenIsNtpPromoDismissedIsFalse() = runTest {
        assertFalse(testee().isNtpPromoDismissed())
    }

    @Test
    fun whenTabSwitcherPromoNotSetThenIsTabSwitcherPromoDismissedIsFalse() = runTest {
        assertFalse(testee().isTabSwitcherPromoDismissed())
    }

    @Test
    fun whenUserBurnedNotSetThenHasUserBurnedWhileBrowsingIsFalse() = runTest {
        assertFalse(testee().hasUserBurnedWhileBrowsing())
    }

    @Test
    fun whenNtpPromoDismissedSetTrueThenIsNtpPromoDismissedIsTrue() = runTest {
        val testee = testee()
        testee.setNtpPromoDismissed(true)
        assertTrue(testee.isNtpPromoDismissed())
    }

    @Test
    fun whenTabSwitcherPromoDismissedSetTrueThenReadsTrue() = runTest {
        val testee = testee()
        testee.setTabSwitcherPromoDismissed(true)
        assertTrue(testee.isTabSwitcherPromoDismissed())
    }

    @Test
    fun whenUserBurnedSetTrueThenHasUserBurnedWhileBrowsingIsTrue() = runTest {
        val testee = testee()
        testee.setUserBurnedWhileBrowsing(true)
        assertTrue(testee.hasUserBurnedWhileBrowsing())
    }
}
